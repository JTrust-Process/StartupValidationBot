package com.startupvalidationbot.offering.intake;

import static com.startupvalidationbot.offering.intake.NativeOfferingDomain.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingMatchService;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.offering.PlatformNormalizer;
import com.startupvalidationbot.radar.CompanyIdentity;
import com.startupvalidationbot.radar.ContentHash;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.EvidenceClassification;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.service.RadarDiscoveryService;
import com.startupvalidationbot.radar.source.PublicSourceUrlPolicy;
import com.startupvalidationbot.radar.source.SourceFetchException;

@Service
public class NativeOfferingIntakeService {
    private static final List<String> P0_PLATFORMS = List.of("Republic", "StartEngine", "Wefunder");

    private final List<NativeOfferingSourceAdapter> adapters;
    private final NativeOfferingStore nativeStore;
    private final OfferingStore offeringStore;
    private final DiligenceStore diligenceStore;
    private final OfferingMatchService matcher;
    private final RadarStore radarStore;
    private final RadarDiscoveryService radarDiscovery;
    private final int maxPerPlatform;
    private final int maxDetails;
    private final int maxNewCompanies;

    public NativeOfferingIntakeService(List<NativeOfferingSourceAdapter> adapters,
            NativeOfferingStore nativeStore, OfferingStore offeringStore, DiligenceStore diligenceStore,
            OfferingMatchService matcher, RadarStore radarStore, RadarDiscoveryService radarDiscovery,
            @Value("${native-offering-intake.max-per-platform:25}") int maxPerPlatform,
            @Value("${native-offering-intake.max-detail-pages-per-platform:10}") int maxDetails,
            @Value("${native-offering-intake.max-new-companies-per-run:25}") int maxNewCompanies) {
        this.adapters = List.copyOf(adapters);
        this.nativeStore = nativeStore;
        this.offeringStore = offeringStore;
        this.diligenceStore = diligenceStore;
        this.matcher = matcher;
        this.radarStore = radarStore;
        this.radarDiscovery = radarDiscovery;
        this.maxPerPlatform = clamp(maxPerPlatform, 1, 25);
        this.maxDetails = clamp(maxDetails, 0, 25);
        this.maxNewCompanies = clamp(maxNewCompanies, 1, 25);
    }

