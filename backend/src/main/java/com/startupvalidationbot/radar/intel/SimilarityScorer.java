package com.startupvalidationbot.radar.intel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic "similar startups" ranking.
 *
 * Similarity is computed from structured data the Radar already holds - shared categories, shared
 * trends, sector and business model - so the feature costs nothing per view and can explain itself.
 * AI may later add prose to a specific comparison, but it never produces the list.
 */
public final class SimilarityScorer {

    private static final int CATEGORY_WEIGHT = 55;
    private static final int TREND_POINTS = 14;
    private static final int TREND_CAP = 28;
    private static final int SECTOR_POINTS = 12;
    private static final int BUSINESS_MODEL_POINTS = 10;
    private static final int MIN_SCORE = 12;

    private SimilarityScorer() {
    }

    /** Structured facets of one company, as held in the database. */
    public record CompanyFacets(long id, String name, String sector, List<String> categories,
            List<String> trendKeys, String businessModel) {
        public CompanyFacets {
            categories = normaliseAll(categories);
            trendKeys = normaliseAll(trendKeys);
            sector = normalise(sector);
            businessModel = normalise(businessModel);
        }
    }

    public record SimilarCompany(long companyId, String name, int score, String relationship, List<String> reasons) {
    }

    public static List<SimilarCompany> rank(CompanyFacets target, List<CompanyFacets> candidates, int limit) {
        if (target == null || candidates == null || candidates.isEmpty()) return List.of();

        List<SimilarCompany> ranked = new ArrayList<>();
        for (CompanyFacets candidate : candidates) {
            if (candidate == null || candidate.id() == target.id()) continue;

            List<String> reasons = new ArrayList<>();
            Set<String> sharedCategories = intersection(target.categories(), candidate.categories());
            Set<String> sharedTrends = intersection(target.trendKeys(), candidate.trendKeys());

            double categoryOverlap = jaccard(target.categories(), candidate.categories());
            int score = (int) Math.round(categoryOverlap * CATEGORY_WEIGHT);
            if (!sharedCategories.isEmpty()) {
                reasons.add("Shares categories: " + String.join(", ", sharedCategories) + ".");
            }

            if (!sharedTrends.isEmpty()) {
                score += Math.min(TREND_CAP, sharedTrends.size() * TREND_POINTS);
                reasons.add("Appears in the same emerging trend" + (sharedTrends.size() > 1 ? "s" : "")
                        + ": " + String.join(", ", sharedTrends) + ".");
            }

            boolean sameSector = !target.sector().isEmpty() && !"unknown".equals(target.sector())
                    && target.sector().equals(candidate.sector());
            if (sameSector) {
                score += SECTOR_POINTS;
                reasons.add("Same source-classified sector.");
            }

            boolean sameModel = !target.businessModel().isEmpty() && !"unknown".equals(target.businessModel())
                    && target.businessModel().equals(candidate.businessModel());
            if (sameModel) {
                score += BUSINESS_MODEL_POINTS;
                reasons.add("Similar business model.");
            }

            if (score < MIN_SCORE || reasons.isEmpty()) continue;
            score = Math.min(100, score);
            ranked.add(new SimilarCompany(candidate.id(), candidate.name(), score,
                    relationship(categoryOverlap, sharedCategories.size(), !sharedTrends.isEmpty(), sameSector),
                    List.copyOf(reasons)));
        }

        ranked.sort(Comparator.comparingInt(SimilarCompany::score).reversed()
                .thenComparing(SimilarCompany::name, Comparator.nullsLast(String::compareTo)));
        return ranked.size() <= limit ? List.copyOf(ranked) : List.copyOf(ranked.subList(0, Math.max(0, limit)));
    }

    /**
     * A conservative label. "Likely competitor" is only claimed on heavy category overlap, because
     * calling two companies competitors on thin evidence is worse than saying nothing useful.
     */
    static String relationship(double categoryOverlap, int sharedCategories, boolean sharedTrend,
            boolean sameSector) {
        if (categoryOverlap >= 0.6 && sharedCategories >= 2) return "Likely competitor";
        if (categoryOverlap >= 0.34 || (sharedCategories >= 2 && sameSector)) return "Adjacent company";
        if (sharedTrend) return "Same emerging trend";
        return "Shares categories";
    }

    private static Set<String> intersection(List<String> left, List<String> right) {
        Set<String> shared = new LinkedHashSet<>(left);
        shared.retainAll(new LinkedHashSet<>(right));
        return shared;
    }

    private static double jaccard(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0d;
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> shared = intersection(left, right);
        return union.isEmpty() ? 0d : (double) shared.size() / union.size();
    }

    private static List<String> normaliseAll(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(SimilarityScorer::normalise).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
