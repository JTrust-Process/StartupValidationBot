package com.startupvalidationbot.diligence.discovery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.DiscoveryAttempt;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityDecision;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityStatus;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.PlatformRun;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.RunResult;
import com.startupvalidationbot.diligence.platform.PlatformHttpClient;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.radar.CompanyIdentity;
import com.startupvalidationbot.radar.ContentHash;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.SafeUrl;

@Service
public class CampaignDiscoveryService {
    private static final List<String> PLATFORMS = List.of("WEFUNDER", "REPUBLIC", "STARTENGINE");

    private final CampaignDiscoveryStore store;
    private final OfferingStore offerings;
    private final RadarStore radar;
    private final DiligenceStore diligence;
    private final CampaignIdentityVerifier verifier;
    private final Map<String, PlatformCampaignDiscovery> discoverers;
    private final PlatformHttpClient http;
    private final int maxCompanies;
    private final int maxCandidates;
    private final int maxRequestsPerPlatform;
    private final int successCacheHours;
    private final int negativeCacheHours;
    private final int errorCacheMinutes;

    public CampaignDiscoveryService(CampaignDiscoveryStore store, OfferingStore offerings, RadarStore radar,
            DiligenceStore diligence, CampaignIdentityVerifier verifier,
            List<PlatformCampaignDiscovery> discoverers, PlatformHttpClient http,
            @Value("${campaign-discovery.max-companies-per-run:25}") int maxCompanies,
            @Value("${campaign-discovery.max-candidates-per-platform:5}") int maxCandidates,
            @Value("${campaign-discovery.max-requests-per-platform-per-run:8}") int maxRequestsPerPlatform,
            @Value("${campaign-discovery.success-cache-hours:24}") int successCacheHours,
            @Value("${campaign-discovery.negative-cache-hours:72}") int negativeCacheHours,
            @Value("${campaign-discovery.error-cache-minutes:120}") int errorCacheMinutes) {
        this.store = store;
        this.offerings = offerings;
        this.radar = radar;
        this.diligence = diligence;
        this.verifier = verifier;
        Map<String, PlatformCampaignDiscovery> values = new LinkedHashMap<>();
        discoverers.forEach(value -> values.put(value.platform(), value));
        this.discoverers = Map.copyOf(values);
        this.http = http;
        this.maxCompanies = clamp(maxCompanies, 1, 100);
        this.maxCandidates = clamp(maxCandidates, 1, 10);
        this.maxRequestsPerPlatform = clamp(maxRequestsPerPlatform, 1, 25);
        this.successCacheHours = clamp(successCacheHours, 1, 168);
        this.negativeCacheHours = clamp(negativeCacheHours, 1, 720);
        this.errorCacheMinutes = clamp(errorCacheMinutes, 15, 1440);
    }

