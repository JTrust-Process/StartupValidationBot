package com.startupvalidationbot.radar.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.RadarIntelViews.CompanyChangeView;
import com.startupvalidationbot.radar.RadarIntelViews.HomeCompanyCard;
import com.startupvalidationbot.radar.RadarIntelViews.HomeSection;
import com.startupvalidationbot.radar.RadarIntelViews.RadarHome;
import com.startupvalidationbot.radar.RadarIntelViews.TrendView;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;
import com.startupvalidationbot.radar.intel.InteractionSignal.Summary;
import com.startupvalidationbot.radar.intel.MomentumRanker;
import com.startupvalidationbot.radar.intel.PersonalRelevance;

/**
 * Radar Home: the daily "what should I know about today" view.
 *
 * Sections are filled in a fixed priority order and a company is only ever placed once, so the page
 * shows six genuinely different things rather than the same five companies six times. Everything is
 * computed from stored data - no AI call is made to render this page.
 */
@Service
public class RadarHomeService {
    private static final int NEW_TODAY_HOURS = 24;
    private static final int NEW_FALLBACK_DAYS = 7;
    private static final int FUNDING_WINDOW_DAYS = 30;
    private static final int CHANGE_WINDOW_DAYS = 14;
    private static final int SECTION_SIZE = 6;

    private final RadarStore store;
    private final RadarIntelStore intelStore;
    private final RadarTrendService trendService;

    public RadarHomeService(RadarStore store, RadarIntelStore intelStore, RadarTrendService trendService) {
        this.store = store;
        this.intelStore = intelStore;
        this.trendService = trendService;
    }

    public RadarHome build() {
        LocalDateTime now = LocalDateTime.now();
        List<Company> companies = store.listCompanies().stream().filter(company -> !company.ignored()).toList();
        Map<Long, Summary> signals = intelStore.signalSummaries(companies.stream().map(Company::id).toList());
        var profile = intelStore.loadProfile();

        Map<Long, Integer> meaningfulChangeCounts = new LinkedHashMap<>();
        List<CompanyChangeView> watchlistChanges = intelStore.recentChanges(Tier.IMPORTANT, CHANGE_WINDOW_DAYS,
                12, true);
        List<CompanyChangeView> allMeaningfulChanges = intelStore.recentChanges(Tier.IMPORTANT,
                FUNDING_WINDOW_DAYS, 200, false);
        allMeaningfulChanges.forEach(change ->
                meaningfulChangeCounts.merge(change.companyId(), 1, Integer::sum));

        Set<Long> fundedIds = new HashSet<>(
                intelStore.companiesWithRecentFunding(FUNDING_WINDOW_DAYS, 50));

        // A company can only appear once across the company-card sections.
        Set<Long> placed = new HashSet<>();
        watchlistChanges.forEach(change -> placed.add(change.companyId()));

        List<HomeSection> sections = new ArrayList<>();

        sections.add(HomeSection.ofChanges("watchlist-updates", "Watchlist Updates",
                "Meaningful changes from companies you follow, newest first.", watchlistChanges));

        List<Company> newToday = withinHours(companies, now, NEW_TODAY_HOURS);
        boolean usedFallback = newToday.isEmpty();
        if (usedFallback) newToday = withinHours(companies, now, NEW_FALLBACK_DAYS * 24);
        sections.add(companySection("new-today", "New Today",
                usedFallback
                        ? "Nothing new in the last 24 hours, so this shows the last " + NEW_FALLBACK_DAYS + " days."
                        : "Startups first discovered in the last 24 hours.",
                sortBy(newToday, Comparator.comparing(Company::firstSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))),
                placed, signals, profile, meaningfulChangeCounts, fundedIds, now));

        sections.add(companySection("recently-funded", "Recently Funded",
                "Companies where a funding round or new investor was detected in the last "
                        + FUNDING_WINDOW_DAYS + " days.",
                companies.stream().filter(company -> fundedIds.contains(company.id())).toList(),
                placed, signals, profile, meaningfulChangeCounts, fundedIds, now));

