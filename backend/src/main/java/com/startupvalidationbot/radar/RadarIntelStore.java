package com.startupvalidationbot.radar;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.RadarIntelViews.CompanyChangeView;
import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;
import com.startupvalidationbot.radar.intel.InteractionSignal;
import com.startupvalidationbot.radar.intel.InteractionSignal.Summary;
import com.startupvalidationbot.radar.intel.InterestProfile;
import com.startupvalidationbot.radar.intel.InterestProfile.Interest;
import com.startupvalidationbot.radar.intel.PersonalRelevanceInputs;
import com.startupvalidationbot.radar.intel.SnapshotChangeDetector.DetectedChange;

/**
 * Persistence for the Phase 2 intelligence layer.
 *
 * Kept separate from {@link RadarStore} so the hardened discovery/analysis path is not disturbed.
 */
@Repository
public class RadarIntelStore implements PersonalRelevanceInputs {
    private static final int PROFILE_ROW_ID = 1;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RadarIntelStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- interests

    @Override
    public InterestProfile loadProfile() {
        List<String> rows = jdbc.queryForList(
                "SELECT interests_json FROM radar_interest_profile WHERE id = ?", String.class, PROFILE_ROW_ID);
        if (rows.isEmpty()) return InterestProfile.defaultProfile();
        try {
            List<Interest> interests = objectMapper.readValue(rows.get(0), new TypeReference<List<Interest>>() {});
            InterestProfile profile = new InterestProfile(interests);
            return profile.isEmpty() ? InterestProfile.defaultProfile() : profile;
        } catch (JsonProcessingException error) {
            // A corrupt profile must not take the whole feed down; fall back to the documented default.
            return InterestProfile.defaultProfile();
        }
    }

    public Optional<LocalDateTime> profileUpdatedAt() {
        return jdbc.queryForList("SELECT updated_at FROM radar_interest_profile WHERE id = ?",
                LocalDateTime.class, PROFILE_ROW_ID).stream().findFirst();
    }

    @Transactional
    public InterestProfile saveProfile(InterestProfile profile) {
        InterestProfile safe = profile == null || profile.isEmpty() ? InterestProfile.defaultProfile() : profile;
        String json = toJson(safe.interests());
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update(
                "UPDATE radar_interest_profile SET interests_json = ?, updated_at = ? WHERE id = ?",
                json, now, PROFILE_ROW_ID);
        if (updated == 0) {
            jdbc.update("INSERT INTO radar_interest_profile (id, interests_json, updated_at) VALUES (?, ?, ?)",
                    PROFILE_ROW_ID, json, now);
        }
        return safe;
    }

    // ---------------------------------------------------------------- interaction signals

    public void recordSignal(long companyId, InteractionSignal signal) {
        jdbc.update("INSERT INTO radar_interaction_signals (company_id, signal_type, created_at) VALUES (?, ?, ?)",
                companyId, signal.name(), LocalDateTime.now());
    }

