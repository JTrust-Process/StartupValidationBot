package com.startupvalidationbot.radar.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.RadarIntelViews.SimilarCompanyView;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.intel.SimilarityScorer;
import com.startupvalidationbot.radar.intel.SimilarityScorer.CompanyFacets;

/**
 * Similar startups, computed from structured data the Radar already holds.
 *
 * Deterministic first: category overlap, shared trends, sector and business model. This costs no AI
 * calls, so opening a company profile never spends model budget.
 */
@Service
public class RadarSimilarityService {
    private static final int DEFAULT_LIMIT = 6;

    private final RadarStore store;
    private final RadarIntelStore intelStore;

    public RadarSimilarityService(RadarStore store, RadarIntelStore intelStore) {
        this.store = store;
        this.intelStore = intelStore;
    }

    public List<SimilarCompanyView> similarTo(long companyId, int limit) {
        Company target = store.findCompany(companyId).orElse(null);
        if (target == null) return List.of();

        Map<Long, List<String>> trendKeys = intelStore.trendKeysByCompany();
        List<Company> candidates = store.listCompanies().stream()
                .filter(company -> !company.ignored())
                .filter(company -> company.id() != null && !company.id().equals(companyId))
                .toList();

        List<CompanyFacets> facets = candidates.stream()
                .map(company -> facetsFor(company, trendKeys))
                .toList();

        var ranked = SimilarityScorer.rank(facetsFor(target, trendKeys), facets,
                limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 25));

        Map<Long, Company> byId = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(Company::id, company -> company, (a, b) -> a));

        return ranked.stream()
                .map(similar -> {
                    Company company = byId.get(similar.companyId());
                    return new SimilarCompanyView(similar.companyId(), similar.name(), similar.score(),
                            similar.relationship(), similar.reasons(),
                            company == null ? List.of() : company.categories(),
                            company == null ? 0 : company.radarScore(),
                            company == null ? 0 : company.personalScore());
                })
                .toList();
    }

    private static CompanyFacets facetsFor(Company company, Map<Long, List<String>> trendKeys) {
        return new CompanyFacets(company.id() == null ? 0L : company.id(), company.name(), company.sector(),
                company.categories(), trendKeys.getOrDefault(company.id(), List.of()),
                businessModelOf(company));
    }

    /**
     * A coarse, deterministic business-model tag derived from source text. Deliberately conservative:
     * an unrecognised model contributes nothing to similarity rather than guessing.
     */
    static String businessModelOf(Company company) {
        String corpus = (company.name() + " " + nullToEmpty(company.description()) + " "
                + String.join(" ", company.categories())).toLowerCase(java.util.Locale.ROOT);
        if (corpus.contains("marketplace") || corpus.contains("two-sided")) return "marketplace";
        if (corpus.contains("open source") || corpus.contains("open-source")) return "open-source";
        if (corpus.contains("api") || corpus.contains("developer platform")) return "api-platform";
        if (corpus.contains("hardware") || corpus.contains("device")) return "hardware";
        if (corpus.contains("agency") || corpus.contains("services")) return "services";
        if (corpus.contains("saas") || corpus.contains("subscription")) return "saas";
        return "unknown";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
