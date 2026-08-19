package com.startupvalidationbot.radar.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.Trend;
import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.RadarIntelStore.TrendMetrics;
import com.startupvalidationbot.radar.RadarIntelViews.TrendView;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.TrendDraft;
import com.startupvalidationbot.radar.intel.TrendVelocity;

@Service
public class RadarTrendService {
    /** Each velocity window. Two equal windows keep the comparison honest. */
    private static final int WINDOW_DAYS = 30;

    private final RadarStore store;
    private final RadarIntelStore intelStore;

    public RadarTrendService(RadarStore store, RadarIntelStore intelStore) {
        this.store = store;
        this.intelStore = intelStore;
    }

    public int rebuild() {
        LocalDateTime periodEnd = LocalDateTime.now();
        LocalDateTime periodStart = periodEnd.minusDays(30);
        Map<String, List<Company>> groups = new LinkedHashMap<>();
        for (Company company : store.listCompanies()) {
            if (company.lastSeenAt().isBefore(periodStart) || company.ignored()) {
                continue;
            }
            List<String> categories = company.categories().isEmpty() ? List.of(company.sector()) : company.categories();
            for (String category : categories) {
                if (category == null || category.isBlank() || "Unknown".equalsIgnoreCase(category)) {
                    continue;
                }
                String key = category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(company);
            }
        }
        List<TrendDraft> trends = groups.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(entry -> {
                    List<Company> companies = entry.getValue().stream().distinct().toList();
                    int momentum = Math.min(100, companies.size() * 12
                            + (int) companies.stream().mapToInt(Company::radarScore).average().orElse(0) / 2);
                    String name = companies.stream().flatMap(company -> company.categories().stream())
                            .filter(category -> category.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                                    .replaceAll("(^-|-$)", "").equals(entry.getKey()))
                            .findFirst().orElse(companies.get(0).sector());
                    return new TrendDraft(entry.getKey(), name,
                            companies.size() + " recently discovered companies are clustering in this theme.",
                            momentum, companies.stream().map(Company::id).toList());
                })
                .sorted(Comparator.comparingInt(TrendDraft::momentumScore).reversed())
                .limit(20)
                .toList();
        store.replaceTrends(trends, periodStart, periodEnd);
        enrichTrendMetrics(trends, periodStart);
        return trends.size();
    }

    /**
     * Grounds each trend in real discovery history.
     *
     * A percentage is only produced when the earlier window holds enough companies to justify one;
     * otherwise the trend reports absolute counts and says why. No number here is invented.
     */
    private void enrichTrendMetrics(List<TrendDraft> trends, LocalDateTime windowStart) {
        LocalDateTime priorStart = windowStart.minusDays(WINDOW_DAYS);
        boolean hasPriorWindow = intelStore.hasHistoryBefore(windowStart);

        for (TrendDraft trend : trends) {
            var counts = intelStore.trendWindowCounts(trend.companyIds(), windowStart, priorStart);
            var velocity = TrendVelocity.compute(counts.recent(), counts.prior(), hasPriorWindow, WINDOW_DAYS);
            var confidence = TrendVelocity.confidence(trend.companyIds().size(), velocity.sufficientHistory(),
                    counts.distinctSources());

            String whyItMatters = String.format(
                    "%d companies in your Radar cluster around %s. %s Confidence is %s because the theme is "
                            + "supported by %d compan%s across %d distinct source%s.",
                    trend.companyIds().size(), trend.name(), velocity.note(), confidence.name().toLowerCase(),
                    trend.companyIds().size(), trend.companyIds().size() == 1 ? "y" : "ies",
                    counts.distinctSources(), counts.distinctSources() == 1 ? "" : "s");

            intelStore.updateTrendMetrics(trend.key(), whyItMatters, confidence.name(), counts.recent(),
                    counts.prior(), velocity.direction().name(), velocity.note());
        }
    }

    public List<Trend> list() {
        return store.listTrends();
    }

    /** Trends joined with their grounded velocity and confidence metrics. */
    public List<TrendView> listDetailed() {
        Map<String, TrendMetrics> metrics = intelStore.trendMetrics();
        return store.listTrends().stream().map(trend -> {
            TrendMetrics detail = metrics.getOrDefault(trend.key(), TrendMetrics.empty());
            return new TrendView(trend.id(), trend.key(), trend.name(), trend.summary(),
                    detail.whyItMatters(), detail.confidence(), trend.companyCount(),
                    detail.recentDiscoveries(), detail.priorDiscoveries(), detail.velocityDirection(),
                    detail.velocityNote(), trend.momentumScore(), trend.companies());
        }).toList();
    }
}