    public RunResult discover() {
        LocalDateTime now = LocalDateTime.now();
        List<Target> targets = targets();
        CampaignDiscoveryContext context = new CampaignDiscoveryContext(http, maxRequestsPerPlatform);
        Set<Long> searchedCompanies = new HashSet<>();
        Map<String, MutablePlatformRun> runs = new LinkedHashMap<>();
        PLATFORMS.forEach(platform -> runs.put(platform,
                new MutablePlatformRun(platform, capability(platform))));
        int candidatesFound = 0;
        int confirmed = 0;
        int possible = 0;
        int rejected = 0;
        int cacheHits = 0;
        int errors = 0;

        for (Target target : targets) {
            CampaignIdentity identity = identity(target);
            for (String platform : target.platforms()) {
                MutablePlatformRun run = runs.get(platform);
                if (run == null) continue;
                if (run.budgetExhausted || run.unavailable) continue;
                if (store.cached(identity.companyId(), platform, identity.fingerprint(), now)) {
                    cacheHits++;
                    run.cacheHits++;
                    continue;
                }
                PlatformCampaignDiscovery discoverer = discoverers.get(platform);
                if (discoverer == null) continue;
                searchedCompanies.add(identity.companyId());
                run.searches++;
                int requestsBefore = context.requests(platform);
                DiscoveryAttempt attempt = discoverer.discover(identity, maxCandidates, context);
                int requests = context.requests(platform) - requestsBefore;
                run.requests += requests;
                if (attempt.error() != null) {
                    errors++;
                    run.errors++;
                    run.error = safe(attempt.error());
                    boolean budgetExhausted = attempt.error().toLowerCase(Locale.ROOT).contains("budget exhausted");
                    run.budgetExhausted = budgetExhausted;
                    String status = budgetExhausted ? "NOT_CHECKED"
                            : unavailable(attempt.error()) ? "UNAVAILABLE" : "DEGRADED";
                    run.status = budgetExhausted ? "DEGRADED" : status;
                    run.unavailable = status.equals("UNAVAILABLE");
                    saveCheck(identity, platform, status, requests, 0,
                            budgetExhausted ? "Public campaign discovery was deferred by the per-run request budget."
                                    : "Public campaign discovery could not be completed.", run.error,
                            now.plusMinutes(errorCacheMinutes));
                    diligence.availability(identity.companyId(), platform, status,
                            budgetExhausted ? "The bounded public check was deferred until a later run."
                                    : "Public campaign discovery could not be completed.",
                            status.equals("UNAVAILABLE")
                                    ? "This platform did not permit the public request; add a canonical URL manually if known."
                                    : "Retry after the platform recovers or add a canonical URL manually.");
                    continue;
                }

                List<Decision> decisions = new ArrayList<>();
                for (CampaignCandidate candidate : attempt.candidates()) {
                    IdentityDecision decision = verifier.verify(identity, candidate);
                    decisions.add(new Decision(candidate, decision));
                    store.saveCandidate(identity.companyId(), identity.offeringId(), candidate, decision);
                    candidatesFound++;
                    run.candidates++;
                    if (decision.status() == IdentityStatus.REJECTED) rejected++;
                }
                List<Decision> plausible = decisions.stream()
                        .filter(value -> value.decision().status() != IdentityStatus.REJECTED).toList();
                if (plausible.size() == 1) {
                    Decision match = plausible.getFirst();
                    if (match.decision().status() == IdentityStatus.CONFIRMED) {
                        confirmed++;
                        run.resolved++;
                        run.status = "OK";
                        if (identity.offeringId() != null) {
                            offerings.attachCampaignUrl(identity.offeringId(), platform, match.candidate().campaignUrl());
                        }
                        saveCheck(identity, platform, "FOUND", requests, decisions.size(),
                                "One corroborated public campaign was found.", null, now.plusHours(successCacheHours));
                        diligence.availability(identity.companyId(), platform, "FOUND",
                                "A corroborated public campaign URL was discovered and saved.", null);
                    } else {
                        possible++;
                        saveCheck(identity, platform, "POSSIBLE", requests, decisions.size(),
                                "One possible campaign needs identity review.", null, now.plusHours(negativeCacheHours));
                        diligence.availability(identity.companyId(), platform, "POSSIBLE",
                                "A possible campaign was found, but independent identity evidence is insufficient.",
                                "Does the campaign issuer domain or SEC intermediary confirm this company identity?");
                    }
                } else if (plausible.size() > 1) {
                    possible += plausible.size();
                    for (Decision value : plausible) {
                        IdentityDecision ambiguous = new IdentityDecision(IdentityStatus.AMBIGUOUS,
                                Math.min(value.decision().confidence(), 60),
                                "Multiple plausible public campaigns require manual identity review.");
                        store.saveCandidate(identity.companyId(), identity.offeringId(), value.candidate(), ambiguous);
                    }
                    saveCheck(identity, platform, "AMBIGUOUS", requests, decisions.size(),
                            "Multiple plausible campaigns require manual review.", null,
                            now.plusHours(negativeCacheHours));
                    diligence.availability(identity.companyId(), platform, "AMBIGUOUS",
                            "Multiple plausible campaign pages were found; none was attached automatically.",
                            "Which campaign URL belongs to the tracked issuer?");
                } else {
                    saveCheck(identity, platform, "NONE_FOUND", requests, decisions.size(),
                            decisions.isEmpty() ? "No matching public campaign was found."
                                    : "Candidate pages were rejected by identity checks.",
                            null, now.plusHours(negativeCacheHours));
                    diligence.availability(identity.companyId(), platform, "NONE_FOUND",
                            "The bounded public check found no campaign with sufficient identity evidence.",
                            "Does this company have a public campaign under another legal issuer name?");
                }
            }
        }

        List<PlatformRun> platformRuns = new ArrayList<>();
        for (MutablePlatformRun value : runs.values()) {
            if (value.searches == 0 && value.cacheHits == 0) value.status = "NO_TARGETS";
            else if (value.searches == 0 && value.cacheHits > 0) value.status = "CACHED";
            else if (value.errors > 0 && value.resolved > 0) value.status = "DEGRADED";
            else if (value.errors == 0 && value.status.equals("NEVER_CHECKED")) value.status = "OK";
            PlatformRun run = value.freeze();
            store.markPlatform(run);
            platformRuns.add(run);
        }
        return new RunResult(targets.size(), searchedCompanies.size(), runs.get("WEFUNDER").searches,
                runs.get("REPUBLIC").searches, runs.get("STARTENGINE").searches, candidatesFound,
                confirmed, possible, rejected, cacheHits, errors, List.copyOf(platformRuns));
    }

