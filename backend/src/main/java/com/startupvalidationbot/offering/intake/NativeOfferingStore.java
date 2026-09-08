package com.startupvalidationbot.offering.intake;

import static com.startupvalidationbot.offering.intake.NativeOfferingDomain.*;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.radar.ContentHash;

@Repository
public class NativeOfferingStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public NativeOfferingStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public boolean saveCandidate(NativeOfferingCandidate candidate, ReconciliationStatus reconciliation,
            String identityStatus, int identityConfidence, boolean actionable) {
        LocalDateTime now = LocalDateTime.now();
        String fingerprint = ContentHash.sha256(candidate.source() + "|" + candidate.platform() + "|"
                + candidate.externalId() + "|" + candidate.status() + "|" + candidate.sourceEvidence());
        int updated = jdbc.update("""
                UPDATE radar_native_offering_candidates SET platform=?,company_name=?,issuer_domain=?,
                  issuer_website=?,campaign_url=?,platform_status=?,offering_exemption=?,intermediary_name=?,
                  security_type=?,amount_raised=?,target_amount=?,maximum_amount=?,valuation_or_cap=?,
                  minimum_investment=?,price_per_share=?,deadline=?,category=?,description=?,issuer_cik=?,
                  sec_accession_number=?,sec_file_number=?,sec_filing_url=?,filing_type=?,filing_date=?,
                  reconciliation_status=?,identity_status=?,identity_confidence=?,actionable=?,
                  source_evidence_json=?,source_fingerprint=?,last_seen_at=?,updated_at=?
                WHERE source=? AND external_id=?
                """, candidate.platform(), candidate.companyName(), candidate.issuerDomain(),
                candidate.issuerWebsite(), candidate.canonicalUrl(), candidate.status().name(),
                value(candidate.exemption(), "UNKNOWN"), candidate.intermediaryName(), candidate.securityType(),
                candidate.amountRaised(), candidate.targetAmount(), candidate.maximumAmount(),
                candidate.valuationOrCap(), candidate.minimumInvestment(), candidate.pricePerShare(),
                candidate.deadline(), candidate.category(), bounded(candidate.description(), 2000),
                candidate.issuerCik(), candidate.secAccessionNumber(), candidate.secFileNumber(),
                candidate.secFilingUrl(), candidate.filingType(), candidate.filingDate(), reconciliation.name(),
                identityStatus, identityConfidence, actionable, write(candidate.sourceEvidence()), fingerprint,
                now, now, candidate.source(), candidate.externalId());
        if (updated > 0) return false;
        jdbc.update("""
                INSERT INTO radar_native_offering_candidates (source,platform,external_id,company_name,
                  issuer_domain,issuer_website,campaign_url,platform_status,offering_exemption,
                  intermediary_name,security_type,amount_raised,target_amount,maximum_amount,valuation_or_cap,
                  minimum_investment,price_per_share,deadline,category,description,issuer_cik,
                  sec_accession_number,sec_file_number,sec_filing_url,filing_type,filing_date,
                  reconciliation_status,identity_status,identity_confidence,actionable,source_evidence_json,
                  source_fingerprint,first_seen_at,last_seen_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, candidate.source(), candidate.platform(), candidate.externalId(), candidate.companyName(),
                candidate.issuerDomain(), candidate.issuerWebsite(), candidate.canonicalUrl(), candidate.status().name(),
                value(candidate.exemption(), "UNKNOWN"), candidate.intermediaryName(), candidate.securityType(),
                candidate.amountRaised(), candidate.targetAmount(), candidate.maximumAmount(),
                candidate.valuationOrCap(), candidate.minimumInvestment(), candidate.pricePerShare(),
                candidate.deadline(), candidate.category(), bounded(candidate.description(), 2000),
                candidate.issuerCik(), candidate.secAccessionNumber(), candidate.secFileNumber(),
                candidate.secFilingUrl(), candidate.filingType(), candidate.filingDate(), reconciliation.name(),
                identityStatus, identityConfidence, actionable, write(candidate.sourceEvidence()), fingerprint,
                now, now, now, now);
        return true;
    }

    public Optional<Long> findOfferingId(NativeOfferingCandidate candidate) {
        if (!blank(candidate.secAccessionNumber())) {
            Optional<Long> value = first("SELECT id FROM radar_offerings WHERE sec_accession_number=?",
                    candidate.secAccessionNumber());
            if (value.isPresent()) return value;
        }
        if (!blank(candidate.issuerCik()) && !blank(candidate.secFileNumber())) {
            Optional<Long> value = first("SELECT id FROM radar_offerings WHERE issuer_cik=? AND sec_file_number=?",
                    candidate.issuerCik(), candidate.secFileNumber());
            if (value.isPresent()) return value;
        }
        if (!blank(candidate.canonicalUrl())) {
            Optional<Long> value = first("""
                    SELECT id FROM radar_offerings WHERE offering_url=?
                    UNION SELECT offering_id FROM radar_platform_campaigns WHERE campaign_url=? LIMIT 1
                    """, candidate.canonicalUrl(), candidate.canonicalUrl());
            if (value.isPresent()) return value;
        }
        return first("SELECT id FROM radar_offerings WHERE native_candidate_key=?",
                nativeKey(candidate));
    }

    @Transactional
    public PlatformUpsert upsertPlatformOffering(NativeOfferingCandidate candidate, Match match,
            ReconciliationStatus reconciliation) {
        Optional<Long> existing = findOfferingId(candidate);
        LocalDateTime now = LocalDateTime.now();
        String mappedStatus = mapStatus(candidate.status());
        String evidence = write(candidate.sourceEvidence());
        if (existing.isPresent()) {
            long id = existing.orElseThrow();
            jdbc.update("""
                    UPDATE radar_offerings SET radar_company_id=COALESCE(?,radar_company_id),
                      issuer_name=?,issuer_name_normalized=?,platform=?,intermediary_name=COALESCE(?,intermediary_name),
                      offering_url=COALESCE(?,offering_url),offering_exemption=?,
                      security_type=COALESCE(?,security_type),minimum_investment=COALESCE(?,minimum_investment),
                      target_amount=COALESCE(?,target_amount),maximum_amount=COALESCE(?,maximum_amount),
                      valuation_or_cap=COALESCE(?,valuation_or_cap),deadline=COALESCE(?,deadline),
                      amount_raised=COALESCE(?,amount_raised),
                      status=CASE WHEN provenance='SEC_EDGAR' THEN status ELSE ? END,
                      source=CASE WHEN provenance='SEC_EDGAR' THEN source ELSE ? END,
                      match_status=?,match_confidence=?,match_reason=?,
                      raw_facts_json=CASE WHEN provenance='SEC_EDGAR' THEN raw_facts_json ELSE ? END,
                      provenance=CASE WHEN provenance='SEC_EDGAR' THEN provenance ELSE 'PLATFORM_OFFERING' END,
                      reconciliation_status=?,platform_status=?,native_candidate_key=COALESCE(native_candidate_key,?),
                      last_seen_at=?,updated_at=? WHERE id=?
                    """, match.companyId(), candidate.companyName(), normalize(candidate.companyName()),
                    candidate.platform(), candidate.intermediaryName(), candidate.canonicalUrl(),
                    value(candidate.exemption(), "UNKNOWN"), candidate.securityType(), candidate.minimumInvestment(),
                    candidate.targetAmount(), candidate.maximumAmount(), candidate.valuationOrCap(),
                    candidate.deadline(), candidate.amountRaised(), mappedStatus, candidate.source(),
                    match.status().name(), match.confidence(), match.reason(), evidence, reconciliation.name(),
                    candidate.status().name(), nativeKey(candidate), now, now, id);
            return new PlatformUpsert(id, false);
        }

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO radar_offerings (radar_company_id,issuer_name,issuer_name_normalized,issuer_cik,
                      platform,intermediary_name,intermediary_cik,offering_url,sec_filing_url,
                      sec_accession_number,sec_file_number,filing_type,filing_date,offering_exemption,
                      security_type,minimum_investment,target_amount,maximum_amount,valuation_or_cap,deadline,
                      amount_raised,status,source,match_status,match_confidence,match_reason,raw_facts_json,
                      first_seen_at,last_seen_at,created_at,updated_at,provenance,reconciliation_status,
                      platform_status,native_candidate_key)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, new String[] { "id" });
            Object[] values = { match.companyId(), candidate.companyName(), normalize(candidate.companyName()),
                    candidate.issuerCik(), candidate.platform(), candidate.intermediaryName(), null,
                    candidate.canonicalUrl(), candidate.secFilingUrl(), candidate.secAccessionNumber(),
                    candidate.secFileNumber(), value(candidate.filingType(), "PLATFORM"),
                    candidate.filingDate() == null ? candidate.retrievedAt().toLocalDate() : candidate.filingDate(),
                    value(candidate.exemption(), "UNKNOWN"), candidate.securityType(), candidate.minimumInvestment(),
                    candidate.targetAmount(), candidate.maximumAmount(), candidate.valuationOrCap(),
                    candidate.deadline(), candidate.amountRaised(), mappedStatus, candidate.source(),
                    match.status().name(), match.confidence(), match.reason(), evidence, now, now, now, now,
                    "PLATFORM_OFFERING", reconciliation.name(), candidate.status().name(), nativeKey(candidate) };
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return new PlatformUpsert(keys.getKey().longValue(), true);
    }

    @Transactional
    public void markSource(SourceDiagnostic value) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime success = "OK".equals(value.status()) || "DEGRADED".equals(value.status()) ? now : null;
        int updated = jdbc.update("""
                UPDATE radar_native_offering_source_state SET capability=?,last_checked_at=?,
                  last_success_at=COALESCE(?,last_success_at),last_status=?,directory_fetched=?,requests_made=?,
                  detail_requests=?,candidates_found=?,active_candidates=?,offerings_inserted=?,
                  companies_matched=?,candidates_rejected=?,last_error=?,updated_at=? WHERE source=?
                """, value.capability(), now, success, value.status(), value.directoryFetched(), value.requests(),
                value.detailRequests(), value.candidates(), value.activeCandidates(), value.inserted(),
                value.matched(), value.rejected(), bounded(value.error(), 2000), now, value.source());
        if (updated == 0) jdbc.update("""
                INSERT INTO radar_native_offering_source_state (source,capability,last_checked_at,last_success_at,
                  last_status,directory_fetched,requests_made,detail_requests,candidates_found,active_candidates,
                  offerings_inserted,companies_matched,candidates_rejected,last_error,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, value.source(), value.capability(), now, success, value.status(), value.directoryFetched(),
                value.requests(), value.detailRequests(), value.candidates(), value.activeCandidates(),
                value.inserted(), value.matched(), value.rejected(), bounded(value.error(), 2000), now);
    }

    public List<SourceDiagnostic> diagnostics() {
        return jdbc.query("""
                SELECT * FROM radar_native_offering_source_state ORDER BY source
                """, (rs, row) -> new SourceDiagnostic(rs.getString("source"), rs.getString("capability"),
                rs.getString("last_status"), rs.getTimestamp("last_checked_at").toLocalDateTime(),
                rs.getBoolean("directory_fetched"), rs.getInt("requests_made"),
                rs.getInt("detail_requests"), rs.getInt("candidates_found"),
                rs.getInt("active_candidates"), rs.getInt("offerings_inserted"),
                rs.getInt("companies_matched"), rs.getInt("candidates_rejected"),
                rs.getString("last_error")));
    }

    public int actionableCandidateCount() {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM radar_native_offering_candidates WHERE actionable=true",
                Integer.class);
        return value == null ? 0 : value;
    }

    private Optional<Long> first(String sql, Object... args) {
        return jdbc.queryForList(sql, Long.class, args).stream().findFirst();
    }

    private String write(Map<String, String> value) {
        try { return json.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not serialize native offering evidence", error); }
    }

    private static String nativeKey(NativeOfferingCandidate candidate) {
        return candidate.source() + "|" + candidate.platform() + "|" + candidate.externalId();
    }

    private static String mapStatus(Status status) {
        return switch (status) {
            case ACTIVE, RESERVATION, CLOSING_SOON -> "ACTIVE";
            case CLOSED -> "ENDED";
            case WITHDRAWN -> "WITHDRAWN";
            case TERMINATED -> "TERMINATED";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static String normalize(String value) {
        return com.startupvalidationbot.radar.CompanyIdentity.normalizeName(value);
    }

    private static String value(String value, String fallback) { return blank(value) ? fallback : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String bounded(String value, int max) {
        return value == null ? null : value.substring(0, Math.min(max, value.length()));
    }

    public record PlatformUpsert(long offeringId, boolean created) { }
}
