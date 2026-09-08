package com.startupvalidationbot.offering;

import static com.startupvalidationbot.offering.OfferingDomain.*;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.ContentHash;

@Repository
public class OfferingStore {
    private static final String SELECT = """
            SELECT o.*, c.name AS company_name
            FROM radar_offerings o
            LEFT JOIN radar_companies c ON c.id = o.radar_company_id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OfferingStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Offering> list(String status, String platform, String matchStatus, Long companyId) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        addFilter(sql, args, "o.status", status);
        if (!blank(platform)) {
            sql.append(" AND UPPER(o.platform) = ?");
            args.add(platform.trim().toUpperCase());
        }
        addFilter(sql, args, "o.match_status", matchStatus);
        if (companyId != null) {
            sql.append(" AND o.radar_company_id = ?");
            args.add(companyId);
        }
        sql.append(" ORDER BY o.filing_date DESC, o.id DESC");
        return jdbc.query(sql.toString(), offeringMapper(), args.toArray());
    }

    public Optional<Offering> find(long id) {
        return jdbc.query(SELECT + " WHERE o.id = ?", offeringMapper(), id).stream().findFirst();
    }

    public Map<String, String> facts(long offeringId) {
        String value = jdbc.queryForObject("SELECT raw_facts_json FROM radar_offerings WHERE id=?", String.class,
                offeringId);
        if (value == null || value.isBlank()) return Map.of();
        try {
            JsonNode node = objectMapper.readTree(value);
            java.util.Map<String, String> facts = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            node.properties().forEach(entry -> facts.put(entry.getKey(), entry.getValue().asText("")));
            return Map.copyOf(facts);
        } catch (JsonProcessingException error) {
            return Map.of();
        }
    }

    public void markResolution(long offeringId, String status, String reason, List<String> sourcesChecked) {
        jdbc.update("""
                UPDATE radar_offerings SET last_resolution_attempt_at=?, resolution_sources_checked=?,
                  resolution_reason=?, resolution_status=?, updated_at=? WHERE id=?
                """, LocalDateTime.now(), json(sourcesChecked), nullIfBlank(reason), status,
                LocalDateTime.now(), offeringId);
    }

    public List<StoredIdentity> listStoredIdentities() {
        return jdbc.query("""
                SELECT id, issuer_name, raw_facts_json FROM radar_offerings
                WHERE match_status IN ('CONFIRMED', 'LIKELY', 'AMBIGUOUS')
                """, (rs, row) -> new StoredIdentity(rs.getLong("id"), rs.getString("issuer_name"),
                        issuerWebsite(rs.getString("raw_facts_json"))));
    }

    public List<StoredCandidate> listStoredCandidatesForResolution(int limit) {
        return jdbc.query("""
                SELECT * FROM radar_offerings
                WHERE match_status IN ('LIKELY','AMBIGUOUS')
                ORDER BY COALESCE(last_resolution_attempt_at, TIMESTAMP '1970-01-01 00:00:00'), updated_at DESC
                LIMIT ?
                """, (rs, row) -> {
            Map<String, String> facts = factsFromJson(rs.getString("raw_facts_json"));
            Candidate candidate = new Candidate(rs.getString("issuer_name"), rs.getString("issuer_cik"),
                    issuerWebsite(rs.getString("raw_facts_json")), rs.getString("platform"),
                    rs.getString("intermediary_name"), rs.getString("intermediary_cik"),
                    rs.getString("offering_url"), rs.getString("sec_filing_url"),
                    rs.getString("sec_accession_number"), rs.getString("sec_file_number"),
                    rs.getString("filing_type"), rs.getObject("filing_date", LocalDate.class),
                    rs.getString("security_type"), rs.getBigDecimal("minimum_investment"),
                    rs.getBigDecimal("target_amount"), rs.getBigDecimal("maximum_amount"),
                    rs.getString("valuation_or_cap"), rs.getObject("deadline", LocalDate.class),
                    rs.getBigDecimal("amount_raised"), rs.getString("source"), facts);
            return new StoredCandidate(rs.getLong("id"), candidate);
        }, Math.max(1, Math.min(limit, 50)));
    }

    public void updateMatch(long offeringId, Match match) {
        jdbc.update("""
                UPDATE radar_offerings SET radar_company_id=?, match_status=?, match_confidence=?,
                  match_reason=?, updated_at=? WHERE id=?
                """, match.companyId(), match.status().name(), match.confidence(), match.reason(),
                LocalDateTime.now(), offeringId);
    }

    public boolean attachCampaignUrl(long offeringId, String platform, String campaignUrl) {
        return jdbc.update("""
                UPDATE radar_offerings SET offering_url=?,platform=CASE WHEN UPPER(platform)='UNKNOWN'
                  THEN ? ELSE platform END,updated_at=?
                WHERE id=? AND (offering_url IS NULL OR TRIM(offering_url)='')
                """, campaignUrl, platform, LocalDateTime.now(), offeringId) == 1;
    }

    @Transactional
    public UpsertResult upsert(Candidate candidate, Match match) {
        LocalDateTime now = LocalDateTime.now();
        Status nextStatus = status(candidate);
        Optional<Offering> existing = findExisting(candidate);
        boolean created = existing.isEmpty();
        long id;
        Status previousStatus = existing.map(Offering::status).orElse(null);

        if (existing.isPresent() && isOlder(candidate, existing.orElseThrow())) {
            id = existing.orElseThrow().id();
            insertFiling(id, candidate, now);
            jdbc.update("UPDATE radar_offerings SET last_seen_at=?, updated_at=? WHERE id=?", now, now, id);
            return new UpsertResult(find(id).orElseThrow(), false);
        }

        if (created) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO radar_offerings (
                          radar_company_id, issuer_name, issuer_name_normalized, issuer_cik, platform,
                          intermediary_name, intermediary_cik, offering_url, sec_filing_url,
                          sec_accession_number, sec_file_number, filing_type, filing_date, offering_exemption,
                          security_type, minimum_investment, target_amount, maximum_amount, valuation_or_cap,
                          deadline, amount_raised, status, source, match_status, match_confidence, match_reason,
                          raw_facts_json, first_seen_at, last_seen_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REG_CF', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, new String[] { "id" });
                bind(statement, candidate, match, nextStatus, now, now);
                return statement;
            }, keys);
            id = keys.getKey().longValue();
        } else {
            Offering current = existing.orElseThrow();
            id = current.id();
            jdbc.update("""
                    UPDATE radar_offerings SET radar_company_id=?, issuer_name=?, issuer_name_normalized=?,
                      issuer_cik=?, platform=?, intermediary_name=?, intermediary_cik=?, offering_url=COALESCE(?,offering_url),
                      sec_filing_url=?, sec_accession_number=?, sec_file_number=?, filing_type=?, filing_date=?,
                      security_type=?, minimum_investment=?, target_amount=?, maximum_amount=?, valuation_or_cap=?,
                      deadline=?, amount_raised=?, status=?, source=?, match_status=?, match_confidence=?,
                      match_reason=?, raw_facts_json=?, provenance='SEC_EDGAR',
                      reconciliation_status='SEC_RECONCILED', last_seen_at=?, updated_at=? WHERE id=?
                    """, match.companyId(), candidate.issuerName(), normalize(candidate.issuerName()),
                    candidate.issuerCik(), candidate.platform(), nullIfBlank(candidate.intermediaryName()),
                    nullIfBlank(candidate.intermediaryCik()), nullIfBlank(candidate.offeringUrl()),
                    candidate.secFilingUrl(), candidate.accessionNumber(), nullIfBlank(candidate.fileNumber()),
                    candidate.filingType(), candidate.filingDate(), nullIfBlank(candidate.securityType()),
                    candidate.minimumInvestment(), candidate.targetAmount(), candidate.maximumAmount(),
                    nullIfBlank(candidate.valuationOrCap()), candidate.deadline(), candidate.amountRaised(),
                    nextStatus.name(), candidate.source(), match.status().name(), match.confidence(), match.reason(),
                    json(candidate.facts()), now, now, id);
        }

        boolean newFiling = insertFiling(id, candidate, now);

        if ((created || newFiling) && match.companyId() != null && match.status() == MatchStatus.CONFIRMED) {
            recordChange(match.companyId(), created, previousStatus, nextStatus, candidate);
        }
        return new UpsertResult(find(id).orElseThrow(), created);
    }

    private boolean insertFiling(long id, Candidate candidate, LocalDateTime now) {
        String factsHash = ContentHash.sha256(json(candidate.facts()));
        return jdbc.update("""
                INSERT INTO radar_offering_filings
                  (offering_id, accession_number, filing_type, filing_date, filing_url, facts_hash, created_at)
                SELECT ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (SELECT 1 FROM radar_offering_filings WHERE accession_number = ?)
                """, id, candidate.accessionNumber(), candidate.filingType(), candidate.filingDate(),
                candidate.secFilingUrl(), factsHash, now, candidate.accessionNumber()) == 1;
    }

    @Transactional
    public void markSource(String key, String status, String error, int inspected) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime success = "OK".equals(status) ? now : null;
        int updated = jdbc.update("""
                UPDATE radar_offering_source_state SET
                  last_success_at=COALESCE(?, last_success_at), last_status=?, last_error=?,
                  records_inspected=?, updated_at=? WHERE source_key=?
                """, success, status, nullIfBlank(error), inspected, now, key);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO radar_offering_source_state
                    (source_key, last_success_at, last_status, last_error, records_inspected, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, key, success, status, nullIfBlank(error), inspected, now);
        }
    }

    public boolean baselineDue(int days) {
        List<LocalDateTime> values = jdbc.queryForList("""
                SELECT last_success_at FROM radar_offering_source_state
                WHERE source_key = 'sec-cf-baseline' AND last_status = 'OK'
                """, LocalDateTime.class);
        return values.isEmpty() || values.get(0) == null || values.get(0).isBefore(LocalDateTime.now().minusDays(days));
    }

    public Diagnostics diagnostics() {
        long total = count("SELECT COUNT(*) FROM radar_offerings");
        long confirmed = count("SELECT COUNT(*) FROM radar_offerings WHERE match_status='CONFIRMED'");
        long possible = count("SELECT COUNT(*) FROM radar_offerings WHERE match_status IN ('LIKELY','AMBIGUOUS')");
        SourceDiagnostic source = jdbc.query("""
                SELECT last_status, last_success_at, last_error FROM radar_offering_source_state
                ORDER BY CASE WHEN last_status='ERROR' THEN 0 ELSE 1 END, updated_at DESC LIMIT 1
                """, rs -> rs.next()
                        ? new SourceDiagnostic(rs.getString(1), timestamp(rs.getTimestamp(2)), rs.getString(3))
                        : new SourceDiagnostic("NEVER_CHECKED", null, null));
        JobDiagnostic job = jdbc.query("""
                SELECT status, started_at, completed_at, summary_json FROM radar_job_runs
                WHERE job_type='offering-discovery' ORDER BY started_at DESC LIMIT 1
                """, rs -> rs.next()
                        ? new JobDiagnostic(rs.getString(1), timestamp(rs.getTimestamp(2)), timestamp(rs.getTimestamp(3)),
                                jsonInt(rs.getString(4), "processed"), jsonInt(rs.getString(4), "created"),
                                jsonInt(rs.getString(4), "updated"), jsonInt(rs.getString(4), "errorCount"))
                        : new JobDiagnostic("NEVER_RUN", null, null, 0, 0, 0, 0));
        Long duration = job.startedAt() == null || job.completedAt() == null ? null
                : java.time.Duration.between(job.startedAt(), job.completedAt()).toMillis();
        return new Diagnostics(total, confirmed, possible, source.status(), source.successAt(), source.error(),
                job.status(), job.startedAt(), job.completedAt(), duration, job.processed(), job.created(),
                job.updated(), job.errorCount());
    }

    private Optional<Offering> findExisting(Candidate candidate) {
        if (!blank(candidate.fileNumber())) {
            Optional<Offering> byFile = jdbc.query(SELECT + " WHERE o.issuer_cik=? AND o.sec_file_number=?",
                    offeringMapper(), candidate.issuerCik(), candidate.fileNumber()).stream().findFirst();
            if (byFile.isPresent()) return byFile;
        }
        Optional<Offering> byAccession = jdbc.query(SELECT + " WHERE o.sec_accession_number=?",
                offeringMapper(), candidate.accessionNumber()).stream().findFirst();
        if (byAccession.isPresent()) return byAccession;
        if (!blank(candidate.offeringUrl())) {
            return jdbc.query(SELECT + " WHERE o.offering_url=?", offeringMapper(), candidate.offeringUrl())
                    .stream().findFirst();
        }
        return Optional.empty();
    }

    private void recordChange(long companyId, boolean created, Status before, Status after, Candidate candidate) {
        String type = created ? "NEW_OFFERING" : lifecycleChange(candidate.filingType(), before, after);
        if (!created && type == null) return;
        String summary = created ? "SEC-filed Regulation Crowdfunding offering discovered."
                : "SEC-filed offering lifecycle changed to " + after.name().toLowerCase().replace('_', ' ') + ".";
        String significance = created || after == Status.WITHDRAWN || after == Status.TERMINATED ? "IMPORTANT" : "NOTABLE";
        jdbc.update("""
                INSERT INTO radar_company_changes (company_id, snapshot_id, change_type, significance, summary,
                  previous_value, current_value, why_it_matters, detected_at)
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?)
                """, companyId, type, significance, summary, before == null ? null : before.name(), after.name(),
                "An offering filing changes what can be evaluated; it does not indicate investment quality.", LocalDateTime.now());
    }

    private static String lifecycleChange(String form, Status before, Status after) {
        if (after == Status.WITHDRAWN) return "OFFERING_WITHDRAWN";
        if (after == Status.TERMINATED) return "OFFERING_TERMINATED";
        if (form != null && form.endsWith("/A")) return "OFFERING_AMENDED";
        if (before != after) return "OFFERING_STATUS_CHANGED";
        return null;
    }

    private static boolean isOlder(Candidate candidate, Offering current) {
        if ("PLATFORM_OFFERING".equals(current.provenance())) return false;
        int dateOrder = candidate.filingDate().compareTo(current.filingDate());
        return dateOrder < 0 || (dateOrder == 0
                && candidate.accessionNumber().compareTo(current.accessionNumber()) < 0);
    }

    private static Status status(Candidate candidate) {
        String form = candidate.filingType() == null ? "" : candidate.filingType().toUpperCase();
        if (form.equals("C-W")) return Status.WITHDRAWN;
        if (form.equals("C-TR")) return Status.TERMINATED;
        if (candidate.deadline() != null) return candidate.deadline().isBefore(LocalDate.now()) ? Status.ENDED : Status.ACTIVE;
        if (form.equals("C") || form.equals("C/A")) return Status.POSSIBLY_ACTIVE;
        return Status.UNKNOWN;
    }

    private void bind(PreparedStatement statement, Candidate c, Match match, Status status,
            LocalDateTime firstSeen, LocalDateTime now) throws java.sql.SQLException {
        Object[] values = { match.companyId(), c.issuerName(), normalize(c.issuerName()), c.issuerCik(), c.platform(),
                nullIfBlank(c.intermediaryName()), nullIfBlank(c.intermediaryCik()), nullIfBlank(c.offeringUrl()),
                c.secFilingUrl(), c.accessionNumber(), nullIfBlank(c.fileNumber()), c.filingType(), c.filingDate(),
                nullIfBlank(c.securityType()), c.minimumInvestment(), c.targetAmount(), c.maximumAmount(),
                nullIfBlank(c.valuationOrCap()), c.deadline(), c.amountRaised(), status.name(), c.source(),
                match.status().name(), match.confidence(), match.reason(), json(c.facts()), firstSeen, now, now, now };
        for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
    }

    private org.springframework.jdbc.core.RowMapper<Offering> offeringMapper() {
        return (rs, row) -> new Offering(rs.getLong("id"), (Long) rs.getObject("radar_company_id"),
                rs.getString("company_name"), rs.getString("issuer_name"), rs.getString("issuer_cik"),
                rs.getString("platform"), rs.getString("intermediary_name"), rs.getString("offering_url"),
                rs.getString("sec_filing_url"), rs.getString("sec_accession_number"), rs.getString("sec_file_number"),
                rs.getString("filing_type"), rs.getObject("filing_date", LocalDate.class),
                rs.getString("offering_exemption"), rs.getString("security_type"), rs.getBigDecimal("minimum_investment"),
                rs.getBigDecimal("target_amount"), rs.getBigDecimal("maximum_amount"), rs.getString("valuation_or_cap"),
                rs.getObject("deadline", LocalDate.class), rs.getBigDecimal("amount_raised"), Status.valueOf(rs.getString("status")),
                rs.getString("source"), MatchStatus.valueOf(rs.getString("match_status")), rs.getInt("match_confidence"),
                rs.getString("match_reason"), timestamp(rs.getTimestamp("first_seen_at")), timestamp(rs.getTimestamp("last_seen_at")),
                rs.getString("provenance"), rs.getString("reconciliation_status"), rs.getString("platform_status"));
    }

    private static void addFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (blank(value)) return;
        sql.append(" AND ").append(column).append(" = ?");
        args.add(value.trim().toUpperCase());
    }

    private long count(String sql) { Long value = jdbc.queryForObject(sql, Long.class); return value == null ? 0 : value; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalArgumentException(e); } }

    private int jsonInt(String value, String field) {
        if (value == null || value.isBlank()) return 0;
        try { return objectMapper.readTree(value).path(field).asInt(0); }
        catch (JsonProcessingException error) { return 0; }
    }

    private String issuerWebsite(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            JsonNode facts = objectMapper.readTree(value);
            for (var field : facts.properties()) {
                if ("issuerwebsite".equalsIgnoreCase(field.getKey())) {
                    String website = field.getValue().asText("").trim();
                    return website.isEmpty() ? null : website;
                }
            }
            return null;
        } catch (JsonProcessingException error) {
            return null;
        }
    }
    private Map<String, String> factsFromJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            JsonNode node = objectMapper.readTree(value);
            java.util.Map<String, String> facts = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            node.properties().forEach(entry -> facts.put(entry.getKey(), entry.getValue().asText("")));
            return Map.copyOf(facts);
        } catch (JsonProcessingException error) { return Map.of(); }
    }
    private static String normalize(String value) { return com.startupvalidationbot.radar.CompanyIdentity.normalizeName(value); }
    private static Object nullIfBlank(String value) { return blank(value) ? null : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static LocalDateTime timestamp(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public record UpsertResult(Offering offering, boolean created) { }
    public record StoredIdentity(long offeringId, String issuerName, String issuerWebsite) { }
    public record StoredCandidate(long offeringId, Candidate candidate) { }
    private record SourceDiagnostic(String status, LocalDateTime successAt, String error) { }
    private record JobDiagnostic(String status, LocalDateTime startedAt, LocalDateTime completedAt,
            int processed, int created, int updated, int errorCount) { }
}
