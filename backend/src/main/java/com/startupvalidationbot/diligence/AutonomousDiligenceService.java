package com.startupvalidationbot.diligence;

import static com.startupvalidationbot.diligence.DiligenceDomain.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.diligence.DiligenceStore.EvidenceDraft;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService.SendCounts;
import com.startupvalidationbot.diligence.platform.PlatformOfferingEnricher;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.radar.ContentHash;
import com.startupvalidationbot.radar.RadarDomain.Analysis;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.SafeUrl;
import com.startupvalidationbot.radar.service.RadarQueryService;

@Service
public class AutonomousDiligenceService {
    private static final List<String> PLATFORM_SOURCES = List.of("WEFUNDER", "REPUBLIC", "STARTENGINE");
    private final DiligenceStore store;
    private final OfferingStore offerings;
    private final RadarStore radar;
    private final RadarQueryService queries;
    private final SecFinancialExtractor financialExtractor;
    private final EvidenceReconciler reconciler;
    private final DiligenceNotificationService notifications;
    private final Map<String, PlatformOfferingEnricher> enrichers;
    private final int maxOfferings;

    public AutonomousDiligenceService(DiligenceStore store, OfferingStore offerings, RadarStore radar,
            RadarQueryService queries, SecFinancialExtractor financialExtractor, EvidenceReconciler reconciler,
            DiligenceNotificationService notifications, List<PlatformOfferingEnricher> enrichers,
            @Value("${diligence.max-offerings-per-run:25}") int maxOfferings) {
        this.store = store; this.offerings = offerings; this.radar = radar; this.queries = queries;
        this.financialExtractor = financialExtractor; this.reconciler = reconciler; this.notifications = notifications;
        Map<String, PlatformOfferingEnricher> values = new HashMap<>();
        enrichers.forEach(value -> values.put(value.platform(), value));
        this.enrichers = Map.copyOf(values);
        this.maxOfferings = Math.max(1, Math.min(maxOfferings, 100));
    }

    public RunResult run() {
        return run(null);
    }