    public RunResult run() {
        int queueBefore = diligenceStore.actionablePacketCount();
        List<SourceResult> results = adapters.stream()
                .sorted(Comparator.comparingInt(value -> "SEC_REG_CF".equals(value.source()) ? 0 : 1))
                .map(value -> value.discover(maxPerPlatform, maxDetails)).toList();
        List<String> errors = new ArrayList<>();
        results.forEach(value -> errors.addAll(value.errors()));

        List<NativeOfferingCandidate> sec = collapseSecLifecycle(results.stream()
                .filter(value -> "SEC_REG_CF".equals(value.source()))
                .flatMap(value -> value.candidates().stream()).toList());
        Map<String, MutableSource> sourceCounts = new LinkedHashMap<>();
        results.forEach(value -> sourceCounts.put(value.source(), new MutableSource(value)));

        int candidatesFound = results.stream().mapToInt(value -> value.candidates().size()).sum();
        int newCompanies = 0, newOfferings = 0;
        int updatedOfferings = 0, duplicates = 0, possible = 0, rejected = 0, reconciled = 0;
        Set<String> activeOpportunityKeys = new HashSet<>();
        Set<Long> matchedCompanyIds = new HashSet<>();
        int secClassified = (int) sec.stream().filter(value -> P0_PLATFORMS.contains(value.platform())).count();
        List<Company> companies = new ArrayList<>(radarStore.listCompanies());

        List<NativeOfferingCandidate> ordered = new ArrayList<>(sec);
        results.stream().filter(value -> !"SEC_REG_CF".equals(value.source()))
                .forEach(value -> ordered.addAll(value.candidates()));

        for (NativeOfferingCandidate raw : ordered) {
            MutableSource counts = sourceCounts.computeIfAbsent(sourceKey(raw), ignored -> MutableSource.synthetic(raw));
            Reconciled candidate = reconcile(raw, sec);
            if (candidate.status == ReconciliationStatus.SEC_RECONCILED && !raw.source().startsWith("SEC_")) {
                reconciled++;
            }
            NativeOfferingCandidate value = candidate.candidate;
            if (value.actionableRegCf()) {
                activeOpportunityKeys.add(opportunityKey(value));
                counts.active++;
            }

            if (!value.actionableRegCf() && !value.reviewableRecentSecRegCf()) {
                nativeStore.saveCandidate(value, candidate.status, "NOT_ACTIONABLE", 0, false);
                rejected++; counts.rejected++;
                continue;
            }

            Match match = matcher.match(value.companyName(), value.issuerWebsite(), companies);
            if (candidate.ambiguous || match.status() == MatchStatus.AMBIGUOUS
                    || match.status() == MatchStatus.REJECTED
                    || match.status() == MatchStatus.LIKELY && match.confidence() < 75) {
                nativeStore.saveCandidate(value, ReconciliationStatus.NEEDS_REVIEW,
                        match.status().name(), match.confidence(), false);
                possible++; rejected++; counts.rejected++;
                continue;
            }

            boolean createdCompany = false;
            if (match.status() == MatchStatus.UNMATCHED) {
                if (newCompanies >= maxNewCompanies) {
                    nativeStore.saveCandidate(value, ReconciliationStatus.NEEDS_REVIEW,
                            "NEW_COMPANY_LIMIT", 0, false);
                    possible++; rejected++; counts.rejected++;
                    continue;
                }
                var upsert = radarDiscovery.ingestOfficialOffering(radarCandidate(value),
                        sourceName(value), sourceUrl(value));
                createdCompany = upsert.created();
                if (createdCompany) newCompanies++;
                companies = new ArrayList<>(radarStore.listCompanies());
                match = matcher.match(value.companyName(), value.issuerWebsite(), companies);
                if (value.source().startsWith("SEC_") && match.companyId() != null) {
                    match = new Match(match.companyId(), MatchStatus.CONFIRMED, Math.max(95, match.confidence()),
                            "Radar company created from the SEC-filed issuer identity.");
                }
            } else {
                if (match.companyId() != null) matchedCompanyIds.add(match.companyId());
                counts.matched++;
            }

            if (match.companyId() == null || match.status() == MatchStatus.UNMATCHED) {
                nativeStore.saveCandidate(value, ReconciliationStatus.NEEDS_REVIEW,
                        match.status().name(), match.confidence(), false);
                possible++; rejected++; counts.rejected++;
                continue;
            }

            nativeStore.saveCandidate(value, candidate.status,
                    match.status().name(), match.confidence(), true);
            long offeringId;
            boolean offeringCreated;
            if (value.source().startsWith("SEC_")) {
                var upsert = offeringStore.upsert(secCandidate(value), match);
                offeringId = upsert.offering().id();
                offeringCreated = upsert.created();
            } else {
                var upsert = nativeStore.upsertPlatformOffering(value, match, candidate.status);
                offeringId = upsert.offeringId();
                offeringCreated = upsert.created();
            }
            if (offeringCreated) { newOfferings++; counts.inserted++; }
            else { updatedOfferings++; duplicates++; }
            saveCampaignEvidence(offeringId, value);
            if (!createdCompany && match.status() == MatchStatus.LIKELY) possible++;
        }

        int secDerivedWefunder = (int) sec.stream()
                .filter(value -> "Wefunder".equals(value.platform()))
                .filter(value -> value.actionableRegCf() || value.reviewableRecentSecRegCf())
                .count();
        sourceCounts.putIfAbsent("WEFUNDER", MutableSource.wefunder(secDerivedWefunder));
        List<SourceDiagnostic> diagnostics = sourceCounts.values().stream().map(MutableSource::diagnostic).toList();
        diagnostics.forEach(nativeStore::markSource);
        return new RunResult(candidatesFound, activeOpportunityKeys.size(), newCompanies,
                matchedCompanyIds.size(), newOfferings,
                updatedOfferings, duplicates, possible, rejected, errors.size(),
                results.stream().filter(value -> "SEC_REG_CF".equals(value.source()))
                        .mapToInt(value -> value.candidates().size()).sum(),
                secClassified, reconciled, queueBefore, diagnostics, List.copyOf(errors));
    }

