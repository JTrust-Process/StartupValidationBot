package com.startupvalidationbot.diligence.discovery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityDecision;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.PlatformDiagnostic;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.PlatformRun;

@Repository
public class CampaignDiscoveryStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CampaignDiscoveryStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public boolean cached(long companyId, String platform, String fingerprint, LocalDateTime now) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM radar_campaign_discovery_checks
                WHERE radar_company_id=? AND platform=? AND identity_fingerprint=? AND next_eligible_at>?
                """, Integer.class, companyId, platform, fingerprint, now);
        return count != null && count > 0;
    }

    @Transactional
    public synchronized void saveCheck(long companyId, Long offeringId, String platform, String fingerprint,
            String status, int requests, int candidates, String summary, String error, LocalDateTime nextEligibleAt) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE radar_campaign_discovery_checks SET offering_id=?,identity_fingerprint=?,status=?,
                  requests_made=?,candidates_found=?,result_summary=?,last_error=?,checked_at=?,next_eligible_at=?
                WHERE radar_company_id=? AND platform=?
                """, offeringId, fingerprint, status, requests, candidates, bounded(summary, 1000),
                bounded(error, 2000), now, nextEligibleAt, companyId, platform);
        if (updated == 0) jdbc.update("""
                INSERT INTO radar_campaign_discovery_checks
                  (radar_company_id,offering_id,platform,identity_fingerprint,status,requests_made,
                   candidates_found,result_summary,last_error,checked_at,next_eligible_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, companyId, offeringId, platform, fingerprint, status, requests, candidates,
                bounded(summary, 1000), bounded(error, 2000), now, nextEligibleAt);
    }

    @Transactional
    public synchronized void saveCandidate(long companyId, Long offeringId, CampaignCandidate candidate,
            IdentityDecision decision) {
        LocalDateTime now = LocalDateTime.now();
        int confidence = Math.min(candidate.discoveryConfidence(), decision.confidence());
        int updated = jdbc.update("""
                UPDATE radar_campaign_discovery_results SET offering_id=?,issuer_name=?,issuer_domain=?,
                  platform_identifier=?,evidence_source=?,discovery_confidence=?,identity_status=?,
                  identity_reason=?,metadata_json=?,last_seen_at=?
                WHERE radar_company_id=? AND platform=? AND campaign_url=?
                """, offeringId, bounded(candidate.issuerName(), 300), bounded(candidate.issuerDomain(), 300),
                bounded(candidate.platformIdentifier(), 300), bounded(candidate.evidenceSource(), 1200),
                confidence, decision.status().name(), bounded(decision.reason(), 1200),
                write(boundedMetadata(candidate.metadata())), now, companyId, candidate.platform(),
                candidate.campaignUrl());
        if (updated == 0) jdbc.update("""
                INSERT INTO radar_campaign_discovery_results
                  (radar_company_id,offering_id,platform,campaign_url,issuer_name,issuer_domain,
                   platform_identifier,evidence_source,discovery_confidence,identity_status,
                   identity_reason,metadata_json,first_seen_at,last_seen_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, companyId, offeringId, candidate.platform(), candidate.campaignUrl(),
                bounded(candidate.issuerName(), 300), bounded(candidate.issuerDomain(), 300),
                bounded(candidate.platformIdentifier(), 300), bounded(candidate.evidenceSource(), 1200),
                confidence, decision.status().name(), bounded(decision.reason(), 1200),
                write(boundedMetadata(candidate.metadata())), now, now);
    }

    @Transactional
    public synchronized void markPlatform(PlatformRun run) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime success = "OK".equals(run.status()) || run.resolved() > 0 ? now : null;
        LocalDateTime failure = run.error() == null ? null : now;
        int updated = jdbc.update("""
                UPDATE radar_campaign_discovery_platform_state SET discovery_capability=?,last_checked_at=?,
                  last_success_at=COALESCE(?,last_success_at),last_failure_at=COALESCE(?,last_failure_at),
                  last_status=?,requests_made=?,candidates_found=?,campaigns_resolved=?,last_error=?,updated_at=?
                WHERE platform=?
                """, run.capability(), now, success, failure, run.status(), run.requests(), run.candidates(),
                run.resolved(), bounded(run.error(), 2000), now, run.platform());
        if (updated == 0) jdbc.update("""
                INSERT INTO radar_campaign_discovery_platform_state
                  (platform,discovery_capability,last_checked_at,last_success_at,last_failure_at,last_status,
                   requests_made,candidates_found,campaigns_resolved,last_error,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, run.platform(), run.capability(), now, success, failure, run.status(), run.requests(),
                run.candidates(), run.resolved(), bounded(run.error(), 2000), now);
    }

    public List<PlatformDiagnostic> platformDiagnostics() {
        return jdbc.query("""
                SELECT platform,discovery_capability,last_checked_at,last_success_at,last_failure_at,
                  last_status,requests_made,candidates_found,campaigns_resolved,last_error
                FROM radar_campaign_discovery_platform_state ORDER BY platform
                """, (rs, row) -> new PlatformDiagnostic(rs.getString(1), rs.getString(2),
                time(rs.getTimestamp(3)), time(rs.getTimestamp(4)), time(rs.getTimestamp(5)),
                rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getString(10)));
    }

    private Map<String, String> boundedMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Map.of();
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        source.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .limit(20).forEach(entry -> values.put(
                        bounded(entry.getKey(), 120), bounded(entry.getValue(), 500)));
        return Map.copyOf(values);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalArgumentException(error); }
    }

    private static String bounded(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }

    private static LocalDateTime time(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
