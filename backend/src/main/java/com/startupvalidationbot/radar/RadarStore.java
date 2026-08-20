package com.startupvalidationbot.radar;

import static com.startupvalidationbot.radar.RadarDomain.*;
import static com.startupvalidationbot.radar.RadarAdminViews.*;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.startupvalidationbot.radar.intel.SnapshotChangeDetector;
import com.startupvalidationbot.radar.intel.SnapshotChangeDetector.DetectedChange;

@Repository
public class RadarStore {
    private static final String COMPANY_SELECT = """
            SELECT c.*, EXISTS(
                SELECT 1 FROM radar_watchlist_entries w WHERE w.company_id = c.id
            ) AS watched
            FROM radar_companies c
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final boolean postgres;

    public RadarStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.postgres = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }

    public List<Source> listSources() {
        return jdbc.query("SELECT * FROM radar_sources ORDER BY name", sourceMapper());
    }

    public Optional<Source> findSource(String sourceKey) {
        return jdbc.query("SELECT * FROM radar_sources WHERE source_key = ?", sourceMapper(), sourceKey)
                .stream().findFirst();
    }

    @Transactional
    public Source upsertSource(String sourceKey, String sourceType, String name, String url, boolean enabled) {
        LocalDateTime now = LocalDateTime.now();
        Optional<Source> existing = findSource(sourceKey);
        if (existing.isPresent()) {
            jdbc.update("""
                    UPDATE radar_sources SET source_type = ?, name = ?, url = ?, enabled = ?, updated_at = ?
                    WHERE source_key = ?
                    """, sourceType, name, blankToNull(url), enabled, now, sourceKey);
        } else {
            jdbc.update("""
                    INSERT INTO radar_sources (
                        source_key, source_type, name, url, config_json, enabled, last_status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, '{}', ?, 'NEVER_CHECKED', ?, ?)
                    """, sourceKey, sourceType, name, blankToNull(url), enabled, now, now);
        }
        return findSource(sourceKey).orElseThrow();
    }

    public void markSource(Source source, String status, String error) {
        jdbc.update("""
                UPDATE radar_sources SET last_checked_at = ?, last_status = ?, last_error = ?, updated_at = ?
                WHERE id = ?
                """, LocalDateTime.now(), status, blankToNull(error), LocalDateTime.now(), source.id());
    }

    public List<Company> listCompanies() {
        return jdbc.query(COMPANY_SELECT + " ORDER BY c.last_seen_at DESC", companyMapper());
    }

    public List<Company> listWatchedCompanies() {
        return jdbc.query(COMPANY_SELECT + " WHERE EXISTS (SELECT 1 FROM radar_watchlist_entries w2 "
                + "WHERE w2.company_id = c.id) ORDER BY c.last_seen_at DESC", companyMapper());
    }

    public Optional<Company> findCompany(long id) {
        return jdbc.query(COMPANY_SELECT + " WHERE c.id = ?", companyMapper(), id).stream().findFirst();
    }

    private Optional<Company> findCompanyByIdentity(String domain, String normalizedName) {
        if (domain != null) {
            Optional<Company> byDomain = jdbc.query(COMPANY_SELECT + " WHERE c.domain = ?", companyMapper(), domain)
                    .stream().findFirst();
            if (byDomain.isPresent()) {
                return byDomain;
            }
        }
        String nameQuery = domain == null
                ? COMPANY_SELECT + " WHERE c.normalized_name = ? ORDER BY c.id LIMIT 1"
                : COMPANY_SELECT + " WHERE c.normalized_name = ? AND c.domain IS NULL ORDER BY c.id LIMIT 1";
        return jdbc.query(nameQuery, companyMapper(), normalizedName).stream().findFirst();
    }

    @Transactional
    public CompanyUpsert upsertCompany(Candidate candidate) {
        String normalizedName = CompanyIdentity.normalizeName(candidate.companyName());
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("company name cannot normalize to an empty value");
        }
        String domain = CompanyIdentity.normalizeDomain(candidate.websiteUrl());
        Optional<Company> match = findCompanyByIdentity(domain, normalizedName);
        LocalDateTime now = LocalDateTime.now();
        boolean created = match.isEmpty();
        long companyId;

        if (created) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO radar_companies (
                            name, normalized_name, domain, website_url, description, sector, categories_json,
                            headquarters, founded_year, aliases_json, accelerator, accelerator_batch,
                            first_seen_at, last_seen_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', ?, ?, ?, ?, ?, ?)
                        """, new String[] { "id" });
                statement.setString(1, candidate.companyName().trim());
                statement.setString(2, normalizedName);
                statement.setString(3, domain);
                statement.setString(4, blankToNull(candidate.websiteUrl()));
                statement.setString(5, valueOrEmpty(candidate.description()));
                statement.setString(6, valueOrDefault(candidate.sector(), "Unknown"));
                statement.setString(7, toJson(safeList(candidate.categories())));
                statement.setString(8, blankToNull(candidate.headquarters()));
                if (candidate.foundedYear() == null) {
                    statement.setNull(9, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(9, candidate.foundedYear());
                }
                statement.setString(10, valueOrEmpty(candidate.accelerator()));
                statement.setString(11, valueOrEmpty(candidate.acceleratorBatch()));
                statement.setTimestamp(12, Timestamp.valueOf(now));
                statement.setTimestamp(13, Timestamp.valueOf(now));
                statement.setTimestamp(14, Timestamp.valueOf(now));
                statement.setTimestamp(15, Timestamp.valueOf(now));
                return statement;
            }, keys);
            companyId = keys.getKey().longValue();
        } else {
            Company current = match.orElseThrow();
            companyId = current.id();
            List<String> aliases = new ArrayList<>(current.aliases());
            if (!current.name().equalsIgnoreCase(candidate.companyName()) && !aliases.contains(current.name())) {
                aliases.add(current.name());
            }
            jdbc.update("""
                    UPDATE radar_companies SET
                        name = ?, normalized_name = ?, domain = ?, website_url = ?, description = ?, sector = ?,
                        categories_json = ?, headquarters = ?, founded_year = ?, aliases_json = ?,
                        accelerator = ?, accelerator_batch = ?, last_seen_at = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    candidate.companyName().trim(), normalizedName, domain != null ? domain : current.domain(),
                    prefer(candidate.websiteUrl(), current.websiteUrl()),
                    prefer(candidate.description(), current.description()),
                    preferUnknown(candidate.sector(), current.sector()),
                    toJson(mergeLists(current.categories(), candidate.categories())),
                    prefer(candidate.headquarters(), current.headquarters()),
                    candidate.foundedYear() != null ? candidate.foundedYear() : current.foundedYear(),
                    toJson(aliases), prefer(candidate.accelerator(), current.accelerator()),
                    prefer(candidate.acceleratorBatch(), current.acceleratorBatch()), now, now, companyId);
        }
        return new CompanyUpsert(findCompany(companyId).orElseThrow(), created);
    }

    @Transactional
    public DiscoverySaveResult saveDiscoveryAndSnapshot(long companyId, Source source, Candidate candidate) {
        String rawText = valueOrEmpty(candidate.rawText());
        String rawHash = ContentHash.sha256(rawText.isBlank() ? toJson(candidate) : rawText);
        LocalDateTime now = LocalDateTime.now();
        String externalId = valueOrDefault(candidate.externalId(), rawHash.substring(0, 24));
        List<Long> discoveryIds = jdbc.queryForList(
                "SELECT id FROM radar_discoveries WHERE source_id = ? AND external_id = ?", Long.class,
                source.id(), externalId);
        boolean created = discoveryIds.isEmpty();
        if (created) {
            jdbc.update("""
                    INSERT INTO radar_discoveries (
                        company_id, source_id, external_id, source_url, raw_text, raw_text_hash, discovered_at, last_seen_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, companyId, source.id(), externalId, blankToNull(candidate.sourceUrl()), rawText, rawHash, now, now);
        } else {
            jdbc.update("""
                    UPDATE radar_discoveries SET company_id = ?, source_url = ?, raw_text = ?, raw_text_hash = ?,
                        last_seen_at = ? WHERE id = ?
                    """, companyId, blankToNull(candidate.sourceUrl()), rawText, rawHash, now, discoveryIds.get(0));
        }

        Integer sourceCount = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT source_id) FROM radar_discoveries WHERE company_id = ?", Integer.class,
                companyId);
        jdbc.update("UPDATE radar_companies SET source_count = ?, updated_at = ? WHERE id = ?",
                sourceCount == null ? 0 : sourceCount, now, companyId);

        String snapshotJson = toJson(new PublicSnapshot(candidate.companyName(), candidate.websiteUrl(),
                candidate.description(), candidate.sector(), safeList(candidate.categories()), candidate.headquarters(),
                candidate.foundedYear(), candidate.sourceUrl(), candidate.publishedAt(), candidate.accelerator(),
                candidate.acceleratorBatch()));
        String inputHash = ContentHash.sha256(snapshotJson);
        boolean snapshotCreated = false;
        Long snapshotId = null;
        List<DetectedChange> detectedChanges = List.of();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM radar_company_snapshots WHERE company_id = ? AND input_hash = ?",
                Integer.class, companyId, inputHash) == 0) {
            List<String> priorSnapshots = jdbc.queryForList("""
                    SELECT snapshot_json FROM radar_company_snapshots
                    WHERE company_id = ? ORDER BY captured_at DESC LIMIT 1
                    """, String.class, companyId);

            // Deterministic, tiered change detection. Comparing meaning rather than bytes means a
            // reworded sentence no longer looks the same as a Series A.
            detectedChanges = priorSnapshots.isEmpty()
                    ? initialPublicEvents(source, rawText)
                    : SnapshotChangeDetector.detect(flattenSnapshot(readTree(priorSnapshots.get(0))),
                            flattenSnapshot(readTree(snapshotJson)));
            List<String> changes = new ArrayList<>();
            if (priorSnapshots.isEmpty()) {
                changes.add("First snapshot");
            }
            changes.addAll(detectedChanges.stream().map(DetectedChange::summary).toList());
            jdbc.update("""
                    INSERT INTO radar_company_snapshots (
                        company_id, source_id, captured_at, input_hash, snapshot_json, notable_changes_json
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """, companyId, source.id(), now, inputHash, snapshotJson, toJson(changes));
            snapshotCreated = true;
            snapshotId = jdbc.queryForList(
                    "SELECT id FROM radar_company_snapshots WHERE company_id = ? AND input_hash = ?",
                    Long.class, companyId, inputHash).stream().findFirst().orElse(null);
        }
        return new DiscoverySaveResult(created, snapshotCreated, snapshotId, detectedChanges);
    }

    private static List<DetectedChange> initialPublicEvents(Source source, String rawText) {
        return "RSS".equalsIgnoreCase(source.sourceType())
                ? SnapshotChangeDetector.detectInitialPublicEvent(rawText)
                : List.of();
    }

    /** Flattens a stored snapshot document into the plain field map the change detector consumes. */
    private static Map<String, String> flattenSnapshot(JsonNode node) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("companyName", node.path("companyName").asText(""));
        fields.put("websiteUrl", node.path("websiteUrl").asText(""));
        fields.put("description", node.path("description").asText(""));
        fields.put("sector", node.path("sector").asText(""));
        fields.put("headquarters", node.path("headquarters").asText(""));
        fields.put("sourceUrl", node.path("sourceUrl").asText(""));
        fields.put("accelerator", node.path("accelerator").asText(""));
        fields.put("acceleratorBatch", node.path("acceleratorBatch").asText(""));
        JsonNode categories = node.path("categories");
        if (categories.isArray()) {
            List<String> values = new ArrayList<>();
            categories.forEach(entry -> values.add(entry.asText("")));
            fields.put("categories", String.join(", ", values));
        } else {
            fields.put("categories", "");
        }
        return fields;
    }

    public Optional<Analysis> findCachedAnalysis(long companyId, String analysisType, String inputHash,
            String promptVersion, String schemaVersion, String provider, String model) {
        return jdbc.query("""
                SELECT * FROM radar_company_analyses
                WHERE company_id = ? AND analysis_type = ? AND input_hash = ? AND prompt_version = ?
                    AND schema_version = ? AND provider = ? AND model = ? AND status = 'SUCCESS'
                """, analysisMapper(), companyId, analysisType, inputHash, promptVersion, schemaVersion,
                provider, model).stream().findFirst();
    }

    public Optional<Analysis> findLatestAnalysis(long companyId, String analysisType) {
        return jdbc.query("""
                SELECT * FROM radar_company_analyses WHERE company_id = ? AND analysis_type = ?
                ORDER BY created_at DESC LIMIT 1
                """, analysisMapper(), companyId, analysisType).stream().findFirst();
    }

    public String latestSnapshotInputHash(long companyId) {
        return jdbc.queryForList("""
                SELECT input_hash FROM radar_company_snapshots
                WHERE company_id = ? ORDER BY captured_at DESC LIMIT 1
                """, String.class, companyId).stream().findFirst().orElse("");
    }

    @Transactional
    public Analysis saveAnalysis(long companyId, String analysisType, String inputHash, String promptVersion,
            String schemaVersion, String analysisOrigin, String provider, String model, AnalysisPayload payload) {
        LocalDateTime now = LocalDateTime.now();
        int inserted = insertIgnore("""
                INSERT INTO radar_company_analyses (
                    company_id, analysis_type, input_hash, prompt_version, schema_version, provider, model,
                    analysis_origin, status, analysis_json, radar_score, personal_score, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?, ?, ?)
                ON CONFLICT (company_id, analysis_type, input_hash, prompt_version) DO NOTHING
                """, """
                MERGE INTO radar_company_analyses AS target
                USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS incoming (
                    company_id, analysis_type, input_hash, prompt_version, schema_version, provider, model,
                    analysis_origin, analysis_json, radar_score, personal_score, created_at
                ) ON target.company_id = incoming.company_id
                    AND target.analysis_type = incoming.analysis_type
                    AND target.input_hash = incoming.input_hash
                    AND target.prompt_version = incoming.prompt_version
                WHEN NOT MATCHED THEN INSERT (
                    company_id, analysis_type, input_hash, prompt_version, schema_version, provider, model,
                    analysis_origin, status, analysis_json, radar_score, personal_score, created_at
                ) VALUES (
                    incoming.company_id, incoming.analysis_type, incoming.input_hash, incoming.prompt_version,
                    incoming.schema_version, incoming.provider, incoming.model, incoming.analysis_origin,
                    'SUCCESS', incoming.analysis_json, incoming.radar_score, incoming.personal_score,
                    incoming.created_at
                )
                """, companyId, analysisType, inputHash, promptVersion, schemaVersion, provider, model,
                analysisOrigin, toJson(payload), payload.radarScore(), payload.personalScore(), now);
        if (inserted == 0) {
            return findCachedAnalysis(companyId, analysisType, inputHash, promptVersion, schemaVersion, provider,
                    model).orElseThrow();
        }
        jdbc.update("""
                UPDATE radar_companies SET radar_score = ?, personal_score = ?, score_reasoning = ?, updated_at = ?
                WHERE id = ?
                """, payload.radarScore(), payload.personalScore(),
                String.join(" ", payload.whyInteresting()), now, companyId);
        syncInvestors(companyId, payload.likelyInvestors());
        return findCachedAnalysis(companyId, analysisType, inputHash, promptVersion, schemaVersion, provider,
                model).orElseThrow();
    }

    public void recordAiAttempt(long companyId, String analysisType, String provider, String model,
            String inputHash, String promptVersion, String schemaVersion, String status, String errorType,
            String errorMessage, int retryCount, Long latencyMs, Long inputTokens, Long outputTokens) {
        recordAiAttempt(companyId, analysisType, provider, model, inputHash, promptVersion, schemaVersion, status,
                errorType, errorMessage, retryCount, latencyMs, inputTokens, outputTokens, null, null, null);
    }

    public void recordAiAttempt(long companyId, String analysisType, String provider, String model,
            String inputHash, String promptVersion, String schemaVersion, String status, String errorType,
            String errorMessage, int retryCount, Long latencyMs, Long inputTokens, Long outputTokens,
            Integer httpStatus, String providerErrorType, String providerErrorCode) {
        jdbc.update("""
                INSERT INTO radar_ai_attempts (
                    company_id, analysis_type, provider, model, input_hash, prompt_version, schema_version,
                    status, error_type, error_message, retry_count, latency_ms, input_tokens, output_tokens,
                    http_status, provider_error_type, provider_error_code, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, companyId, analysisType, provider, model, inputHash, promptVersion, schemaVersion, status,
                blankToNull(errorType), blankToNull(truncate(errorMessage, 1_000)), retryCount, latencyMs,
                inputTokens, outputTokens, httpStatus, blankToNull(truncate(providerErrorType, 160)),
                blankToNull(truncate(providerErrorCode, 160)), LocalDateTime.now());
    }

    private void syncInvestors(long companyId, List<String> investorNames) {
        for (String name : safeList(investorNames)) {
            String normalized = CompanyIdentity.normalizeName(name);
            if (normalized.isBlank()) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            insertIgnore("""
                    INSERT INTO radar_investors (
                        name, normalized_name, investor_type, created_at, updated_at
                    ) VALUES (?, ?, 'Unknown', ?, ?)
                    ON CONFLICT (normalized_name) DO NOTHING
                    """, """
                    MERGE INTO radar_investors AS target
                    USING (VALUES (?, ?, ?, ?)) AS incoming (name, normalized_name, created_at, updated_at)
                       ON target.normalized_name = incoming.normalized_name
                    WHEN NOT MATCHED THEN
                        INSERT (name, normalized_name, investor_type, created_at, updated_at)
                        VALUES (incoming.name, incoming.normalized_name, 'Unknown', incoming.created_at,
                            incoming.updated_at)
                    """, name, normalized, now, now);
            long investorId = jdbc.queryForObject(
                    "SELECT id FROM radar_investors WHERE normalized_name = ?", Long.class, normalized);
            insertIgnore("""
                    INSERT INTO radar_company_investors (company_id, investor_id, created_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (company_id, investor_id) DO NOTHING
                    """, """
                    MERGE INTO radar_company_investors AS target
                    USING (VALUES (?, ?, ?)) AS incoming (company_id, investor_id, created_at)
                       ON target.company_id = incoming.company_id AND target.investor_id = incoming.investor_id
                    WHEN NOT MATCHED THEN
                        INSERT (company_id, investor_id, created_at)
                        VALUES (incoming.company_id, incoming.investor_id, incoming.created_at)
                    """, companyId, investorId, now);
        }
    }

    public void saveResearchSource(long companyId, String sourceType, String title, String url, String excerpt,
            boolean fact, EvidenceClassification evidenceClassification) {
        List<Long> existing = jdbc.queryForList("""
                SELECT id FROM radar_research_sources
                WHERE company_id = ? AND source_type = ? AND title = ? AND COALESCE(url, '') = COALESCE(?, '')
                """, Long.class, companyId, sourceType, title, blankToNull(url));
        EvidenceClassification classification = evidenceClassification == null ? EvidenceClassification.UNKNOWN
                : evidenceClassification;
        if (!existing.isEmpty()) {
            jdbc.update("""
                    UPDATE radar_research_sources
                    SET excerpt = ?, is_fact = ?, evidence_classification = ?
                    WHERE id = ?
                    """, valueOrEmpty(excerpt), fact, classification.name(), existing.get(0));
            return;
        }
        jdbc.update("""
                INSERT INTO radar_research_sources (
                    company_id, source_type, title, url, excerpt, is_fact, evidence_classification, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, companyId, sourceType, title, blankToNull(url), valueOrEmpty(excerpt), fact,
                classification.name(), LocalDateTime.now());
    }

    public CompanyDetail getCompanyDetail(long companyId) {
        Company company = findCompany(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Radar company not found: " + companyId));
        Analysis analysis = findLatestAnalysis(companyId, "DEEP_DIVE")
                .or(() -> findLatestAnalysis(companyId, "RADAR")).orElse(null);
        List<Snapshot> snapshots = jdbc.query("""
                SELECT id, captured_at, notable_changes_json, snapshot_json
                FROM radar_company_snapshots WHERE company_id = ? ORDER BY captured_at DESC LIMIT 20
                """, (rs, row) -> new Snapshot(rs.getLong("id"), timestamp(rs, "captured_at"),
                        readStringList(rs.getString("notable_changes_json")), rs.getString("snapshot_json")), companyId);
        List<ResearchSource> research = listResearchSources(companyId);
        List<Map<String, Object>> watch = jdbc.queryForList(
                "SELECT notes, next_review_at FROM radar_watchlist_entries WHERE company_id = ?", companyId);
        String notes = watch.isEmpty() ? "" : String.valueOf(watch.get(0).getOrDefault("notes", ""));
        Object reviewValue = watch.isEmpty() ? null : watch.get(0).get("next_review_at");
        LocalDateTime nextReview = reviewValue instanceof Timestamp value ? value.toLocalDateTime() : null;
        return new CompanyDetail(company, analysis, snapshots, research, notes, nextReview);
    }

    public List<ResearchSource> listResearchSources(long companyId) {
        return jdbc.query("""
                SELECT * FROM radar_research_sources WHERE company_id = ? ORDER BY created_at DESC
                """, (rs, row) -> new ResearchSource(rs.getLong("id"), rs.getString("source_type"),
                        rs.getString("title"), rs.getString("url"), timestamp(rs, "source_date"),
                        rs.getString("excerpt"), rs.getBoolean("is_fact"),
                        EvidenceClassification.valueOf(rs.getString("evidence_classification"))), companyId);
    }

    @Transactional
    public void watchCompany(long companyId, String notes, LocalDateTime nextReviewAt) {
        if (findCompany(companyId).isEmpty()) {
            throw new IllegalArgumentException("Radar company not found: " + companyId);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE radar_watchlist_entries SET notes = ?, next_review_at = ?, updated_at = ? WHERE company_id = ?
                """, valueOrEmpty(notes), nextReviewAt, now, companyId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO radar_watchlist_entries (
                        company_id, status, notes, next_review_at, created_at, updated_at
                    ) VALUES (?, 'WATCHING', ?, ?, ?, ?)
                    """, companyId, valueOrEmpty(notes), nextReviewAt, now, now);
        }
    }

    public void unwatchCompany(long companyId) {
        jdbc.update("DELETE FROM radar_watchlist_entries WHERE company_id = ?", companyId);
    }

    public void ignoreCompany(long companyId, boolean ignored) {
        if (jdbc.update("UPDATE radar_companies SET ignored = ?, updated_at = ? WHERE id = ?",
                ignored, LocalDateTime.now(), companyId) == 0) {
            throw new IllegalArgumentException("Radar company not found: " + companyId);
        }
    }

    @Transactional
    public void replaceTrends(List<TrendDraft> trends, LocalDateTime periodStart, LocalDateTime periodEnd) {
        jdbc.update("DELETE FROM radar_trend_companies");
        jdbc.update("DELETE FROM radar_trends");
        for (TrendDraft trend : trends) {
            KeyHolder keys = new GeneratedKeyHolder();
            LocalDateTime now = LocalDateTime.now();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO radar_trends (
                            trend_key, name, summary, company_count, momentum_score, period_start, period_end,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, new String[] { "id" });
                statement.setString(1, trend.key());
                statement.setString(2, trend.name());
                statement.setString(3, trend.summary());
                statement.setInt(4, trend.companyIds().size());
                statement.setInt(5, trend.momentumScore());
                statement.setTimestamp(6, Timestamp.valueOf(periodStart));
                statement.setTimestamp(7, Timestamp.valueOf(periodEnd));
                statement.setTimestamp(8, Timestamp.valueOf(now));
                statement.setTimestamp(9, Timestamp.valueOf(now));
                return statement;
            }, keys);
            long trendId = keys.getKey().longValue();
            for (long companyId : trend.companyIds()) {
                jdbc.update("""
                        INSERT INTO radar_trend_companies (trend_id, company_id, relevance_score, created_at)
                        VALUES (?, ?, ?, ?)
                        """, trendId, companyId, trend.momentumScore(), now);
            }
        }
    }

    public List<Trend> listTrends() {
        return jdbc.query("SELECT * FROM radar_trends ORDER BY momentum_score DESC", (rs, row) -> {
            long trendId = rs.getLong("id");
            List<Company> companies = jdbc.query(COMPANY_SELECT
                    + " JOIN radar_trend_companies tc ON tc.company_id = c.id WHERE tc.trend_id = ? "
                    + "ORDER BY c.radar_score DESC", companyMapper(), trendId);
            return new Trend(trendId, rs.getString("trend_key"), rs.getString("name"), rs.getString("summary"),
                    rs.getInt("company_count"), rs.getInt("momentum_score"), timestamp(rs, "period_start"),
                    timestamp(rs, "period_end"), companies);
        });
    }

    public int companyCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM radar_companies", Integer.class);
        return count == null ? 0 : count;
    }

    public int sourceCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM radar_sources", Integer.class);
        return count == null ? 0 : count;
    }

    public boolean databaseHealthy() {
        try {
            Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public Optional<JobRunStatus> latestJobRun(String jobType) {
        return jdbc.query("""
                SELECT job_type, status, started_at, completed_at, error_message
                FROM radar_job_runs WHERE job_type = ? ORDER BY started_at DESC LIMIT 1
                """, (rs, row) -> jobRunStatus(rs), jobType).stream().findFirst();
    }

    public List<JobRunStatus> recentJobFailures(int limit) {
        return jdbc.query("""
                SELECT job_type, status, started_at, completed_at, error_message
                FROM radar_job_runs WHERE status = 'FAILED' ORDER BY started_at DESC LIMIT ?
                """, (rs, row) -> jobRunStatus(rs), Math.max(1, Math.min(limit, 50)));
    }

    public LocalDateTime latestEnrichmentAt() {
        return jdbc.query("SELECT MAX(created_at) AS latest_at FROM radar_company_analyses",
                rs -> rs.next() ? timestamp(rs, "latest_at") : null);
    }

    public LocalDateTime latestDigestAt() {
        return jdbc.query("SELECT MAX(generated_at) AS latest_at FROM radar_digests",
                rs -> rs.next() ? timestamp(rs, "latest_at") : null);
    }

    public long discoveryCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM radar_discoveries", Long.class);
        return count == null ? 0 : count;
    }

    public long aiCallCount() {
        Long count = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE
                    WHEN status IN ('SUCCESS', 'FAILED')
                        AND COALESCE(error_type, '') NOT IN ('MISSING_CREDENTIALS', 'UNSUPPORTED_PROVIDER')
                    THEN retry_count + 1 ELSE 0 END), 0)
                FROM radar_ai_attempts
                """, Long.class);
        return count == null ? 0 : count;
    }

    public long aiAttemptCount(String status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM radar_ai_attempts WHERE status = ?", Long.class,
                status);
        return count == null ? 0 : count;
    }

    public RadarExport exportRadar() {
        List<ExportSource> sources = jdbc.query("""
                SELECT id, source_key, source_type, name, url, enabled, last_checked_at, last_status, last_error
                FROM radar_sources ORDER BY id
                """, (rs, row) -> new ExportSource(rs.getLong("id"), rs.getString("source_key"),
                        rs.getString("source_type"), rs.getString("name"), SafeUrl.redact(rs.getString("url")),
                        rs.getBoolean("enabled"), timestamp(rs, "last_checked_at"), rs.getString("last_status"),
                        SafeUrl.redactUrlsIn(rs.getString("last_error"))));
        List<ExportDiscovery> discoveries = jdbc.query("""
                SELECT id, company_id, source_id, external_id, source_url, raw_text_hash, discovered_at, last_seen_at
                FROM radar_discoveries ORDER BY id
                """, (rs, row) -> new ExportDiscovery(rs.getLong("id"), rs.getLong("company_id"),
                        rs.getLong("source_id"), rs.getString("external_id"),
                        SafeUrl.redact(rs.getString("source_url")),
                        rs.getString("raw_text_hash"), timestamp(rs, "discovered_at"),
                        timestamp(rs, "last_seen_at")));
        List<ExportSnapshot> snapshots = jdbc.query("""
                SELECT id, company_id, source_id, captured_at, input_hash, snapshot_json, notable_changes_json
                FROM radar_company_snapshots ORDER BY id
                """, (rs, row) -> new ExportSnapshot(rs.getLong("id"), rs.getLong("company_id"),
                        rs.getObject("source_id", Long.class), timestamp(rs, "captured_at"),
                        rs.getString("input_hash"), redactUrls(readTree(rs.getString("snapshot_json"))),
                        readStringList(rs.getString("notable_changes_json"))));
        RowMapper<Analysis> mapper = analysisMapper();
        List<ExportAnalysis> analyses = jdbc.query("SELECT * FROM radar_company_analyses ORDER BY id",
                (rs, row) -> new ExportAnalysis(rs.getLong("company_id"), rs.getString("input_hash"),
                        rs.getString("status"), redactUrls(mapper.mapRow(rs, row), Analysis.class)));
        List<ExportWatchlist> watchlist = jdbc.query("""
                SELECT company_id, status, notes, next_review_at, created_at, updated_at
                FROM radar_watchlist_entries ORDER BY id
                """, (rs, row) -> new ExportWatchlist(rs.getLong("company_id"), rs.getString("status"),
                        SafeUrl.redactUrlsIn(rs.getString("notes")), timestamp(rs, "next_review_at"),
                        timestamp(rs, "created_at"),
                        timestamp(rs, "updated_at")));
        List<ExportResearchSource> research = jdbc.query("""
                SELECT id, company_id, source_type, title, url, source_date, is_fact,
                       evidence_classification, created_at
                FROM radar_research_sources ORDER BY id
                """, (rs, row) -> new ExportResearchSource(rs.getLong("id"), rs.getLong("company_id"),
                        rs.getString("source_type"), rs.getString("title"), SafeUrl.redact(rs.getString("url")),
                        timestamp(rs, "source_date"), rs.getBoolean("is_fact"),
                        rs.getString("evidence_classification"), timestamp(rs, "created_at")));
        List<Company> companies = listCompanies().stream()
                .map(company -> redactUrls(company, Company.class)).toList();
        List<Trend> trends = listTrends().stream().map(trend -> redactUrls(trend, Trend.class)).toList();
        return new RadarExport("startup-radar-export-v1", LocalDateTime.now(), companies, discoveries,
                sources, snapshots, analyses, watchlist, trends, research);
    }

    @Transactional
    public JobStart beginJob(String jobType, String idempotencyKey, Duration leaseDuration) {
        LocalDateTime now = LocalDateTime.now();
        insertIgnore("""
                INSERT INTO radar_job_locks (job_type, lease_token, locked_until, updated_at)
                VALUES (?, NULL, ?, ?)
                ON CONFLICT (job_type) DO NOTHING
                """, """
                MERGE INTO radar_job_locks AS target
                USING (VALUES (?, ?, ?)) AS incoming (job_type, locked_until, updated_at)
                   ON target.job_type = incoming.job_type
                WHEN NOT MATCHED THEN
                    INSERT (job_type, lease_token, locked_until, updated_at)
                    VALUES (incoming.job_type, NULL, incoming.locked_until, incoming.updated_at)
                """, jobType, now.minusSeconds(1), now);
        String leaseToken = UUID.randomUUID().toString();
        int acquired = jdbc.update("""
                UPDATE radar_job_locks SET lease_token = ?, locked_until = ?, updated_at = ?
                WHERE job_type = ? AND locked_until < ?
                """, leaseToken, now.plus(leaseDuration), now, jobType, now);
        if (acquired == 0) return new JobStart(false, false, null);

        int inserted = insertIgnore("""
                INSERT INTO radar_job_runs (job_type, idempotency_key, status, started_at)
                VALUES (?, ?, 'RUNNING', ?)
                ON CONFLICT (job_type, idempotency_key) DO NOTHING
                """, """
                MERGE INTO radar_job_runs AS target
                USING (VALUES (?, ?, ?)) AS incoming (job_type, idempotency_key, started_at)
                   ON target.job_type = incoming.job_type
                    AND target.idempotency_key = incoming.idempotency_key
                WHEN NOT MATCHED THEN
                    INSERT (job_type, idempotency_key, status, started_at)
                    VALUES (incoming.job_type, incoming.idempotency_key, 'RUNNING', incoming.started_at)
                """, jobType, idempotencyKey, now);
        if (inserted == 1) {
            return new JobStart(true, false, leaseToken);
        }
        int restarted = jdbc.update("""
                UPDATE radar_job_runs SET status = 'RUNNING', summary_json = '{}', error_message = NULL,
                    started_at = ?, completed_at = NULL
                WHERE job_type = ? AND idempotency_key = ?
                    AND (status = 'FAILED' OR (status = 'RUNNING' AND started_at < ?))
                """, now, jobType, idempotencyKey, now.minus(leaseDuration));
        if (restarted == 1) return new JobStart(true, false, leaseToken);
        releaseJobLock(jobType, leaseToken);
        return new JobStart(false, true, null);
    }

    private static JobRunStatus jobRunStatus(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new JobRunStatus(rs.getString("job_type"), rs.getString("status"), timestamp(rs, "started_at"),
                timestamp(rs, "completed_at"), rs.getString("error_message"));
    }

    @Transactional
    public void completeJob(String jobType, String idempotencyKey, String leaseToken, String status, Object summary,
            String error) {
        jdbc.update("""
                UPDATE radar_job_runs SET status = ?, summary_json = ?, error_message = ?, completed_at = ?
                WHERE job_type = ? AND idempotency_key = ?
                """, status, toJson(summary), blankToNull(error), LocalDateTime.now(), jobType, idempotencyKey);
        releaseJobLock(jobType, leaseToken);
    }

    private void releaseJobLock(String jobType, String leaseToken) {
        if (leaseToken == null || leaseToken.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                UPDATE radar_job_locks SET lease_token = NULL, locked_until = ?, updated_at = ?
                WHERE job_type = ? AND lease_token = ?
                """, now.minusSeconds(1), now, jobType, leaseToken);
    }

    @Transactional
    public void saveDigest(String periodKey, String subject, String text, String html, String status,
            String messageId, String error) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE radar_digests SET subject = ?, text_body = ?, html_body = ?, status = ?,
                    provider_message_id = ?, error_message = ?, generated_at = ?, sent_at = ?
                WHERE period_key = ?
                """, subject, text, html, status, blankToNull(messageId), blankToNull(error), now,
                "SENT".equals(status) ? now : null, periodKey);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO radar_digests (
                        digest_type, period_key, subject, text_body, html_body, status, provider_message_id,
                        error_message, generated_at, sent_at
                    ) VALUES ('WEEKLY_COMBINED', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, periodKey, subject, text, html, status, blankToNull(messageId), blankToNull(error), now,
                    "SENT".equals(status) ? now : null);
        }
    }

    public record CompanyUpsert(Company company, boolean created) {
    }

    public record DiscoverySaveResult(boolean discoveryCreated, boolean snapshotCreated, Long snapshotId,
            List<DetectedChange> changes) {
    }

    public record JobStart(boolean acquired, boolean duplicate, String leaseToken) {
    }

    private record PublicSnapshot(String companyName, String websiteUrl, String description, String sector,
            List<String> categories, String headquarters, Integer foundedYear, String sourceUrl,
            LocalDateTime publishedAt, String accelerator, String acceleratorBatch) {
    }

    public record TrendDraft(String key, String name, String summary, int momentumScore, List<Long> companyIds) {
    }

    public record AnalysisPayload(String summary, String sector, String problem, String solution, String businessModel,
            String stage, List<String> founders, String fundingSummary, List<String> likelyInvestors,
            List<String> trendTags, List<String> monitoringTriggers, List<String> facts, List<String> inferences,
            List<String> whyInteresting, List<String> momentumSignals, List<String> tractionSignals,
            List<String> technicalDifferentiation, List<String> marketSignals, List<String> risks,
            List<String> bullCase, List<String> bearCase, List<String> unansweredQuestions, String whyItMatters,
            String whyYouShouldCare, String investmentAccessibility, String careerAngle, List<String> sourceUrls,
            String confidence, List<String> radarScoreInputs, List<String> personalScoreInputs,
            Map<String, Integer> radarDimensions, int radarScore, int personalScore) {
    }

    private RowMapper<Source> sourceMapper() {
        return (rs, row) -> new Source(rs.getLong("id"), rs.getString("source_key"), rs.getString("source_type"),
                rs.getString("name"), rs.getString("url"), rs.getString("config_json"), rs.getBoolean("enabled"),
                timestamp(rs, "last_checked_at"), rs.getString("last_status"), rs.getString("last_error"));
    }

    private RowMapper<Company> companyMapper() {
        return (rs, row) -> new Company(rs.getLong("id"), rs.getString("name"), rs.getString("domain"),
                rs.getString("website_url"), rs.getString("description"), rs.getString("sector"),
                readStringList(rs.getString("categories_json")), rs.getString("headquarters"),
                (Integer) rs.getObject("founded_year"), readStringList(rs.getString("aliases_json")),
                rs.getInt("radar_score"), rs.getInt("personal_score"), rs.getString("score_reasoning"),
                rs.getInt("source_count"), timestamp(rs, "first_seen_at"), timestamp(rs, "last_seen_at"),
                rs.getBoolean("ignored"), rs.getBoolean("watched"),
                valueOrEmpty(rs.getString("accelerator")), valueOrEmpty(rs.getString("accelerator_batch")));
    }

    private RowMapper<Analysis> analysisMapper() {
        return (rs, row) -> {
            JsonNode node = readTree(rs.getString("analysis_json"));
            return new Analysis(rs.getLong("id"), rs.getString("analysis_type"),
                    rs.getString("analysis_origin"), rs.getString("provider"), rs.getString("model"),
                    rs.getString("prompt_version"), rs.getString("schema_version"),
                    node.path("summary").asText(""), node.path("sector").asText("Unknown"),
                    node.path("problem").asText("Unknown"), node.path("solution").asText("Unknown"),
                    node.path("businessModel").asText("Unknown"), node.path("stage").asText("Unknown"),
                    readNodeList(node.path("founders")),
                    node.path("fundingSummary").asText("Unknown"), readNodeList(node.path("likelyInvestors")),
                    readNodeList(node.path("trendTags")), readNodeList(node.path("monitoringTriggers")),
                    readNodeList(node.path("facts")),
                    readNodeList(node.path("inferences")), readNodeList(node.path("whyInteresting")),
                    readNodeList(node.path("momentumSignals")), readNodeList(node.path("tractionSignals")),
                    readNodeList(node.path("technicalDifferentiation")), readNodeList(node.path("marketSignals")),
                    readNodeList(node.path("risks")), readNodeList(node.path("bullCase")),
                    readNodeList(node.path("bearCase")),
                    readNodeList(node.path("unansweredQuestions")), node.path("whyItMatters").asText(""),
                    node.path("whyYouShouldCare").asText(""),
                    node.path("investmentAccessibility").asText("Unknown"),
                    node.path("careerAngle").asText("Unknown"), readNodeList(node.path("sourceUrls")),
                    node.path("confidence").asText("LOW"), readNodeList(node.path("radarScoreInputs")),
                    readNodeList(node.path("personalScoreInputs")),
                    objectMapper.convertValue(node.path("radarDimensions"), new TypeReference<Map<String, Integer>>() {}),
                    rs.getInt("radar_score"), rs.getInt("personal_score"), timestamp(rs, "created_at"));
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize Radar data", error);
        }
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Stored Radar JSON is invalid", error);
        }
    }

    private <T> T redactUrls(T value, Class<T> type) {
        try {
            return objectMapper.treeToValue(redactUrls(objectMapper.valueToTree(value)), type);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to sanitize Radar export data", error);
        }
    }

    private static JsonNode redactUrls(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    object.put(entry.getKey(), SafeUrl.redactUrlsIn(value.asText()));
                } else {
                    redactUrls(value);
                }
            });
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                JsonNode value = array.get(index);
                if (value.isTextual()) {
                    array.set(index, TextNode.valueOf(SafeUrl.redactUrlsIn(value.asText())));
                } else {
                    redactUrls(value);
                }
            }
        }
        return node;
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException error) {
            return List.of();
        }
    }

    private static List<String> readNodeList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private int insertIgnore(String postgresSql, String fallbackSql, Object... args) {
        return jdbc.update(postgres ? postgresSql : fallbackSql, args);
    }

    private static LocalDateTime timestamp(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private static String prefer(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private static String preferUnknown(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() || "Unknown".equalsIgnoreCase(preferred)
                ? valueOrDefault(fallback, "Unknown") : preferred.trim();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).distinct().toList();
    }

    private static List<String> mergeLists(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(safeList(first));
        for (String value : safeList(second)) {
            if (merged.stream().noneMatch(item -> item.equalsIgnoreCase(value))) {
                merged.add(value);
            }
        }
        return Collections.unmodifiableList(merged);
    }
}