    private void saveCampaignEvidence(long offeringId, NativeOfferingCandidate candidate) {
        if (blank(candidate.canonicalUrl()) || !P0_PLATFORMS.contains(candidate.platform())) return;
        try {
            com.startupvalidationbot.diligence.platform.PlatformUrlPolicy.require(
                    candidate.platform().toUpperCase(Locale.ROOT), candidate.canonicalUrl());
        } catch (IllegalArgumentException ignored) { return; }
        Map<String, String> facts = new LinkedHashMap<>(candidate.sourceEvidence());
        put(facts, "securityType", candidate.securityType());
        put(facts, "amountRaised", candidate.amountRaised());
        put(facts, "targetAmount", candidate.targetAmount());
        put(facts, "maximumAmount", candidate.maximumAmount());
        put(facts, "valuationOrCap", candidate.valuationOrCap());
        put(facts, "minimumInvestment", candidate.minimumInvestment());
        put(facts, "deadline", candidate.deadline());
        String fingerprint = ContentHash.sha256(candidate.platform() + "|" + candidate.canonicalUrl()
                + "|" + candidate.status() + "|" + facts);
        LocalDateTime now = candidate.retrievedAt();
        diligenceStore.upsertCampaign(new PlatformCampaign(null, offeringId,
                candidate.platform().toUpperCase(Locale.ROOT), candidate.canonicalUrl(),
                candidate.source().startsWith("SEC_") ? "SEC_OFFERING_URL" : "PUBLIC_LIVE_DIRECTORY",
                candidate.source().startsWith("SEC_") ? 90 : 95, campaignStatus(candidate.status()),
                candidate.companyName(), candidate.securityType(), candidate.minimumInvestment(),
                candidate.pricePerShare(), null, money(candidate.valuationOrCap()), null,
                candidate.targetAmount(), candidate.maximumAmount(), candidate.amountRaised(),
                parseInteger(candidate.sourceEvidence().get("investorCount")), candidate.deadline(),
                candidate.description(), Map.copyOf(facts), fingerprint, now, now));
    }

    private static List<NativeOfferingCandidate> collapseSecLifecycle(List<NativeOfferingCandidate> values) {
        Map<String, NativeOfferingCandidate> latest = new LinkedHashMap<>();
        for (NativeOfferingCandidate value : values) {
            String key = !blank(value.secFileNumber()) ? safe(value.issuerCik()) + "|" + value.secFileNumber()
                    : !blank(value.issuerCik()) ? value.issuerCik() + "|" + CompanyIdentity.normalizeName(value.companyName())
                    : value.secAccessionNumber();
            NativeOfferingCandidate previous = latest.get(key);
            if (previous == null || after(value, previous)) latest.put(key, value);
        }
        return List.copyOf(latest.values());
    }

    private static boolean after(NativeOfferingCandidate left, NativeOfferingCandidate right) {
        if (left.filingDate() == null) return false;
        if (right.filingDate() == null) return true;
        int order = left.filingDate().compareTo(right.filingDate());
        return order > 0 || order == 0 && safe(left.secAccessionNumber()).compareTo(safe(right.secAccessionNumber())) > 0;
    }

    private static Reconciled reconcile(NativeOfferingCandidate candidate,
            List<NativeOfferingCandidate> secCandidates) {
        if (candidate.source().startsWith("SEC_")) {
            return new Reconciled(candidate, ReconciliationStatus.SEC_RECONCILED, false);
        }
        List<NativeOfferingCandidate> matches = secCandidates.stream()
                .filter(sec -> sameIdentity(candidate, sec)).toList();
        if (matches.size() > 1) return new Reconciled(candidate, ReconciliationStatus.NEEDS_REVIEW, true);
        if (matches.isEmpty()) return new Reconciled(candidate, ReconciliationStatus.PLATFORM_CONFIRMED, false);
        return new Reconciled(merge(candidate, matches.getFirst()), ReconciliationStatus.SEC_RECONCILED, false);
    }

