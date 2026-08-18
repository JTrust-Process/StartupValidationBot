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
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.TrendDraft;

@Service
public class RadarTrendService {
    private final RadarStore store;

    public RadarTrendService(RadarStore store) {
        this.store = store;
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
        return trends.size();
    }

    public List<Trend> list() {
        return store.listTrends();
    }
}