    private RunResult run(Long targetOfferingId) {
        List<String> errors = new ArrayList<>();
        List<Company> companies = radar.listCompanies();
        Map<Long, List<Offering>> byCompany = new HashMap<>();
        offerings.list(null, null, null, null).stream().filter(value -> value.radarCompanyId() != null)
                .forEach(value -> byCompany.computeIfAbsent(value.radarCompanyId(), ignored -> new ArrayList<>()).add(value));
        List<Long> offeringIds = targetOfferingId == null
                ? store.eligibleOfferingIds(maxOfferings) : List.of(targetOfferingId);
        Set<Long> checkedCompanyIds = targetOfferingId == null ? null : offeringIds.stream()
                .map(offerings::find).flatMap(java.util.Optional::stream).map(Offering::radarCompanyId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        initializeAvailability(checkedCompanyIds == null ? companies : companies.stream()
                .filter(company -> checkedCompanyIds.contains(company.id())).toList(), byCompany);

        int considered = 0, identities = 0, campaigns = 0, ready = 0, partial = 0, review = 0;
        int platformErrors = 0, aiFallbacks = 0, queued = 0;
        Map<String, Integer> platformRequests = new HashMap<>();
        Map<String, Integer> platformCampaigns = new HashMap<>();
        Map<String, String> platformFailure = new HashMap<>();
        Set<String> successfulCoverage = new HashSet<>();
        for (Long offeringId : offeringIds) {
            considered++;
            Offering offering = offerings.find(offeringId).orElse(null);
            if (offering == null || offering.radarCompanyId() == null) continue;
            if (offering.matchStatus() == MatchStatus.CONFIRMED) identities++;
            Map<String, String> secFacts = offerings.facts(offering.id());
            List<String> sourcesChecked = new ArrayList<>(List.of("SEC_EDGAR"));
            PlatformCampaign campaign = null;
            PlatformCampaign previousCampaign = store.findCampaign(offering.id()).orElse(null);
            String platform = normalizePlatform(offering.platform());
            PlatformOfferingEnricher enricher = enrichers.get(platform);
            if (enricher != null && offering.offeringUrl() != null) {
                try {
                    URI url = URI.create(offering.offeringUrl());
                    if (enricher.supports(url)) {
                        campaign = store.upsertCampaign(enricher.enrich(offering, url));
                        platformRequests.merge(platform, 1, Integer::sum);
                        campaigns++; sourcesChecked.add(platform);
                        platformCampaigns.merge(platform, 1, Integer::sum);
                        successfulCoverage.add(offering.radarCompanyId() + "|" + platform);
                        store.availability(offering.radarCompanyId(), platform, "FOUND",
                                "Public campaign resolved and checked.", null);
                    } else {
                        store.availability(offering.radarCompanyId(), platform, "COULD_NOT_ESTABLISH",
                                "The known URL is not a permitted canonical " + platform + " campaign URL.",
                                "What is the official public campaign URL?");
                    }
                } catch (RuntimeException error) {
                    platformErrors++;
                    String message = safe(error);
                    platformRequests.merge(platform, 1, Integer::sum);
                    platformFailure.put(platform, message);
                    if (!successfulCoverage.contains(offering.radarCompanyId() + "|" + platform)) {
                        store.availability(offering.radarCompanyId(), platform, "COULD_NOT_ESTABLISH",
                                "Campaign evidence could not be refreshed.",
                                "Can the official campaign page be reached and verified?");
                    }
                }
            }

            List<FinancialPeriod> financials = financialExtractor.extract(offering, secFacts);
            List<EvidenceDraft> evidence = secEvidence(offering, secFacts, financials);
            Map<String, String> platformFacts = campaign == null ? Map.of() : campaign.facts();
            if (campaign != null) evidence.addAll(platformEvidence(campaign));
            List<String> discrepancies = reconciler.reconcile(secFacts, platformFacts);
            Analysis analysis = queries.detail(offering.radarCompanyId()).latestAnalysis();
            if (analysis == null || "DETERMINISTIC".equals(analysis.analysisOrigin())) aiFallbacks++;
            PacketStatus status = status(offering, campaign, financials);
            if (status == PacketStatus.READY) ready++;
            else if (status == PacketStatus.PARTIAL) partial++;
            else if (status == PacketStatus.NEEDS_REVIEW) review++;
            List<String> missing = missing(offering, campaign, financials);
            List<String> questions = questions(offering, campaign, financials, discrepancies);
            String summary = summary(offering, analysis, status);
            String fingerprint = ContentHash.sha256(offering.id() + "|" + offering.matchStatus() + "|"
                    + secFacts + "|" + (campaign == null ? "" : campaign.sourceFingerprint()) + "|" + status);
            Packet packet = store.savePacket(new PacketDraft(offering.radarCompanyId(), offering.id(), status,
                    offering.matchStatus().name(), completeness(offering, campaign, financials), confidence(offering, campaign),
                    summary, analysis == null ? List.of() : analysis.bullCase(),
                    analysis == null ? List.of() : analysis.bearCase(),
                    analysis == null ? deterministicRisks(offering) : analysis.risks(), questions, discrepancies,
                    analysis == null ? List.of("Monitor SEC amendments and campaign status changes.") : analysis.monitoringTriggers(),
                    sourcesChecked, missing, fingerprint, evidence, financials));
            if (packet.status() == PacketStatus.READY) {
                if (notifications.queueReady(packet)) queued++;
            } else if (notifications.queueNewConfirmed(offering, packet)) {
                queued++;
            }
            if (campaign != null && notifications.queueMaterialChange(previousCampaign, campaign, packet)) queued++;
            offerings.markResolution(offering.id(), offering.matchStatus().name(), offering.matchReason(), sourcesChecked);
        }
        for (String platform : PLATFORM_SOURCES) {
            int requests = platformRequests.getOrDefault(platform, 0);
            if (targetOfferingId != null && requests == 0) continue;
            String failure = platformFailure.get(platform);
            store.markPlatform(platform, failure != null ? "DEGRADED" : requests > 0 ? "OK" : "NO_TARGETS",
                    requests, platformCampaigns.getOrDefault(platform, 0), failure);
        }
        SendCounts sends = notifications.sendPending();
        return new RunResult(companies.size(), considered, identities, campaigns, ready, partial, review,
                platformErrors, aiFallbacks, queued, sends.sent(), sends.failed(), List.copyOf(errors));
    }

    public Packet refresh(long packetId) {
        Packet packet = store.find(packetId).orElseThrow(() -> new IllegalArgumentException("Diligence packet not found: " + packetId));
        run(packet.offeringId());
        return store.find(packet.id()).orElseThrow();
    }

    private void initializeAvailability(List<Company> companies, Map<Long,List<Offering>> byCompany) {
        for (Company company : companies) {
            List<Offering> found = byCompany.getOrDefault(company.id(), List.of());
            store.availability(company.id(), "SEC_REG_CF", found.isEmpty() ? "NONE_FOUND" : "FOUND",
                    found.isEmpty() ? "SEC Reg CF records checked; no matched offering found."
                            : found.size() + " matched SEC Reg CF offering record(s).", null);
            for (String platform : PLATFORM_SOURCES) {
                boolean known = found.stream().anyMatch(value -> platform.equals(normalizePlatform(value.platform()))
                        || hostMatches(value.offeringUrl(), platform));
                store.availability(company.id(), platform, known ? "POSSIBLE" : "COULD_NOT_ESTABLISH",
                        known ? "Platform evidence is queued for targeted verification."
                                : "No SEC-known or official campaign URL was available for targeted lookup.",
                        known ? null : "Does this company have a public " + platform + " campaign not linked from SEC evidence?");
            }
        }
    }

    private static PacketStatus status(Offering offering, PlatformCampaign campaign, List<FinancialPeriod> financials) {
        if (offering.matchStatus() != MatchStatus.CONFIRMED) return PacketStatus.NEEDS_REVIEW;
        return campaign != null && !financials.isEmpty() ? PacketStatus.READY : PacketStatus.PARTIAL;
    }

    private static List<EvidenceDraft> secEvidence(Offering offering, Map<String,String> facts,
            List<FinancialPeriod> financials) {
        List<EvidenceDraft> evidence = new ArrayList<>();
        add(evidence, offering, "issuer_name", offering.issuerName(), null);
        add(evidence, offering, "security_type", offering.securityType(), null);
        add(evidence, offering, "minimum_investment", offering.minimumInvestment(), null);
        add(evidence, offering, "target_amount", offering.targetAmount(), null);
        add(evidence, offering, "maximum_amount", offering.maximumAmount(), null);
        add(evidence, offering, "amount_raised", offering.amountRaised(), null);
        add(evidence, offering, "deadline", offering.deadline(), null);
        for (FinancialPeriod period : financials) {
            add(evidence, offering, "revenue", period.revenue(), period.period());
            add(evidence, offering, "net_income", period.netIncome(), period.period());
            add(evidence, offering, "cash", period.cash(), period.period());
            add(evidence, offering, "assets", period.assets(), period.period());
            add(evidence, offering, "short_term_debt", period.shortTermDebt(), period.period());
            add(evidence, offering, "long_term_debt", period.longTermDebt(), period.period());
        }
        String compensation = value(facts, "COMPENSATIONAMOUNT", "compensationAmount");
        if (compensation != null) add(evidence, offering, "intermediary_compensation", compensation, null);
        return evidence;
    }

    private static void add(List<EvidenceDraft> target, Offering offering, String key, Object value, String period) {
        if (value == null) return;
        target.add(new EvidenceDraft("SEC_EDGAR", offering.secFilingUrl(), "SEC Form " + offering.filingType(),
                key, value.toString(), period, EvidenceClassification.SEC_FILED_FACT, 95,
                "As-filed structured Form C field.", Map.of("accessionNumber", offering.accessionNumber())));
    }

    private static List<EvidenceDraft> platformEvidence(PlatformCampaign campaign) {
        List<EvidenceDraft> evidence = new ArrayList<>();
        campaign.facts().forEach((key, value) -> evidence.add(new EvidenceDraft(campaign.platform(),
                campaign.campaignUrl(), campaign.platform() + " public campaign", key, value, null,
                EvidenceClassification.PLATFORM_ISSUER_CLAIM, 70, "Public campaign-page claim.", Map.of())));
        return evidence;
    }

    private static int completeness(Offering offering, PlatformCampaign campaign, List<FinancialPeriod> financials) {
        int score = 25;
        if (offering.matchStatus() == MatchStatus.CONFIRMED) score += 20;
        if (offering.securityType() != null) score += 10;
        if (offering.targetAmount() != null || offering.maximumAmount() != null) score += 10;
        if (offering.deadline() != null) score += 5;
        if (!financials.isEmpty()) score += 20;
        if (campaign != null) score += 10;
        return Math.min(100, score);
    }

    private static int confidence(Offering offering, PlatformCampaign campaign) {
        int value = offering.matchConfidence();
        if (campaign != null) value = Math.min(100, value + 5);
        return value;
    }

    private static List<String> missing(Offering offering, PlatformCampaign campaign, List<FinancialPeriod> financials) {
        List<String> missing = new ArrayList<>();
        if (campaign == null) missing.add("Verified public campaign page");
        if (financials.isEmpty()) missing.add("Structured multi-period SEC financials");
        if (offering.minimumInvestment() == null) missing.add("Minimum investment");
        if (offering.valuationOrCap() == null) missing.add("Valuation or valuation cap");
        return List.copyOf(missing);
    }

    private static List<String> questions(Offering offering, PlatformCampaign campaign,
            List<FinancialPeriod> financials, List<String> discrepancies) {
        List<String> questions = new ArrayList<>();
        if (offering.matchStatus() != MatchStatus.CONFIRMED) questions.add("Does the issuer website domain independently match the tracked company?");
        if (campaign == null) questions.add("What is the canonical public campaign URL, if one exists?");
        if (financials.isEmpty()) questions.add("Which financial periods are disclosed in the filed Form C exhibits?");
        if (!discrepancies.isEmpty()) questions.add("Which source reflects the current filed offering terms?");
        return List.copyOf(questions);
    }

    private static String summary(Offering offering, Analysis analysis, PacketStatus status) {
        String base = analysis == null ? offering.issuerName() + " has an SEC-filed Regulation Crowdfunding offering."
                : analysis.summary();
        return base + " Diligence status: " + status.name().replace('_', ' ') + ".";
    }

    private static List<String> deterministicRisks(Offering offering) {
        List<String> risks = new ArrayList<>(List.of("Private securities are illiquid and may result in total loss."));
        if (offering.matchStatus() != MatchStatus.CONFIRMED) risks.add("Issuer identity is not yet confirmed by independent domain evidence.");
        return List.copyOf(risks);
    }

    private static String normalizePlatform(String value) {
        if (value == null) return "UNKNOWN";
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.contains("WEFUNDER")) return "WEFUNDER";
        if (normalized.contains("REPUBLIC") || normalized.contains("OPENDEAL")) return "REPUBLIC";
        if (normalized.contains("STARTENGINE")) return "STARTENGINE";
        return normalized;
    }
    private static boolean hostMatches(String url, String platform) {
        if (url == null) return false;
        try {
            String host = URI.create(url).getHost();
            return host != null && host.toUpperCase(Locale.ROOT).contains(platform);
        }
        catch (RuntimeException error) { return false; }
    }
    private static String value(Map<String,String> values, String... keys) { for (String key : keys) if (values.get(key) != null) return values.get(key); return null; }
    private static String safe(RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return SafeUrl.redactUrlsIn(message);
    }
}