    private static boolean sameIdentity(NativeOfferingCandidate platform, NativeOfferingCandidate sec) {
        if (!blank(platform.issuerCik()) && platform.issuerCik().equals(sec.issuerCik())) return true;
        if (!blank(platform.secAccessionNumber())
                && platform.secAccessionNumber().equals(sec.secAccessionNumber())) return true;
        if (!blank(platform.secFileNumber()) && platform.secFileNumber().equals(sec.secFileNumber())) return true;
        if (!blank(platform.canonicalUrl()) && platform.canonicalUrl().equals(sec.canonicalUrl())) return true;
        if (!blank(platform.issuerDomain()) && platform.issuerDomain().equals(sec.issuerDomain())) return true;
        return CompanyIdentity.normalizeName(platform.companyName()).equals(CompanyIdentity.normalizeName(sec.companyName()))
                && PlatformNormalizer.normalize(platform.platform()).equals(PlatformNormalizer.normalize(sec.platform()));
    }

    private static NativeOfferingCandidate merge(NativeOfferingCandidate platform, NativeOfferingCandidate sec) {
        Status status = List.of(Status.WITHDRAWN, Status.TERMINATED, Status.CLOSED).contains(sec.status())
                ? sec.status() : platform.status();
        Map<String, String> evidence = new LinkedHashMap<>(sec.sourceEvidence());
        evidence.putAll(platform.sourceEvidence());
        evidence.put("secReconciliation", sec.secAccessionNumber());
        return new NativeOfferingCandidate(platform.source(), platform.platform(), platform.externalId(),
                platform.companyName(), platform.canonicalUrl(), first(sec.issuerWebsite(), platform.issuerWebsite()),
                first(sec.issuerDomain(), platform.issuerDomain()), status, "REG_CF",
                first(sec.intermediaryName(), platform.intermediaryName()),
                first(platform.securityType(), sec.securityType()), first(platform.amountRaised(), sec.amountRaised()),
                first(platform.targetAmount(), sec.targetAmount()), first(platform.maximumAmount(), sec.maximumAmount()),
                first(platform.valuationOrCap(), sec.valuationOrCap()),
                first(platform.minimumInvestment(), sec.minimumInvestment()), platform.pricePerShare(),
                first(platform.deadline(), sec.deadline()), platform.category(), platform.description(), sec.issuerCik(),
                sec.secAccessionNumber(), sec.secFileNumber(), sec.secFilingUrl(), sec.filingType(), sec.filingDate(),
                Map.copyOf(evidence), platform.retrievedAt());
    }

    private static com.startupvalidationbot.radar.RadarDomain.Candidate radarCandidate(
            NativeOfferingCandidate value) {
        String sourceKey = "native-offering-" + sourceKey(value).toLowerCase(Locale.ROOT).replace('_', '-');
        String website = safeWebsite(value.issuerWebsite());
        String sourceUrl = sourceUrl(value);
        String raw = OfferingHtml.bounded(value.sourceEvidence().toString(), 4000);
        return new com.startupvalidationbot.radar.RadarDomain.Candidate(sourceKey,
                value.externalId(), value.companyName(), website, value.description(),
                blank(value.category()) ? "Unknown" : value.category(),
                blank(value.category()) ? List.of("Reg CF") : List.of(value.category(), "Reg CF"),
                null, null, sourceUrl, value.filingDate() == null ? value.retrievedAt()
                        : value.filingDate().atTime(LocalTime.NOON), raw,
                EvidenceClassification.PUBLIC_OFFICIAL);
    }

    private static Candidate secCandidate(NativeOfferingCandidate value) {
        return new Candidate(value.companyName(), value.issuerCik(), value.issuerWebsite(), value.platform(),
                value.intermediaryName(), null, value.canonicalUrl(), value.secFilingUrl(),
                value.secAccessionNumber(), value.secFileNumber(), value.filingType(), value.filingDate(),
                value.securityType(), value.minimumInvestment(), value.targetAmount(), value.maximumAmount(),
                value.valuationOrCap(), value.deadline(), value.amountRaised(), value.source(), value.sourceEvidence());
    }

