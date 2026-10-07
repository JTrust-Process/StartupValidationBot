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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.diligence.DiligenceStore.EvidenceDraft;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService.SendCounts;
import com.startupvalidationbot.diligence.platform.PlatformOfferingEnricher;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryService;
import com.startupvalidationbot.diligence.discovery.AuthoritativeCampaignResolver;
import com.startupvalidationbot.offering.IntermediaryRegistry;
import com.startupvalidationbot.radar.CompanyIdentity;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.offering.SecCrowdfundingSourceAdapter;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain;
import com.startupvalidationbot.offering.intake.NativeOfferingIntakeService;
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
    private final CampaignDiscoveryService campaignDiscovery;
    private final NativeOfferingIntakeService nativeIntake;
    private final int maxOfferings;
    private final SecCrowdfundingSourceAdapter sec;
    private AuthoritativeCampaignResolver authoritativeCampaignResolver;

    @Autowired
    void setAuthoritativeCampaignResolver(AuthoritativeCampaignResolver resolver) { this.authoritativeCampaignResolver = resolver; }

    @Autowired
    public AutonomousDiligenceService(DiligenceStore store, OfferingStore offerings, RadarStore radar,
            RadarQueryService queries, SecFinancialExtractor financialExtractor, EvidenceReconciler reconciler,
            DiligenceNotificationService notifications, List<PlatformOfferingEnricher> enrichers,
            CampaignDiscoveryService campaignDiscovery, NativeOfferingIntakeService nativeIntake,
            SecCrowdfundingSourceAdapter sec,
            @Value("${diligence.max-offerings-per-run:25}") int maxOfferings) {
        this.store = store; this.offerings = offerings; this.radar = radar; this.queries = queries;
        this.financialExtractor = financialExtractor; this.reconciler = reconciler; this.notifications = notifications;
        this.campaignDiscovery = campaignDiscovery;
        this.nativeIntake = nativeIntake;
        this.sec = sec;
        Map<String, PlatformOfferingEnricher> values = new HashMap<>();
        enrichers.forEach(value -> values.put(value.platform(), value));
        this.enrichers = Map.copyOf(values);
        this.maxOfferings = Math.max(1, Math.min(maxOfferings, 100));
    }

    AutonomousDiligenceService(DiligenceStore store, OfferingStore offerings, RadarStore radar,
            RadarQueryService queries, SecFinancialExtractor financialExtractor, EvidenceReconciler reconciler,
            DiligenceNotificationService notifications, List<PlatformOfferingEnricher> enrichers,
            int maxOfferings) {
        this(store, offerings, radar, queries, financialExtractor, reconciler, notifications, enrichers,
                null, null, null, maxOfferings);
    }

    public RunResult run() {
        return run(null);
    }

    private RunResult run(Long targetOfferingId) {
        List<String> errors = new ArrayList<>();
        NativeOfferingDomain.RunResult nativeResult = targetOfferingId == null && nativeIntake != null
                ? nativeIntake.run() : NativeOfferingDomain.RunResult.empty();
        CampaignDiscoveryDomain.RunResult discovery = targetOfferingId == null && campaignDiscovery != null
                ? campaignDiscovery.discover() : CampaignDiscoveryDomain.RunResult.empty();
        List<Company> companies = radar.listCompanies();
        Map<Long, List<Offering>> byCompany = new HashMap<>();
        offerings.list(null, null, null, null).stream().filter(value -> value.radarCompanyId() != null)
                .forEach(value -> byCompany.computeIfAbsent(value.radarCompanyId(), ignored -> new ArrayList<>()).add(value));
        DiligenceRefreshSelector.Selection selection = targetOfferingId == null
                ? store.selectOfferings(maxOfferings, LocalDateTime.now()) : null;
        List<Long> offeringIds = targetOfferingId == null ? selection.ids() : List.of(targetOfferingId);
        List<String> refreshDiagnostics = new ArrayList<>();
        if (selection != null) selection.counts().forEach((bucket, count) -> refreshDiagnostics.add("selected" + bucket.name() + "=" + count));
        refreshDiagnostics.add("selectedOfferingIds=" + offeringIds);
        Map<String, Integer> secCounts = new LinkedHashMap<>();
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
            store.markDiligenceAttempt(offeringId);
            if (sec != null && "SEC_EDGAR".equals(offering.provenance()) && offering.secFilingUrl() != null) {
                var extracted = sec.enrich(offerings.storedCandidate(offeringId));
                secCounts.merge("secFilingsInspected", 1, Integer::sum);
                String extractionStatus = extracted.facts().getOrDefault("_secStatus", "UNSUPPORTED_STRUCTURE");
                secCounts.merge("secStatus" + extractionStatus, 1, Integer::sum);
                String financialStatus = "SUCCESS".equals(extractionStatus)
                        ? financialExtractor.extractionStatus(offering, extracted.facts())
                        : extractionStatus;
                secCounts.merge("secFinancial" + financialStatus, 1, Integer::sum);
                for (String counter : List.of("DocumentsAttempted", "PrimaryDocumentsAttempted", "AlternateDocumentsAttempted",
                        "OversizedSkipped", "RequestFailures", "ParseFailures")) {
                    secCounts.merge("sec" + counter, integer(extracted.facts().get("_sec" + counter)), Integer::sum);
                }
                refreshDiagnostics.add("secOffering" + offeringId + "=" + extractionStatus);
                offering = offerings.upsert(extracted, new Match(offering.radarCompanyId(), offering.matchStatus(),
                        offering.matchConfidence(), offering.matchReason())).offering();
            }
            if (offering.matchStatus() == MatchStatus.CONFIRMED) identities++;
            Map<String, String> storedFacts = offerings.facts(offering.id());
            if (authoritativeCampaignResolver != null && "SEC_EDGAR".equals(offering.provenance())) {
                var resolution = authoritativeCampaignResolver.resolve(offering, storedFacts);
                offerings.recordCampaignResolution(offering.id(), resolution.metadata());
                secCounts.merge("campaignLink" + resolution.state(), 1, Integer::sum);
                if (resolution.url() != null) {
                    offerings.attachCampaignUrl(offering.id(), resolution.platform(), resolution.url());
                    offering = offerings.find(offering.id()).orElseThrow();
                }
                storedFacts = offerings.facts(offering.id());
            }
            Map<String, String> secFacts = "SEC_EDGAR".equals(offering.provenance())
                    ? storedFacts : Map.of();
            PlatformCampaign previousCampaign = store.findCampaign(offering.id()).orElse(null);
            PlatformCampaign campaign = previousCampaign;
            List<String> sourcesChecked = new ArrayList<>();
            if (offering.secFilingUrl() != null) sourcesChecked.add("SEC_EDGAR");
            if (campaign != null) sourcesChecked.add(campaign.platform());
            String platform = normalizePlatform(offering.platform());
            PlatformOfferingEnricher enricher = enrichers.get(platform);
            if (enricher != null && offering.offeringUrl() != null && !"AMBIGUOUS".equals(storedFacts.get("_secCampaign.state"))
                    && !"AMBIGUOUS".equals(storedFacts.get("_issuerCampaign.state"))
                    && IntermediaryRegistry.fromFacts(storedFacts).state() != IntermediaryRegistry.State.AMBIGUOUS) {
                try {
                    URI url = URI.create(offering.offeringUrl());
                    if (enricher.supports(url)) {
                        PlatformCampaign observed = enricher.enrich(offering, url);
                        if (observed.issuerName() == null || !CompanyIdentity.normalizeName(offering.issuerName()).equals(CompanyIdentity.normalizeName(observed.issuerName()))) {
                            offerings.recordCampaignResolution(offering.id(), Map.of("_issuerCampaign.fetchStatus", "ISSUER_MISMATCH"));
                            throw new IllegalStateException("Public campaign issuer does not exactly match the filed issuer; review required");
                        }
                        campaign = store.upsertCampaign(observed);
                        offerings.recordCampaignResolution(offering.id(), Map.of("_issuerCampaign.fetchStatus", "SUCCESS"));
                        platformRequests.merge(platform, 1, Integer::sum);
                        campaigns++;
                        if (!sourcesChecked.contains(platform)) sourcesChecked.add(platform);
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
                    offerings.recordCampaignResolution(offering.id(), Map.of("_issuerCampaign.fetchStatus",
                            message.contains("does not exactly match") ? "ISSUER_MISMATCH" : message.contains("403") ? "HTTP_403" : message.contains("404") ? "HTTP_404" : message.toLowerCase(Locale.ROOT).contains("verification") ? "BROWSER_VERIFICATION" : "UNAVAILABLE"));
                    platformRequests.merge(platform, 1, Integer::sum);
                    platformFailure.put(platform, message);
                    if (!successfulCoverage.contains(offering.radarCompanyId() + "|" + platform)) {
                        store.availability(offering.radarCompanyId(), platform, "COULD_NOT_ESTABLISH",
                                "Campaign evidence could not be refreshed.",
                                "Can the official campaign page be reached and verified?");
                    }
                }
            }

            storedFacts = offerings.facts(offering.id());
            secFacts = "SEC_EDGAR".equals(offering.provenance()) ? storedFacts : Map.of();
            List<FinancialPeriod> financials = offering.secFilingUrl() == null
                    ? List.of() : financialExtractor.extract(offering, secFacts);
            Packet previousPacket = store.findByOffering(offeringId).orElse(null);
            if (previousPacket != null) financials = mergeFinancials(previousPacket.financials(), financials);
            List<EvidenceDraft> evidence = offering.secFilingUrl() == null
                    ? new ArrayList<>() : new ArrayList<>(secEvidence(offering, secFacts, financials));
            if (offering.secFilingUrl() != null) evidence.addAll(financialExtractor.evidence(offering, secFacts));
            Map<String, String> platformFacts = campaign == null ? Map.of() : campaign.facts();
            if (campaign != null) evidence.addAll(platformEvidence(campaign));
            EffectiveOfferingTerms.Projection terms = EffectiveOfferingTerms.resolve(offering, campaign, storedFacts);
            List<String> discrepancies = new ArrayList<>(reconciler.reconcile(secFacts, platformFacts));
            discrepancies.addAll(terms.issues());
            if ("AMBIGUOUS".equals(storedFacts.get("_issuerCampaign.state")) || "ISSUER_MISMATCH".equals(storedFacts.get("_issuerCampaign.fetchStatus"))) discrepancies.add("Campaign link identity requires review; intermediary identity is not issuer identity");
            Analysis analysis = queries.detail(offering.radarCompanyId()).latestAnalysis();
            if (analysis == null || "DETERMINISTIC".equals(analysis.analysisOrigin())) aiFallbacks++;
            PacketStatus status = status(offering, terms, financials, discrepancies);
            if (status == PacketStatus.READY) ready++;
            else if (status == PacketStatus.PARTIAL) partial++;
            else if (status == PacketStatus.NEEDS_REVIEW) review++;
            List<String> missing = missing(terms, financials);
            List<String> questions = questions(offering, terms, financials, discrepancies);
            String summary = summary(offering, analysis, status);
            String fingerprint = ContentHash.sha256(offering.id() + "|" + offering.matchStatus() + "|"
                    + secFacts + "|" + (campaign == null ? "" : campaign.sourceFingerprint()) + "|" + status);
            Packet packet = store.savePacket(new PacketDraft(offering.radarCompanyId(), offering.id(), status,
                    offering.matchStatus().name(), completeness(offering, terms, financials), confidence(offering, campaign),
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
        int queueAfter = store.actionablePacketCount();
        secCounts.forEach((key, count) -> refreshDiagnostics.add(key + "=" + count));
        return new RunResult(companies.size(), considered, identities, campaigns, ready, partial, review,
                platformErrors, aiFallbacks, queued, sends.sent(), sends.failed(), List.copyOf(errors), discovery,
                nativeResult, nativeResult.reviewQueueBefore(), queueAfter, List.copyOf(refreshDiagnostics));
    }

    public Packet refresh(long packetId) {
        Packet packet = store.find(packetId).orElseThrow(() -> new IllegalArgumentException("Diligence packet not found: " + packetId));
        run(packet.offeringId());
        return store.find(packet.id()).orElseThrow();
    }

    static List<FinancialPeriod> mergeFinancials(List<FinancialPeriod> retained, List<FinancialPeriod> incoming) {
        Map<String, FinancialPeriod> result = new LinkedHashMap<>();
        retained.forEach(p -> result.put(p.period(), p));
        for (FinancialPeriod p : incoming) {
            FinancialPeriod old = result.get(p.period());
            if (old == null) { result.put(p.period(), p); continue; }
            result.put(p.period(), new FinancialPeriod(p.period(), first(p.revenue(), old.revenue()),
                    first(p.costOfGoods(), old.costOfGoods()), first(p.netIncome(), old.netIncome()),
                    first(p.cash(), old.cash()), first(p.assets(), old.assets()), first(p.liabilities(), old.liabilities()),
                    first(p.shortTermDebt(), old.shortTermDebt()), first(p.longTermDebt(), old.longTermDebt()),
                    first(p.taxesPaid(), old.taxesPaid()), p.sourceAccessionNumber(), p.sourceUrl(),
                    first(p.grossProfit(), old.grossProfit()), first(p.currentAssets(), old.currentAssets()),
                    first(p.currentLiabilities(), old.currentLiabilities()), first(p.equity(), old.equity()),
                    first(p.periodEndingDate(), old.periodEndingDate())));
        }
        return List.copyOf(result.values());
    }
    private static <T> T first(T incoming, T retained) { return incoming == null ? retained : incoming; }

    private void initializeAvailability(List<Company> companies, Map<Long,List<Offering>> byCompany) {
        for (Company company : companies) {
            List<Offering> found = byCompany.getOrDefault(company.id(), List.of());
            List<Offering> secFound = found.stream().filter(value -> value.secFilingUrl() != null).toList();
            store.availability(company.id(), "SEC_REG_CF", secFound.isEmpty() ? "NONE_FOUND" : "FOUND",
                    secFound.isEmpty() ? "SEC Reg CF records checked; no matched offering found."
                            : secFound.size() + " matched SEC Reg CF offering record(s).", null);
            for (String platform : PLATFORM_SOURCES) {
                boolean known = found.stream().anyMatch(value -> platform.equals(normalizePlatform(value.platform()))
                        || hostMatches(value.offeringUrl(), platform));
                if (known) {
                    store.availability(company.id(), platform, "FOUND",
                            "A current public offering record was captured by native intake or SEC evidence.", null);
                } else {
                    store.availabilityIfAbsent(company.id(), platform, "NOT_CHECKED",
                            "No bounded public campaign check has been recorded yet.",
                            "Does this company have a public " + platform + " campaign not linked from SEC evidence?");
                }
            }
        }
    }

    static PacketStatus status(Offering offering, EffectiveOfferingTerms.Projection terms,
            List<FinancialPeriod> financials, List<String> discrepancies) {
        if (List.of(MatchStatus.AMBIGUOUS, MatchStatus.UNMATCHED, MatchStatus.REJECTED)
                .contains(offering.matchStatus()) || "NEEDS_REVIEW".equals(offering.reconciliationStatus())
                || !discrepancies.isEmpty()) return PacketStatus.NEEDS_REVIEW;
        boolean secReconciled = "Established from SEC-filed offering".equals(terms.secReconciliationStatus());
        if (offering.matchStatus() == MatchStatus.CONFIRMED && secReconciled
                && terms.officialCampaignVerified() && !financials.isEmpty()) return PacketStatus.READY;
        if (offering.matchStatus() == MatchStatus.CONFIRMED) return PacketStatus.PARTIAL;
        if (offering.matchStatus() == MatchStatus.LIKELY && nativePlatformLinked(offering, terms)
                && terms.hasUsablePlatformTerms()) return PacketStatus.PARTIAL;
        return PacketStatus.NEEDS_REVIEW;
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
        add(evidence, offering, "valuation_or_cap", offering.valuationOrCap(), null);
        for (String key : List.of("issuerWebsite", "intermediaryName", "intermediaryCik", "intermediaryWebsite", "offeringUrl")) {
            String clue = value(facts, key, key.toUpperCase(Locale.ROOT));
            if (clue != null) add(evidence, offering, key, clue, null);
        }
        String compensation = value(facts, "COMPENSATIONAMOUNT", "compensationAmount");
        if (compensation != null) add(evidence, offering, "intermediary_compensation", compensation, null);
        for (var item : facts.entrySet()) {
            if (!item.getKey().startsWith("_secTerm.fact.") || !item.getKey().endsWith(".value")) continue;
            String prefix = item.getKey().substring(0, item.getKey().length() - 6);
            String field = prefix.substring("_secTerm.fact.".length()).replaceFirst("\\.\\d+$", "");
            String semantic = facts.get(prefix + ".type");
            evidence.add(new EvidenceDraft("SEC_EDGAR", facts.getOrDefault(prefix + ".sourceUrl", facts.getOrDefault("_sourceUrl." + field, offering.secFilingUrl())),
                    "SEC Form " + offering.filingType(), "filed_" + field + "_" + (semantic == null ? "UNKNOWN" : semantic), item.getValue(), null,
                    EvidenceClassification.SEC_FILED_FACT, 95, facts.getOrDefault(prefix + ".excerpt", "Explicit filed term"),
                    Map.of("accessionNumber", offering.accessionNumber(), "semanticType", semantic == null ? "UNKNOWN" : semantic)));
        }
        var identity = IntermediaryRegistry.fromFacts(facts);
        if (identity.family() != null) evidence.add(new EvidenceDraft("SEC_EDGAR", offering.secFilingUrl(), "SEC filed intermediary",
                "platform_family", identity.family(), null, EvidenceClassification.SEC_FILED_FACT, 95, identity.reason(),
                Map.of("accessionNumber", offering.accessionNumber(), "identityState", identity.state().name())));
        for (String key : List.of("COMISSIONCRD", "COMMISSIONCRD", "COMMISSIONFILENUMBER", "_secTerm.rawSecurity", "_secTerm.rawSecuritySubtype")) {
            String raw = IntermediaryRegistry.value(facts, key);
            if (raw != null) add(evidence, offering, key, raw, null);
        }
        String link = facts.get("_issuerCampaign.url");
        if (link != null) evidence.add(new EvidenceDraft(facts.getOrDefault("_issuerCampaign.source", "ISSUER_WEBSITE_CLAIM"),
                facts.getOrDefault("_issuerCampaign.sourceUrl", offering.secFilingUrl()), "Explicit issuer campaign link (fetch verification separate)",
                "campaign_link", link, null, "SEC_FILED_FACT".equals(facts.get("_issuerCampaign.source")) ? EvidenceClassification.SEC_FILED_FACT : EvidenceClassification.ISSUER_WEBSITE_CLAIM,
                90, facts.getOrDefault("_issuerCampaign.reason", "Explicit observed link"), Map.of("linkState", facts.getOrDefault("_issuerCampaign.state", "UNKNOWN"))));
        if (facts.containsKey("_issuerCampaign.fetchStatus")) evidence.add(new EvidenceDraft("PUBLIC_FETCH_DIAGNOSTIC", offering.offeringUrl(),
                "Campaign fetch diagnostic (not an offering term)", "campaign_fetch_status", facts.get("_issuerCampaign.fetchStatus"), null,
                EvidenceClassification.DETERMINISTIC_INFERENCE, 100, "Access failure is separate from retained issuer/link/term evidence.", Map.of()));
        Map<String, String> keys = Map.of("security_type", "securityType", "minimum_investment", "minimumInvestment",
                "target_amount", "targetAmount", "maximum_amount", "maximumAmount", "amount_raised", "amountRaised", "deadline", "deadline");
        return evidence.stream().map(item -> {
            String key = keys.get(item.factKey());
            if (key == null || !facts.containsKey("_sourceUrl." + key)) return item;
            return new EvidenceDraft(item.sourceType(), facts.get("_sourceUrl." + key), item.sourceTitle(), item.factKey(),
                    item.factValue(), item.period(), item.classification(), item.confidence(), item.rawExcerpt(),
                    Map.of("accessionNumber", facts.getOrDefault("_accession." + key, offering.accessionNumber())));
        }).toList();
    }

    private static void add(List<EvidenceDraft> target, Offering offering, String key, Object value, String period) {
        if (value == null) return;
        target.add(new EvidenceDraft("SEC_EDGAR", offering.secFilingUrl(), "SEC Form " + offering.filingType(),
                key, value.toString(), period, EvidenceClassification.SEC_FILED_FACT, 95,
                "As-filed structured Form C field.", Map.of("accessionNumber", offering.accessionNumber())));
    }

    private static List<EvidenceDraft> platformEvidence(PlatformCampaign campaign) {
        List<EvidenceDraft> evidence = new ArrayList<>();
        campaign.facts().forEach((key, value) -> { if (!key.startsWith("_")) evidence.add(new EvidenceDraft(campaign.platform(),
                campaign.campaignUrl(), campaign.platform() + " public campaign", key, value, null,
                EvidenceClassification.PLATFORM_ISSUER_CLAIM, 70, "Public campaign-page claim.", Map.of())); });
        return evidence;
    }

    static int completeness(Offering offering, EffectiveOfferingTerms.Projection terms,
            List<FinancialPeriod> financials) {
        int score = 15;
        if (terms.platformIdentityStatus().startsWith("SEC_CONFIRMED") || terms.platformIdentityStatus().startsWith("EXPLICIT_DOMAIN_CONFIRMED") || terms.officialCampaignVerified()) score += 5;
        if (terms.amountRaised() != null) score += 5;
        if (offering.matchStatus() == MatchStatus.CONFIRMED) score += 15;
        if (terms.securityType() != null) score += 10;
        if (terms.minimumInvestment() != null) score += 5;
        if (terms.targetAmount() != null || terms.maximumAmount() != null) score += 10;
        if (terms.valuation() != null || terms.valuationCap() != null) score += 5;
        if (terms.deadline() != null) score += 5;
        if (!financials.isEmpty()) score += 20;
        if (terms.officialCampaignVerified()) score += 10;
        return Math.min(100, score);
    }

    private static int confidence(Offering offering, PlatformCampaign campaign) {
        int value = offering.matchConfidence();
        if (campaign != null) value = Math.min(100, value + 5);
        return value;
    }

    static List<String> missing(EffectiveOfferingTerms.Projection terms, List<FinancialPeriod> financials) {
        List<String> missing = new ArrayList<>();
        if (!terms.officialCampaignVerified()) missing.add("Verified public campaign page");
        if (terms.platformIdentityStatus().equals("Not established") || terms.platformIdentityStatus().startsWith("AMBIGUOUS")) missing.add("Unambiguous intermediary/platform identity");
        if (terms.amountRaised() == null) missing.add("Final filed amount raised (commitments are distinct)");
        if (financials.isEmpty()) missing.add("Structured multi-period SEC financials");
        if (terms.securityType() == null) missing.add("Security type");
        if (terms.minimumInvestment() == null) missing.add("Minimum investment");
        if (terms.targetAmount() == null && terms.maximumAmount() == null) missing.add("Target or maximum amount");
        if (terms.valuation() == null && terms.valuationCap() == null) missing.add("Valuation or valuation cap");
        if (terms.deadline() == null) missing.add("Offering deadline");
        return List.copyOf(missing);
    }

    static List<String> questions(Offering offering, EffectiveOfferingTerms.Projection terms,
            List<FinancialPeriod> financials, List<String> discrepancies) {
        List<String> questions = new ArrayList<>();
        if (offering.matchStatus() != MatchStatus.CONFIRMED && !nativePlatformLinked(offering, terms)) {
            questions.add("Does the issuer website domain independently match the tracked company?");
        }
        if (!terms.officialCampaignVerified()) questions.add("What is the canonical public campaign URL, if one exists?");
        if ("Not established".equals(terms.secReconciliationStatus())) {
            questions.add("Can the platform issuer be reconciled to an SEC Form C legal issuer?");
        }
        if (financials.isEmpty()) questions.add("Which financial periods are disclosed in the filed Form C exhibits?");
        if (!discrepancies.isEmpty()) questions.add("Which source reflects the current filed offering terms?");
        return List.copyOf(questions);
    }

    private static boolean nativePlatformLinked(Offering offering, EffectiveOfferingTerms.Projection terms) {
        return "PLATFORM_OFFERING".equals(offering.provenance()) && terms.officialCampaignVerified()
                && List.of("PLATFORM_CONFIRMED", "SEC_RECONCILED").contains(offering.reconciliationStatus());
    }

    private static String summary(Offering offering, Analysis analysis, PacketStatus status) {
        String base = analysis == null ? offering.issuerName() + (offering.secFilingUrl() == null
                ? " has a public platform Regulation Crowdfunding listing; SEC reconciliation is not yet established."
                : " has an SEC-filed Regulation Crowdfunding offering.")
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
    private static int integer(String value) { try { return Integer.parseInt(value); } catch (RuntimeException ignored) { return 0; } }
    private static String safe(RuntimeException error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return SafeUrl.redactUrlsIn(message);
    }
}