    @Override
    public Summary signalSummary(long companyId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT signal_type, COUNT(*) AS total FROM radar_interaction_signals
                 WHERE company_id = ? GROUP BY signal_type
                """, resultSet -> {
            counts.put(resultSet.getString("signal_type"), resultSet.getInt("total"));
        }, companyId);
        Boolean currentlyIgnored = jdbc.queryForObject(
                "SELECT ignored FROM radar_companies WHERE id = ?", Boolean.class, companyId);
        return toSummary(counts, Boolean.TRUE.equals(currentlyIgnored));
    }

    /** Batched lookup so a feed of 50 companies does not issue 50 queries. */
    public Map<Long, Summary> signalSummaries(List<Long> companyIds) {
        Map<Long, Summary> summaries = new LinkedHashMap<>();
        if (companyIds == null || companyIds.isEmpty()) return summaries;

        Map<Long, Map<String, Integer>> raw = new LinkedHashMap<>();
        String placeholders = String.join(",", companyIds.stream().map(id -> "?").toList());
        jdbc.query("SELECT company_id, signal_type, COUNT(*) AS total FROM radar_interaction_signals "
                + "WHERE company_id IN (" + placeholders + ") GROUP BY company_id, signal_type",
                resultSet -> {
                    raw.computeIfAbsent(resultSet.getLong("company_id"), ignored -> new LinkedHashMap<>())
                            .put(resultSet.getString("signal_type"), resultSet.getInt("total"));
                }, companyIds.toArray());
        Set<Long> ignoredCompanyIds = new HashSet<>(jdbc.queryForList("SELECT id FROM radar_companies WHERE id IN ("
                + placeholders + ") AND ignored = TRUE", Long.class, companyIds.toArray()));

        for (Long companyId : companyIds) {
            summaries.put(companyId, toSummary(raw.getOrDefault(companyId, Map.of()),
                    ignoredCompanyIds.contains(companyId)));
        }
        return summaries;
    }

    public List<SignalCount> signalTotals() {
        return jdbc.query("""
                SELECT signal_type, COUNT(*) AS total FROM radar_interaction_signals GROUP BY signal_type
                ORDER BY total DESC
                """, (rs, row) -> new SignalCount(rs.getString("signal_type"), rs.getInt("total")));
    }

    public void updatePersonalScore(long companyId, int personalScore) {
        jdbc.update("UPDATE radar_companies SET personal_score = ?, updated_at = ? WHERE id = ?",
                Math.max(0, Math.min(100, personalScore)), LocalDateTime.now(), companyId);
    }

    // ---------------------------------------------------------------- company changes

    @Transactional
    public int recordChanges(long companyId, Long snapshotId, List<DetectedChange> changes) {
        if (changes == null || changes.isEmpty()) return 0;
        LocalDateTime now = LocalDateTime.now();
        int written = 0;
        for (DetectedChange change : changes) {
            jdbc.update("""
                    INSERT INTO radar_company_changes (company_id, snapshot_id, change_type, significance,
                        summary, previous_value, current_value, why_it_matters, detected_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, companyId, snapshotId, change.changeType(), change.significance().name(),
                    truncate(change.summary(), 500), truncate(change.previousValue(), 1000),
                    truncate(change.currentValue(), 1000), truncate(change.whyItMatters(), 1000), now);
            written++;
        }
        return written;
    }

    public List<CompanyChangeView> changesForCompany(long companyId, int limit) {
        return jdbc.query("""
                SELECT ch.id, ch.company_id, c.name AS company_name, ch.change_type, ch.significance, ch.summary,
                       ch.previous_value, ch.current_value, ch.why_it_matters, ch.detected_at
                  FROM radar_company_changes ch
                  JOIN radar_companies c ON c.id = ch.company_id
                 WHERE ch.company_id = ?
                 ORDER BY ch.detected_at DESC, ch.id DESC
                 LIMIT ?
                """, changeMapper(), companyId, Math.max(1, limit));
    }

    /** Changes at or above a tier, newest first. Used by Radar Home and the watchlist. */
    public List<CompanyChangeView> recentChanges(Tier minimumTier, int sinceDays, int limit, boolean watchedOnly) {
        List<String> allowed = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            if (tier.atLeast(minimumTier)) allowed.add(tier.name());
        }
        String placeholders = String.join(",", allowed.stream().map(value -> "?").toList());
        List<Object> args = new ArrayList<>(allowed);
        args.add(LocalDateTime.now().minusDays(Math.max(1, sinceDays)));
        args.add(Math.max(1, limit));

        String watchedClause = watchedOnly
                ? " AND EXISTS (SELECT 1 FROM radar_watchlist_entries w WHERE w.company_id = ch.company_id)"
                : "";

        return jdbc.query("SELECT ch.id, ch.company_id, c.name AS company_name, ch.change_type, ch.significance,"
                + " ch.summary, ch.previous_value, ch.current_value, ch.why_it_matters, ch.detected_at"
                + " FROM radar_company_changes ch JOIN radar_companies c ON c.id = ch.company_id"
                + " WHERE ch.significance IN (" + placeholders + ") AND ch.detected_at >= ?" + watchedClause
                + " ORDER BY ch.detected_at DESC, ch.id DESC LIMIT ?",
                changeMapper(), args.toArray());
    }

    public int countMeaningfulChanges(int sinceDays) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM radar_company_changes
                 WHERE significance IN ('IMPORTANT', 'MAJOR') AND detected_at >= ?
                """, Integer.class, LocalDateTime.now().minusDays(Math.max(1, sinceDays)));
        return count == null ? 0 : count;
    }

    /** Companies whose most recent funding-type change is recent. Powers "Recently Funded". */
    public List<Long> companiesWithRecentFunding(int sinceDays, int limit) {
        return jdbc.queryForList("""
                SELECT ch.company_id FROM radar_company_changes ch
                 WHERE ch.change_type IN ('FUNDING_ROUND', 'NEW_INVESTOR')
                   AND ch.detected_at >= ?
                 GROUP BY ch.company_id
                 ORDER BY MAX(ch.detected_at) DESC
                 LIMIT ?
                """, Long.class, LocalDateTime.now().minusDays(Math.max(1, sinceDays)), Math.max(1, limit));
    }

    // ---------------------------------------------------------------- trend support

    /** First-seen counts per trend key across the current and preceding window. */
    public TrendWindowCounts trendWindowCounts(List<Long> companyIds, LocalDateTime windowStart,
            LocalDateTime priorStart) {
        if (companyIds == null || companyIds.isEmpty()) return new TrendWindowCounts(0, 0, 0);
        String placeholders = String.join(",", companyIds.stream().map(id -> "?").toList());

        List<Object> recentArgs = new ArrayList<>(companyIds);
        recentArgs.add(windowStart);
        Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM radar_companies WHERE id IN ("
                + placeholders + ") AND first_seen_at >= ?", Integer.class, recentArgs.toArray());

        List<Object> priorArgs = new ArrayList<>(companyIds);
        priorArgs.add(priorStart);
        priorArgs.add(windowStart);
        Integer prior = jdbc.queryForObject("SELECT COUNT(*) FROM radar_companies WHERE id IN ("
                + placeholders + ") AND first_seen_at >= ? AND first_seen_at < ?", Integer.class,
                priorArgs.toArray());

        Integer sources = jdbc.queryForObject("SELECT COUNT(DISTINCT d.source_id) FROM radar_discoveries d "
                + "WHERE d.company_id IN (" + placeholders + ")", Integer.class, companyIds.toArray());

        return new TrendWindowCounts(recent == null ? 0 : recent, prior == null ? 0 : prior,
                sources == null ? 0 : sources);
    }

    public boolean hasHistoryBefore(LocalDateTime instant) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM radar_companies WHERE first_seen_at < ?",
                Integer.class, instant);
        return count != null && count > 0;
    }

    @Transactional
    public void updateTrendMetrics(String trendKey, String whyItMatters, String confidence, int recentDiscoveries,
            int priorDiscoveries, String velocityDirection, String velocityNote) {
        jdbc.update("""
                UPDATE radar_trends SET why_it_matters = ?, confidence = ?, recent_discoveries = ?,
                    prior_discoveries = ?, velocity_direction = ?, velocity_note = ?, updated_at = ?
                 WHERE trend_key = ?
                """, truncate(whyItMatters, 4000), confidence, recentDiscoveries, priorDiscoveries,
                velocityDirection, truncate(velocityNote, 300), LocalDateTime.now(), trendKey);
    }

    /** The Phase 2 metric columns for each trend, keyed by trend_key. */
    public Map<String, TrendMetrics> trendMetrics() {
        Map<String, TrendMetrics> metrics = new LinkedHashMap<>();
        jdbc.query("""
                SELECT trend_key, why_it_matters, confidence, recent_discoveries, prior_discoveries,
                       velocity_direction, velocity_note
                  FROM radar_trends
                """, resultSet -> {
            metrics.put(resultSet.getString("trend_key"), new TrendMetrics(
                    resultSet.getString("why_it_matters"), resultSet.getString("confidence"),
                    resultSet.getInt("recent_discoveries"), resultSet.getInt("prior_discoveries"),
                    resultSet.getString("velocity_direction"), resultSet.getString("velocity_note")));
        });
        return metrics;
    }

    /** Trend keys a company belongs to, used for similarity. */
    public Map<Long, List<String>> trendKeysByCompany() {
        Map<Long, List<String>> keys = new LinkedHashMap<>();
        jdbc.query("""
                SELECT tc.company_id, t.trend_key FROM radar_trend_companies tc
                  JOIN radar_trends t ON t.id = tc.trend_id
                """, resultSet -> {
            keys.computeIfAbsent(resultSet.getLong("company_id"), ignored -> new ArrayList<>())
                    .add(resultSet.getString("trend_key"));
        });
        return keys;
    }

    // ---------------------------------------------------------------- helpers

    private static Summary toSummary(Map<String, Integer> counts, boolean currentlyIgnored) {
        return new Summary(counts.getOrDefault("WATCH", 0),
                currentlyIgnored ? Math.max(1, counts.getOrDefault("IGNORE", 0)) : 0,
                counts.getOrDefault("DEEP_DIVE", 0), counts.getOrDefault("VISIT", 0));
    }

    private static org.springframework.jdbc.core.RowMapper<CompanyChangeView> changeMapper() {
        return (rs, row) -> new CompanyChangeView(rs.getLong("id"), rs.getLong("company_id"),
                rs.getString("company_name"), rs.getString("change_type"), rs.getString("significance"),
                rs.getString("summary"), rs.getString("previous_value"), rs.getString("current_value"),
                rs.getString("why_it_matters"),
                rs.getTimestamp("detected_at") == null ? null : rs.getTimestamp("detected_at").toLocalDateTime());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize Radar interest profile", error);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record SignalCount(String signalType, int total) {
        public String label() {
            return signalType == null ? "" : signalType.toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    public record TrendWindowCounts(int recent, int prior, int distinctSources) {
    }

    public record TrendMetrics(String whyItMatters, String confidence, int recentDiscoveries,
            int priorDiscoveries, String velocityDirection, String velocityNote) {

        public static TrendMetrics empty() {
            return new TrendMetrics("", "LOW", 0, 0, "UNKNOWN", "");
        }
    }
}
