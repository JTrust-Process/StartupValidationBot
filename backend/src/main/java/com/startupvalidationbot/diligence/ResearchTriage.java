package com.startupvalidationbot.diligence;

import static com.startupvalidationbot.diligence.DiligenceDomain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Read-only research ordering, independent of investment scores and worker refresh selection. */
public final class ResearchTriage {
    private ResearchTriage() { }
    public enum Category { TIME_SENSITIVE, SUBSTANTIVE_RESEARCH, INVESTIGATE, BLOCKED, ENDED }
    public enum Lifecycle { ACTIVE, POSSIBLY_ACTIVE, UNKNOWN, ENDED, WITHDRAWN, TERMINATED }
    public record Filing(String accession, String form, LocalDate filedAt, String url) { }
    public record Input(Packet packet, String offeringStatus, String matchStatus, int matchConfidence,
            String filedLifecycle, boolean completed, String fetchStatus, LocalDateTime lastAttemptAt, List<Filing> filings) { }
    public record Support(String field, String value, String sourceUrl, Long evidenceId,
            String classification, String observedAt, String reference) { }
    public record Reason(String code, String explanation, int weight, List<Support> supports) { }
    public record Entry(Packet packet, Lifecycle lifecycle, Category category, int researchPriority,
            String currentIdentityStatus, int currentIdentityConfidence, LocalDateTime latestEvidenceAt,
            List<Reason> reasons, List<String> questions) { }
    public record Queue(String version, LocalDateTime evaluatedAt, LocalDate deadlineDate, String timeZone,
            List<Entry> attention, List<Entry> entries) { }