    private List<Target> targets() {
        List<Company> companies = radar.listCompanies();
        Map<Long, Company> byId = new HashMap<>();
        companies.forEach(company -> byId.put(company.id(), company));
        List<Offering> allOfferings = offerings.list(null, null, null, null);
        Set<Long> companiesWithOfferings = new HashSet<>();
        allOfferings.stream().filter(value -> value.radarCompanyId() != null)
                .forEach(value -> companiesWithOfferings.add(value.radarCompanyId()));
        LinkedHashMap<String, Target> targets = new LinkedHashMap<>();

        for (Offering offering : allOfferings) {
            if (offering.radarCompanyId() == null || !blank(offering.offeringUrl())) continue;
            Company company = byId.get(offering.radarCompanyId());
            if (company == null || company.ignored() || !eligible(offering, company)) continue;
            String platform = platform(offering.platform(), offering.intermediaryName());
            List<String> platforms = PLATFORMS.contains(platform) ? List.of(platform) : PLATFORMS;
            String key = company.id().toString();
            targets.putIfAbsent(key, new Target(company, offering, platforms));
            if (uniqueCompanies(targets) >= maxCompanies) break;
        }

        if (uniqueCompanies(targets) < maxCompanies) {
            for (Company company : companies) {
                if (company.ignored() || companiesWithOfferings.contains(company.id())) continue;
                if (!company.watched() && (company.radarScore() < 70 || company.personalScore() < 50)
                        && !recentOfferingSignal(company)) continue;
                targets.putIfAbsent(company.id().toString(), new Target(company, null, PLATFORMS));
                if (uniqueCompanies(targets) >= maxCompanies) break;
            }
        }
        return List.copyOf(targets.values());
    }

