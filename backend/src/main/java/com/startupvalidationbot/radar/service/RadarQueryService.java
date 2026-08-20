package com.startupvalidationbot.radar.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.CompanyDetail;
import com.startupvalidationbot.radar.RadarStore;

@Service
public class RadarQueryService {
    private final RadarStore store;

    public RadarQueryService(RadarStore store) {
        this.store = store;
    }

    public List<Company> list(String search, String sector, Integer minRadar, Integer minPersonal,
            Boolean watched, Boolean includeIgnored, String sort) {
        String query = value(search).toLowerCase(Locale.ROOT);
        String sectorFilter = value(sector).toLowerCase(Locale.ROOT);
        Comparator<Company> comparator = switch (value(sort)) {
            case "personal" -> Comparator.comparingInt(Company::personalScore).reversed();
            case "newest" -> Comparator.comparing(Company::firstSeenAt).reversed();
            case "updated" -> Comparator.comparing(Company::lastSeenAt).reversed();
            default -> Comparator.comparingInt(Company::radarScore).reversed();
        };
        return store.listCompanies().stream()
                .filter(company -> Boolean.TRUE.equals(includeIgnored) || !company.ignored())
                .filter(company -> watched == null || company.watched() == watched)
                .filter(company -> minRadar == null || company.radarScore() >= minRadar)
                .filter(company -> minPersonal == null || company.personalScore() >= minPersonal)
                .filter(company -> sectorFilter.isBlank()
                        || value(company.sector()).toLowerCase(Locale.ROOT).contains(sectorFilter)
                        || company.categories().stream().anyMatch(category -> category.toLowerCase(Locale.ROOT)
                                .contains(sectorFilter)))
                .filter(company -> query.isBlank()
                        || company.name().toLowerCase(Locale.ROOT).contains(query)
                        || value(company.description()).toLowerCase(Locale.ROOT).contains(query)
                        || value(company.domain()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(comparator)
                .toList();
    }

    public CompanyDetail detail(long companyId) {
        return store.getCompanyDetail(companyId);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