    public static Queue queue(List<Input> inputs, LocalDateTime now, LocalDate today, String timeZone) {
        List<Entry> entries = inputs.stream().map(input -> evaluate(input, now, today)).sorted(order()).toList();
        var companies = new HashSet<Long>();
        List<Entry> attention = entries.stream().filter(e -> e.category() != Category.ENDED && e.category() != Category.BLOCKED
                && e.researchPriority() > 0).filter(e -> companies.add(e.packet().radarCompanyId())).limit(5).toList();
        return new Queue("research-triage-v1", now, today, timeZone, attention, entries);
    }
    public static Comparator<Entry> order() {
        return Comparator.comparingInt((Entry e) -> e.category().ordinal())
                .thenComparing(Comparator.comparingInt(Entry::researchPriority).reversed())
                .thenComparing(e -> e.category() == Category.TIME_SENSITIVE ? e.packet().deadline() : null,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e.packet().lastRefreshedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingLong(e -> e.packet().id());
    }
    static Entry evaluate(Input input, LocalDateTime now, LocalDate today) {
        Packet p = input.packet();
        Lifecycle lifecycle = lifecycle(input, today);
        var reasons = new ArrayList<Reason>();
        var questions = new java.util.LinkedHashSet<String>();
        LocalDateTime latest = p.evidence().stream().filter(ResearchTriage::sourceEvidence).map(Evidence::observedAt)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        if (List.of(Lifecycle.ENDED, Lifecycle.WITHDRAWN, Lifecycle.TERMINATED).contains(lifecycle)) {
            add(reasons, "OFFERING_ENDED", "Historical offering; excluded from active research attention.", 0,
                    field("offering.status", input.offeringStatus(), p.secFilingUrl()),
                    field("offering.filedLifecycle", input.filedLifecycle(), p.secFilingUrl()),
                    field("offering.completed", input.completed(), p.secFilingUrl()), term(p, "deadline", p.deadline()));
            return entry(input, lifecycle, Category.ENDED, latest, reasons, p.unansweredQuestions());
        }
        if ("REJECTED".equals(input.matchStatus())) {
            add(reasons, "ISSUER_LINK_REJECTED", "Issuer link is rejected; establish the correct company before researching this packet.", 0,
                    field("offering.matchStatus", input.matchStatus(), p.secFilingUrl()));
            return entry(input, lifecycle, Category.BLOCKED, latest, reasons, List.of("Which company does this filed issuer represent?"));
        }
        boolean urgent = false;
        TermProvenance deadlineSource = p.termProvenance().get("deadline");
        if ((lifecycle == Lifecycle.ACTIVE || lifecycle == Lifecycle.POSSIBLY_ACTIVE) && p.deadline() != null
                && deadlineSource != null && present(deadlineSource.sourceUrl()) && authoritative(deadlineSource.classification())) {
            long days = ChronoUnit.DAYS.between(today, p.deadline());
            if (days >= 0 && days <= 30) {
                urgent = true;
                add(reasons, "APPROACHING_SOURCE_DEADLINE", "Source-backed offering deadline " + p.deadline()
                        + " is in " + days + " day(s); confirm the current offering window.", days <= 7 ? 100 : days <= 14 ? 80 : 60,
                        term(p, "deadline", p.deadline()), field("offering.status", input.offeringStatus(), p.secFilingUrl()));
                questions.add("Has the offering deadline been extended or has the offering closed early?");
            }
        }
        if (lifecycle == Lifecycle.UNKNOWN) add(reasons, "LIFECYCLE_UNRESOLVED", "Current offering lifecycle is not established; no deadline urgency is inferred.", 0,
                field("offering.status", input.offeringStatus(), p.secFilingUrl()));
        // Filed dates, not ingestion timestamps: backfilled old amendments are not recent news.
        Filing amendment = input.filings().stream().filter(f -> List.of("C/A", "C-U/A").contains(f.form())
                && f.filedAt() != null && !f.filedAt().isAfter(today) && !f.filedAt().isBefore(today.minusDays(14))
                && (p.reviewedAt() == null || f.filedAt().isAfter(p.reviewedAt().toLocalDate())))
                .max(Comparator.comparing(Filing::filedAt).thenComparing(Filing::accession)).orElse(null);
        if (amendment != null) {
            add(reasons, "RECENT_SEC_AMENDMENT", "SEC " + amendment.form() + " filed " + amendment.filedAt()
                    + " is recent and has no later recorded review; inspect the amendment.", 35,
                    new Support("filing.filedAt", amendment.filedAt().toString(), amendment.url(), null,
                            "SEC_FILED_FACT", null, amendment.accession()), field("packet.reviewedAt", p.reviewedAt(), null));
            questions.add("What changed in SEC amendment " + amendment.accession() + "?");
            filedChanges(input, amendment, reasons, questions);
        }
        List<Evidence> terms = p.evidence().stream().filter(ResearchTriage::sourceEvidence).filter(e -> List.of(
                "security_type", "minimum_investment", "target_amount", "maximum_amount", "deadline", "valuation_or_cap").contains(e.factKey())).toList();
        List<FinancialPeriod> financials = p.financials().stream().filter(f -> present(f.sourceUrl()) && (
                f.revenue() != null || f.netIncome() != null || f.cash() != null || f.assets() != null)).toList();
        boolean substantive = !financials.isEmpty() || terms.stream().map(Evidence::factKey).distinct().count() >= 2;
        if (!financials.isEmpty()) add(reasons, "FINANCIAL_EVIDENCE_AVAILABLE", financials.size()
                + " source-backed financial period(s) are available for substantive research; this does not assess performance.", 20,
                financials.stream().map(f -> new Support("financials.period", f.period(), f.sourceUrl(), null,
                        "SOURCE_BACKED_FINANCIAL", null, f.sourceAccessionNumber())).toArray(Support[]::new));
        else questions.add("Which financial periods and original statements are available in the public offering documents?");
        if (terms.stream().map(Evidence::factKey).distinct().count() >= 2) add(reasons, "OFFERING_EVIDENCE_AVAILABLE",
                "Substantive source-backed offering terms are available to examine.", 15,
                terms.stream().limit(6).map(ResearchTriage::evidence).toArray(Support[]::new));
        if (!"CONFIRMED".equals(input.matchStatus())) {
            add(reasons, "ISSUER_IDENTITY_UNRESOLVED", "Issuer identity is " + input.matchStatus() + " (" + input.matchConfidence()
                    + "/100); investigate the company-to-issuer link before relying on this packet.", substantive ? 25 : 10,
                    field("offering.matchStatus", input.matchStatus(), p.secFilingUrl()), field("offering.matchConfidence", input.matchConfidence(), p.secFilingUrl()));
            questions.add("Does the legal issuer and its official website independently match the Radar company?");
        }
        if (!p.materialDiscrepancies().isEmpty()) {
            add(reasons, "RECORDED_DISCREPANCIES", "Recorded evidence discrepancies require investigation; they are not investment verdicts.", substantive ? 30 : 10,
                    field("packet.materialDiscrepancies", String.join("; ", p.materialDiscrepancies()), null));
            p.materialDiscrepancies().forEach(v -> questions.add("Resolve recorded discrepancy: " + v));
        }
        var missing = new ArrayList<String>();
        if (!present(p.securityType()) || p.securityType().matches("(?i)unknown|other")) missing.add("security type");
        if (p.minimumInvestment() == null) missing.add("minimum investment");
        if (p.deadline() == null) missing.add("offering deadline");
        if (!present(p.valuationOrCap()) && p.securityType() != null && p.securityType().matches("(?i).*(SAFE|stock|equity).*")) missing.add("valuation or cap (if applicable)");
        if (!missing.isEmpty()) {
            add(reasons, "MISSING_RESEARCH_TERMS", "Unestablished research fields: " + String.join(", ", missing)
                    + ". Missing data is not a negative investment signal.", substantive ? Math.min(12, missing.size() * 4) : 0,
                    field("packet.securityType", p.securityType(), null), term(p, "minimumInvestment", p.minimumInvestment()),
                    term(p, "deadline", p.deadline()), term(p, "valuationOrCap", p.valuationOrCap()));
            missing.forEach(v -> questions.add("Can public authoritative evidence establish " + v + "?"));
        }
        boolean blocked = List.of("HTTP_403", "HTTP_404", "BROWSER_VERIFICATION").contains(safe(input.fetchStatus()));
        if (blocked) {
            add(reasons, "SOURCE_ACCESS_BLOCKED", "Campaign access is " + input.fetchStatus()
                    + "; repeated access failure adds no priority. Use retained SEC evidence or an owner-provided document.", 0,
                    field("offering.campaignFetchStatus", input.fetchStatus(), p.campaignUrl()), field("offering.lastDiligenceAttemptAt", input.lastAttemptAt(), null));
            questions.add("Is an accessible official document available without bypassing access controls?");
        } else if (List.of("TIMEOUT", "HTTP_429", "HTTP_500", "HTTP_502", "HTTP_503").contains(safe(input.fetchStatus()))
                && substantive && input.lastAttemptAt() != null && !input.lastAttemptAt().isAfter(now.minusDays(7))) {
            add(reasons, "RECOVERABLE_SOURCE_FAILURE", "A recorded transient source failure is at least seven days old; a permitted recheck may help.", 8,
                    field("offering.campaignFetchStatus", input.fetchStatus(), p.campaignUrl()), field("offering.lastDiligenceAttemptAt", input.lastAttemptAt(), null));
        } else if (present(input.fetchStatus()) && !"SUCCESS".equals(input.fetchStatus())) add(reasons, "SOURCE_FAILURE_UNCLASSIFIED",
                "Source access was not established; no recoverable-failure urgency is inferred.", 0, field("offering.campaignFetchStatus", input.fetchStatus(), p.campaignUrl()));
        if (p.lastRefreshedAt() == null || !p.lastRefreshedAt().isAfter(now.minusDays(7))) add(reasons,
                p.lastRefreshedAt() == null ? "REFRESH_NOT_ESTABLISHED" : "STALE_DILIGENCE", p.lastRefreshedAt() == null
                        ? "A successful diligence refresh time is not established." : "Last successful diligence refresh is "
                            + ChronoUnit.DAYS.between(p.lastRefreshedAt(), now) + " days old.", substantive ? 12 : 0,
                field("packet.lastRefreshedAt", p.lastRefreshedAt(), null));
        if (latest == null || !latest.isAfter(now.minusDays(30))) add(reasons, "EVIDENCE_FRESHNESS_UNCERTAIN",
                "Retained source evidence is absent or was first observed at least 30 days ago; a recent refresh does not make it a new fact.", substantive ? 6 : 0,
                field("evidence.latestObservedAt", latest, null));
        p.unansweredQuestions().forEach(questions::add);
        return entry(input, lifecycle, urgent ? Category.TIME_SENSITIVE : substantive ? Category.SUBSTANTIVE_RESEARCH
                : blocked ? Category.BLOCKED : Category.INVESTIGATE, latest, reasons, List.copyOf(questions));
    }
    private static void filedChanges(Input input, Filing amendment, List<Reason> reasons, java.util.Set<String> questions) {
        for (Evidence current : input.packet().evidence()) {
            String semantic = String.valueOf(current.metadata().getOrDefault("semanticType", "UNKNOWN"));
            if (current.classification() != EvidenceClassification.SEC_FILED_FACT || !present(current.sourceUrl())
                    || !current.factKey().startsWith("filed_") || "UNKNOWN".equals(semantic)
                    || !amendment.accession().equals(current.metadata().get("accessionNumber"))) continue;
            Evidence prior = input.packet().evidence().stream().filter(e -> e.classification() == EvidenceClassification.SEC_FILED_FACT
                    && present(e.sourceUrl()) && current.factKey().equals(e.factKey()) && Objects.equals(current.period(), e.period())
                    && semantic.equals(e.metadata().get("semanticType")) && Objects.equals(current.metadata().get("units"), e.metadata().get("units"))
                    && input.filings().stream().anyMatch(f -> f.accession().equals(e.metadata().get("accessionNumber"))
                        && f.filedAt() != null && f.filedAt().isBefore(amendment.filedAt())))
                    .max(Comparator.comparing((Evidence e) -> input.filings().stream()
                            .filter(f -> f.accession().equals(e.metadata().get("accessionNumber")))
                            .map(Filing::filedAt).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(LocalDate.MIN))
                            .thenComparingLong(Evidence::id)).orElse(null);
            boolean ambiguousPrior = prior != null && input.packet().evidence().stream().anyMatch(e ->
                    e.classification() == EvidenceClassification.SEC_FILED_FACT && present(e.sourceUrl())
                    && Objects.equals(prior.metadata().get("accessionNumber"), e.metadata().get("accessionNumber"))
                    && current.factKey().equals(e.factKey()) && Objects.equals(current.period(), e.period())
                    && semantic.equals(e.metadata().get("semanticType")) && Objects.equals(current.metadata().get("units"), e.metadata().get("units"))
                    && !sameValue(prior.factValue(), e.factValue()));
            if (prior != null && !ambiguousPrior && !sameValue(current.factValue(), prior.factValue())) {
                add(reasons, "SOURCE_BACKED_TERM_CHANGE", "Comparable SEC-filed " + semantic.replace('_', ' ').toLowerCase(java.util.Locale.ROOT)
                        + " values differ across filings; inspect their conditions and applicability.", 25, evidence(prior), evidence(current));
                questions.add("Why do comparable filed " + semantic + " values differ between accessions?");
                break;
            }
        }
    }
    private static boolean sameValue(String a, String b) {
        if (Objects.equals(a, b)) return true;
        try { return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0; }
        catch (NumberFormatException error) { return false; }
    }
    private static Lifecycle lifecycle(Input input, LocalDate today) {
        if ("WITHDRAWN".equals(input.offeringStatus()) || "WITHDRAWN".equals(input.filedLifecycle())) return Lifecycle.WITHDRAWN;
        if ("TERMINATED".equals(input.offeringStatus()) || "TERMINATED".equals(input.filedLifecycle())) return Lifecycle.TERMINATED;
        if (input.completed() || "COMPLETED".equals(input.filedLifecycle()) || "ENDED".equals(input.offeringStatus())
                || input.packet().deadline() != null && input.packet().deadline().isBefore(today)) return Lifecycle.ENDED;
        if (List.of("CLOSED", "FUNDED", "WITHDRAWN", "TERMINATED").contains(safe(input.packet().platformStatus()))
                && input.packet().termProvenance().containsKey("platformStatus")) return Lifecycle.ENDED;
        return switch (safe(input.offeringStatus())) {
            case "ACTIVE" -> Lifecycle.ACTIVE;
            case "POSSIBLY_ACTIVE" -> Lifecycle.POSSIBLY_ACTIVE;
            default -> Lifecycle.UNKNOWN;
        };
    }
    private static Entry entry(Input input, Lifecycle lifecycle, Category category, LocalDateTime latest, List<Reason> reasons, List<String> questions) {
        int weight = category == Category.ENDED || category == Category.BLOCKED ? 0 : reasons.stream().mapToInt(Reason::weight).sum();
        return new Entry(input.packet(), lifecycle, category, weight, input.matchStatus(), input.matchConfidence(), latest, List.copyOf(reasons), List.copyOf(questions));
    }
    private static void add(List<Reason> reasons, String code, String explanation, int weight, Support... supports) {
        reasons.add(new Reason(code, explanation, weight, List.of(supports)));
    }
    private static Support field(String field, Object value, String url) {
        return new Support(field, value == null ? "Not established" : value.toString(), url, null, "PERSISTED_FIELD", null, null);
    }
    private static Support term(Packet p, String field, Object value) {
        TermProvenance source = p.termProvenance().get(field);
        return source == null ? field("packet." + field, value, null) : new Support("packet." + field,
                value == null ? "Not established" : value.toString(), source.sourceUrl(), null, source.classification().name(), source.observedAt(), source.accession());
    }
    private static Support evidence(Evidence e) {
        return new Support("evidence." + e.factKey(), e.factValue(), e.sourceUrl(), e.id(), e.classification().name(),
                e.observedAt() == null ? null : e.observedAt().toString(), String.valueOf(e.metadata().getOrDefault("accessionNumber", "")));
    }
    private static boolean sourceEvidence(Evidence e) {
        return present(e.sourceUrl()) && (authoritative(e.classification()) || e.classification() == EvidenceClassification.ISSUER_WEBSITE_CLAIM) && present(e.factValue());
    }
    private static boolean authoritative(EvidenceClassification c) {
        return c == EvidenceClassification.SEC_FILED_FACT || c == EvidenceClassification.PLATFORM_ISSUER_CLAIM;
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String safe(String value) { return value == null ? "" : value; }
}