        sections.add(companySection("best-matches", "Best Matches For You",
                "Highest personal relevance against your configured interests.",
                sortBy(companies, Comparator.comparingInt(Company::personalScore).reversed()),
                placed, signals, profile, meaningfulChangeCounts, fundedIds, now));

        List<Company> byMomentum = companies.stream()
                .sorted(Comparator.comparingInt((Company company) -> momentum(company, meaningfulChangeCounts, now))
                        .reversed())
                .toList();
        sections.add(companySection("high-momentum", "High Momentum",
                "Corroborated by multiple sources, recently active, or changing quickly.",
                byMomentum, placed, signals, profile, meaningfulChangeCounts, fundedIds, now));

        List<TrendView> trends = trendService.listDetailed().stream().limit(SECTION_SIZE).toList();
        sections.add(HomeSection.ofTrends("emerging-trends", "Emerging Trends",
                "Themes grounded in companies actually present in your Radar.", trends));

        return new RadarHome(now, companies.size(), withinHours(companies, now, NEW_TODAY_HOURS).size(),
                intelStore.countMeaningfulChanges(CHANGE_WINDOW_DAYS), sections);
    }

    private HomeSection companySection(String key, String title, String subtitle, List<Company> candidates,
            Set<Long> placed, Map<Long, Summary> signals,
            com.startupvalidationbot.radar.intel.InterestProfile profile,
            Map<Long, Integer> changeCounts, Set<Long> fundedIds, LocalDateTime now) {
        List<HomeCompanyCard> cards = new ArrayList<>();
        for (Company company : candidates) {
            if (cards.size() >= SECTION_SIZE) break;
            if (company.id() == null || placed.contains(company.id())) continue;
            placed.add(company.id());
            cards.add(toCard(key, company, signals, profile, changeCounts, fundedIds, now));
        }
        return HomeSection.ofCompanies(key, title, subtitle, cards);
    }

    private HomeCompanyCard toCard(String sectionKey, Company company, Map<Long, Summary> signals,
            com.startupvalidationbot.radar.intel.InterestProfile profile, Map<Long, Integer> changeCounts,
            Set<Long> fundedIds, LocalDateTime now) {
        int meaningfulChanges = changeCounts.getOrDefault(company.id(), 0);
        boolean funded = fundedIds.contains(company.id());
        long daysSinceFirstSeen = daysBetween(company.firstSeenAt(), now);
        int momentum = momentum(company, changeCounts, now);

        var relevance = PersonalRelevance.score(company.name(), company.description(), company.sector(),
                company.categories(), profile, signals.getOrDefault(company.id(), Summary.empty()));

        return new HomeCompanyCard(company.id(), company.name(), company.description(), company.sector(),
                company.categories(), company.accelerator(), company.acceleratorBatch(), company.radarScore(),
                relevance.score(), company.sourceCount(), company.watched(), company.firstSeenAt(),
                company.lastSeenAt(),
                MomentumRanker.whyItMatters(company.name(), company.sourceCount(), daysSinceFirstSeen,
                        meaningfulChanges, funded, company.accelerator(), company.acceleratorBatch(),
                        company.radarScore()),
                relevance.reasons(),
                MomentumRanker.highlight(sectionKey, daysSinceFirstSeen, momentum, relevance.score(), funded));
    }

    private int momentum(Company company, Map<Long, Integer> changeCounts, LocalDateTime now) {
        return MomentumRanker.momentumScore(company.sourceCount(), daysBetween(company.lastSeenAt(), now),
                changeCounts.getOrDefault(company.id(), 0), company.radarScore());
    }

    private static List<Company> withinHours(List<Company> companies, LocalDateTime now, int hours) {
        LocalDateTime cutoff = now.minusHours(hours);
        return companies.stream()
                .filter(company -> company.firstSeenAt() != null && company.firstSeenAt().isAfter(cutoff))
                .toList();
    }

    private static List<Company> sortBy(List<Company> companies, Comparator<Company> comparator) {
        return companies.stream().sorted(comparator).toList();
    }

    private static long daysBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null) return Long.MAX_VALUE / 2;
        return Math.max(0, Duration.between(from, to).toDays());
    }
}