    private CampaignIdentity identity(Target target) {
        Company company = target.company();
        Offering offering = target.offering();
        Map<String, String> facts = offering == null ? Map.of() : offerings.facts(offering.id());
        String issuerWebsite = first(facts, "issuerWebsite", "ISSUERWEBSITE", "website");
        String knownPlatform = offering == null ? "UNKNOWN"
                : platform(offering.platform(), offering.intermediaryName());
        String fingerprint = ContentHash.sha256(String.join("|",
                safePart(company.name()), safePart(company.domain()), safePart(company.websiteUrl()),
                String.join(",", company.aliases()), safePart(offering == null ? null : offering.issuerName()),
                safePart(issuerWebsite), safePart(knownPlatform)));
        return new CampaignIdentity(company.id(), offering == null ? null : offering.id(), company.name(),
                company.websiteUrl(), CompanyIdentity.normalizeDomain(company.domain()), company.aliases(),
                offering == null ? company.name() : offering.issuerName(),
                offering == null ? null : offering.issuerCik(), issuerWebsite,
                offering == null ? null : offering.intermediaryName(),
                offering == null ? null : offering.secFilingUrl(), knownPlatform,
                PLATFORMS.contains(knownPlatform), fingerprint);
    }

    private void saveCheck(CampaignIdentity identity, String platform, String status, int requests,
            int candidates, String summary, String error, LocalDateTime nextEligibleAt) {
        store.saveCheck(identity.companyId(), identity.offeringId(), platform, identity.fingerprint(), status,
                requests, candidates, summary, error, nextEligibleAt);
    }

    private String capability(String platform) {
        PlatformCampaignDiscovery discoverer = discoverers.get(platform);
        return discoverer == null ? "UNAVAILABLE" : discoverer.capability();
    }

    private static boolean eligible(Offering offering, Company company) {
        return company.watched() || offering.matchStatus() == MatchStatus.CONFIRMED
                || offering.matchStatus() == MatchStatus.LIKELY && offering.matchConfidence() >= 75
                || offering.matchStatus() == MatchStatus.AMBIGUOUS;
    }

    private static boolean recentOfferingSignal(Company company) {
        if (company.lastSeenAt() == null || company.lastSeenAt().isBefore(LocalDateTime.now().minusDays(14))) {
            return false;
        }
        String value = (safePart(company.description()) + " " + String.join(" ", company.categories()))
                .toLowerCase(Locale.ROOT);
        return List.of("reg cf", "crowdfunding", "wefunder", "republic", "startengine", "raising")
                .stream().anyMatch(value::contains);
    }

    private static int uniqueCompanies(Map<String, Target> targets) {
        return (int) targets.values().stream().map(value -> value.company().id()).distinct().count();
    }

    private static String platform(String platform, String intermediary) {
        String value = (safePart(platform) + " " + safePart(intermediary)).toUpperCase(Locale.ROOT);
        if (value.contains("WEFUNDER")) return "WEFUNDER";
        if (value.contains("REPUBLIC") || value.contains("OPENDEAL")) return "REPUBLIC";
        if (value.contains("STARTENGINE")) return "STARTENGINE";
        return "UNKNOWN";
    }

    private static boolean unavailable(String error) {
        String value = error.toLowerCase(Locale.ROOT);
        return value.contains("http 401") || value.contains("http 403") || value.contains("redirect rejected");
    }

    private static String first(Map<String, String> values, String... keys) {
        for (String key : keys) if (!blank(values.get(key))) return values.get(key);
        return null;
    }

    private static String safe(String value) {
        return SafeUrl.redactUrlsIn(value == null ? "Campaign discovery failed" : value);
    }

    private static String safePart(String value) { return value == null ? "" : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(value, max)); }

    private record Target(Company company, Offering offering, List<String> platforms) { }
    private record Decision(CampaignCandidate candidate, IdentityDecision decision) { }

    private static final class MutablePlatformRun {
        private final String platform;
        private final String capability;
        private int searches;
        private int requests;
        private int candidates;
        private int resolved;
        private int cacheHits;
        private int errors;
        private boolean budgetExhausted;
        private boolean unavailable;
        private String status = "NEVER_CHECKED";
        private String error;

        private MutablePlatformRun(String platform, String capability) {
            this.platform = platform;
            this.capability = capability;
        }

        private PlatformRun freeze() {
            return new PlatformRun(platform, capability, status, requests, candidates, resolved, error);
        }
    }
}