    private static String sourceName(NativeOfferingCandidate value) {
        return value.source().startsWith("SEC_") ? "SEC Regulation Crowdfunding filings"
                : value.platform() + " live offerings";
    }

    private static String sourceUrl(NativeOfferingCandidate value) {
        if (!blank(value.canonicalUrl())) return value.canonicalUrl();
        return value.secFilingUrl();
    }

    private static String safeWebsite(String value) {
        if (blank(value)) return null;
        try {
            URI uri = PublicSourceUrlPolicy.requirePublicHttpUrl(value);
            String host = safe(uri.getHost()).toLowerCase(Locale.ROOT);
            if (host.endsWith("republic.com") || host.endsWith("startengine.com")
                    || host.endsWith("wefunder.com") || host.endsWith("sec.gov")) return null;
            return uri.toString();
        } catch (SourceFetchException error) { return null; }
    }

    private static CampaignStatus campaignStatus(Status status) {
        return switch (status) {
            case ACTIVE, CLOSING_SOON -> CampaignStatus.ACTIVE;
            case RESERVATION -> CampaignStatus.RESERVATION;
            case CLOSED, WITHDRAWN, TERMINATED -> CampaignStatus.CLOSED;
            case UNKNOWN -> CampaignStatus.UNKNOWN;
        };
    }

    private static java.math.BigDecimal money(String value) {
        if (blank(value)) return null;
        return OfferingHtml.money(value.replaceAll("(?i)\\s+valuation cap$", ""));
    }

    private static Integer parseInteger(String value) { return OfferingHtml.integer(value); }
    private static String sourceKey(NativeOfferingCandidate value) {
        return value.source().startsWith("SEC_") ? "SEC_REG_CF" : value.platform().toUpperCase(Locale.ROOT);
    }

    private static String opportunityKey(NativeOfferingCandidate value) {
        if (!blank(value.secAccessionNumber())) return "SEC|" + value.secAccessionNumber();
        if (!blank(value.issuerCik()) && !blank(value.secFileNumber())) {
            return "SEC_FILE|" + value.issuerCik() + "|" + value.secFileNumber();
        }
        if (!blank(value.canonicalUrl())) return "URL|" + value.canonicalUrl();
        return sourceKey(value) + "|" + value.externalId();
    }

    private static <T> T first(T first, T second) { return first == null ? second : first; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }
    private static void put(Map<String, String> values, String key, Object value) {
        if (value != null && !value.toString().isBlank()) values.put(key, value.toString());
    }

    private record Reconciled(NativeOfferingCandidate candidate, ReconciliationStatus status, boolean ambiguous) { }

    private static final class MutableSource {
        private final String source;
        private final String capability;
        private final String status;
        private final boolean directoryFetched;
        private final int requests;
        private final int details;
        private final int candidates;
        private final String error;
        private int active;
        private int inserted;
        private int matched;
        private int rejected;

        private MutableSource(SourceResult value) {
            source = value.source(); capability = value.capability(); status = value.status();
            directoryFetched = value.directoryFetched(); requests = value.requests();
            details = value.detailRequests(); candidates = value.candidates().size();
            error = value.errors().isEmpty() ? null : String.join("; ", value.errors());
        }

        private MutableSource(String source, String capability, String status, int candidates) {
            this.source = source; this.capability = capability; this.status = status;
            this.directoryFetched = false; this.requests = 0; this.details = 0;
            this.candidates = candidates; this.error = null; this.active = candidates;
        }

        private static MutableSource synthetic(NativeOfferingCandidate value) {
            return new MutableSource(sourceKey(value), "NORMALIZED_NATIVE_INPUT", "OK", 0);
        }

        private static MutableSource wefunder(int secDerived) {
            return new MutableSource("WEFUNDER", "SEC_INTERMEDIARY_AND_KNOWN_URLS",
                    secDerived > 0 ? "AVAILABLE_VIA_SEC" : "UNAVAILABLE", secDerived);
        }

        private SourceDiagnostic diagnostic() {
            return new SourceDiagnostic(source, capability, status, LocalDateTime.now(), directoryFetched,
                    requests, details, candidates, active, inserted, matched, rejected, error);
        }
    }
}
