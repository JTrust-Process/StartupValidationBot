package com.startupvalidationbot.diligence;

import static com.startupvalidationbot.diligence.DiligenceDomain.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.startupvalidationbot.radar.ContentHash;

@Repository
public class DiligenceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DiligenceStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public List<Long> eligibleOfferingIds(int limit) {
        return jdbc.queryForList("""
                SELECT o.id FROM radar_offerings o
                LEFT JOIN radar_watchlist w ON w.company_id=o.radar_company_id
                WHERE o.radar_company_id IS NOT NULL AND (
                  o.match_status='CONFIRMED' OR
                  (o.match_status='LIKELY' AND o.match_confidence >= 75) OR
                  w.company_id IS NOT NULL)
                ORDER BY CASE o.match_status WHEN 'CONFIRMED' THEN 0 ELSE 1 END,
                  o.updated_at DESC LIMIT ?
                """, Long.class, Math.max(1, Math.min(limit, 100)));
    }

    @Transactional
    public synchronized PlatformCampaign upsertCampaign(PlatformCampaign campaign) {
        lockOffering(campaign.offeringId());
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE radar_platform_campaigns SET offering_id=?, campaign_url_source=?,
                  campaign_url_confidence=?, campaign_status=?, issuer_name=?, security_type=?,
                  minimum_investment=?, price_per_share=?, valuation=?, valuation_cap=?, discount_percent=?,
                  target_amount=?, maximum_amount=?, amount_raised=?, investor_count=?, deadline=?, headline=?,
                  facts_json=?, source_fingerprint=?, last_checked_at=?, last_verified_at=?, updated_at=?
                WHERE platform=? AND campaign_url=?
                """, campaign.offeringId(), campaign.campaignUrlSource(), campaign.campaignUrlConfidence(),
                campaign.status().name(), campaign.issuerName(), campaign.securityType(), campaign.minimumInvestment(),
                campaign.pricePerShare(), campaign.valuation(), campaign.valuationCap(), campaign.discountPercent(),
                campaign.targetAmount(), campaign.maximumAmount(), campaign.amountRaised(), campaign.investorCount(),
                campaign.deadline(), campaign.headline(), write(campaign.facts()), campaign.sourceFingerprint(),
                campaign.lastCheckedAt(), campaign.lastVerifiedAt(), now, campaign.platform(), campaign.campaignUrl());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO radar_platform_campaigns (offering_id, platform, campaign_url,
                      campaign_url_source, campaign_url_confidence, campaign_status, issuer_name, security_type,
                      minimum_investment, price_per_share, valuation, valuation_cap, discount_percent,
                      target_amount, maximum_amount, amount_raised, investor_count, deadline, headline,
                      facts_json, source_fingerprint, last_checked_at, last_verified_at, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, campaign.offeringId(), campaign.platform(), campaign.campaignUrl(),
                    campaign.campaignUrlSource(), campaign.campaignUrlConfidence(), campaign.status().name(),
                    campaign.issuerName(), campaign.securityType(), campaign.minimumInvestment(),
                    campaign.pricePerShare(), campaign.valuation(), campaign.valuationCap(), campaign.discountPercent(),
                    campaign.targetAmount(), campaign.maximumAmount(), campaign.amountRaised(), campaign.investorCount(),
                    campaign.deadline(), campaign.headline(), write(campaign.facts()), campaign.sourceFingerprint(),
                    campaign.lastCheckedAt(), campaign.lastVerifiedAt(), now, now);
        }
        return findCampaign(campaign.offeringId()).orElseThrow();
    }

    public Optional<PlatformCampaign> findCampaign(long offeringId) {
        return jdbc.query("""
                SELECT * FROM radar_platform_campaigns WHERE offering_id=?
                ORDER BY last_verified_at DESC, id DESC LIMIT 1
                """, (rs, row) -> new PlatformCampaign(rs.getLong("id"), rs.getLong("offering_id"),
                rs.getString("platform"), rs.getString("campaign_url"), rs.getString("campaign_url_source"),
                rs.getInt("campaign_url_confidence"), CampaignStatus.valueOf(rs.getString("campaign_status")),
                rs.getString("issuer_name"), rs.getString("security_type"), rs.getBigDecimal("minimum_investment"),
                rs.getBigDecimal("price_per_share"), rs.getBigDecimal("valuation"), rs.getBigDecimal("valuation_cap"),
                rs.getBigDecimal("discount_percent"), rs.getBigDecimal("target_amount"), rs.getBigDecimal("maximum_amount"),
                rs.getBigDecimal("amount_raised"), (Integer) rs.getObject("investor_count"),
                rs.getObject("deadline", LocalDate.class), rs.getString("headline"),
                readMap(rs.getString("facts_json")), rs.getString("source_fingerprint"),
                time(rs.getTimestamp("last_checked_at")), time(rs.getTimestamp("last_verified_at"))), offeringId)
                .stream().findFirst();
    }

    @Transactional
    public synchronized Packet savePacket(PacketDraft draft) {
        lockOffering(draft.offeringId());
        LocalDateTime now = LocalDateTime.now();
        Long id = jdbc.query("SELECT id FROM radar_diligence_packets WHERE offering_id=?",
                rs -> rs.next() ? rs.getLong(1) : null, draft.offeringId());
        if (id == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO radar_diligence_packets (radar_company_id, offering_id, status,
                          identity_status, completeness, confidence, summary, bull_case, bear_case,
                          key_risks, unanswered_questions, material_discrepancies, next_monitoring_milestones,
                          sources_checked, data_not_found, source_fingerprint, generated_at, last_refreshed_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, new String[] { "id" });
                Object[] values = packetValues(draft, now, now);
                for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                return statement;
            }, keys);
            id = keys.getKey().longValue();
        } else {
            updatePacket(id, draft, now);
        }
        syncEvidence(id, draft.evidence());
        syncFinancials(id, draft.offeringId(), draft.financials());
        return find(id).orElseThrow();
    }

    private void lockOffering(long offeringId) {
        jdbc.queryForObject("SELECT id FROM radar_offerings WHERE id=? FOR UPDATE", Long.class, offeringId);
    }

    private void updatePacket(long id, PacketDraft draft, LocalDateTime now) {
        jdbc.update("""
                UPDATE radar_diligence_packets SET radar_company_id=?, status=?, identity_status=?,
                  completeness=?, confidence=?, summary=?, bull_case=?, bear_case=?, key_risks=?,
                  unanswered_questions=?, material_discrepancies=?, next_monitoring_milestones=?,
                  sources_checked=?, data_not_found=?, source_fingerprint=?, last_refreshed_at=?
                WHERE id=?
                """, draft.companyId(), draft.status().name(), draft.identityStatus(), draft.completeness(),
                draft.confidence(), draft.summary(), write(draft.bullCase()), write(draft.bearCase()),
                write(draft.keyRisks()), write(draft.unansweredQuestions()), write(draft.discrepancies()),
                write(draft.milestones()), write(draft.sourcesChecked()), write(draft.dataNotFound()),
                draft.sourceFingerprint(), now, id);
    }

    private Object[] packetValues(PacketDraft d, LocalDateTime generated, LocalDateTime refreshed) {
        return new Object[] { d.companyId(), d.offeringId(), d.status().name(), d.identityStatus(), d.completeness(),
                d.confidence(), d.summary(), write(d.bullCase()), write(d.bearCase()), write(d.keyRisks()),
                write(d.unansweredQuestions()), write(d.discrepancies()), write(d.milestones()),
                write(d.sourcesChecked()), write(d.dataNotFound()), d.sourceFingerprint(), generated, refreshed };
    }

    private void syncEvidence(long packetId, List<EvidenceDraft> evidence) {
        for (EvidenceDraft item : evidence) {
            String fingerprint = ContentHash.sha256(item.classification() + "|" + item.sourceUrl() + "|"
                    + item.factKey() + "|" + item.period() + "|" + item.factValue());
            jdbc.update("""
                    INSERT INTO radar_diligence_evidence (packet_id, source_type, source_url, source_title,
                      fact_key, fact_value, period, classification, observed_at, confidence, raw_excerpt,
                      metadata_json, evidence_fingerprint)
                    SELECT ?,?,?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (
                      SELECT 1 FROM radar_diligence_evidence WHERE packet_id=? AND evidence_fingerprint=?)
                    """, packetId, item.sourceType(), item.sourceUrl(), item.sourceTitle(), item.factKey(),
                    item.factValue(), item.period(), item.classification().name(), LocalDateTime.now(),
                    item.confidence(), bounded(item.rawExcerpt(), 2000), write(item.metadata()), fingerprint,
                    packetId, fingerprint);
        }
    }

    private void syncFinancials(long packetId, long offeringId, List<FinancialPeriod> periods) {
        for (FinancialPeriod period : periods) {
            int updated = jdbc.update("""
                    UPDATE radar_diligence_financials SET revenue=?, cost_of_goods=?, net_income=?, cash=?,
                      assets=?, liabilities=?, short_term_debt=?, long_term_debt=?, taxes_paid=?,
                      source_accession_number=?, source_url=?, updated_at=? WHERE packet_id=? AND period=?
                    """, period.revenue(), period.costOfGoods(), period.netIncome(), period.cash(), period.assets(),
                    period.liabilities(), period.shortTermDebt(), period.longTermDebt(), period.taxesPaid(),
                    period.sourceAccessionNumber(), period.sourceUrl(), LocalDateTime.now(), packetId, period.period());
            if (updated == 0) jdbc.update("""
                    INSERT INTO radar_diligence_financials (packet_id, offering_id, period, revenue,
                      cost_of_goods, net_income, cash, assets, liabilities, short_term_debt, long_term_debt,
                      taxes_paid, source_accession_number, source_url, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, packetId, offeringId, period.period(), period.revenue(), period.costOfGoods(),
                    period.netIncome(), period.cash(), period.assets(), period.liabilities(), period.shortTermDebt(),
                    period.longTermDebt(), period.taxesPaid(), period.sourceAccessionNumber(), period.sourceUrl(),
                    LocalDateTime.now(), LocalDateTime.now());
        }
    }

    public List<Packet> list(String status) {
        String sql = packetSelect() + (status == null || status.isBlank() ? "" : " WHERE p.status=?")
                + " ORDER BY p.last_refreshed_at DESC";
        List<PacketRow> rows = status == null || status.isBlank()
                ? jdbc.query(sql, packetRowMapper()) : jdbc.query(sql, packetRowMapper(), status.toUpperCase());
        return rows.stream().map(this::packet).toList();
    }

    public Optional<Packet> find(long id) {
        return jdbc.query(packetSelect() + " WHERE p.id=?", packetRowMapper(), id).stream().findFirst().map(this::packet);
    }

    public Optional<Packet> findByOffering(long offeringId) {
        return jdbc.query(packetSelect() + " WHERE p.offering_id=?", packetRowMapper(), offeringId).stream()
                .findFirst().map(this::packet);
    }

    @Transactional
    public synchronized void availability(long companyId, String sourceType, String status, String summary, String question) {
        int updated = jdbc.update("""
                UPDATE radar_investment_availability_checks SET status=?, result_summary=?,
                  unresolved_question=?, checked_at=? WHERE radar_company_id=? AND source_type=?
                """, status, summary, question, LocalDateTime.now(), companyId, sourceType);
        if (updated == 0) jdbc.update("""
                INSERT INTO radar_investment_availability_checks
                  (radar_company_id, source_type, status, result_summary, unresolved_question, checked_at)
                VALUES (?,?,?,?,?,?)
                """, companyId, sourceType, status, summary, question, LocalDateTime.now());
    }

    public CompanyAvailability availability(long companyId) {
        List<AvailabilityCheck> checks = jdbc.query("""
                SELECT source_type,status,result_summary,unresolved_question,checked_at
                FROM radar_investment_availability_checks WHERE radar_company_id=? ORDER BY source_type
                """, (rs, row) -> new AvailabilityCheck(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), time(rs.getTimestamp(5))), companyId);
        LocalDateTime latest = checks.stream().map(AvailabilityCheck::checkedAt).max(LocalDateTime::compareTo).orElse(null);
        return new CompanyAvailability(companyId, latest, checks);
    }

    public synchronized void markPlatform(String platform, String status, int requests, int found, String error) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update("""
                UPDATE radar_platform_source_state SET last_checked_at=?,last_status=?,requests_made=?,
                  campaigns_found=?,last_error=?,updated_at=? WHERE platform=?
                """, now, status, requests, found, bounded(error, 2000), now, platform);
        if (updated == 0) jdbc.update("""
                INSERT INTO radar_platform_source_state(platform,last_checked_at,last_status,requests_made,
                  campaigns_found,last_error,updated_at) VALUES (?,?,?,?,?,?,?)
                """, platform, now, status, requests, found, bounded(error, 2000), now);
    }

    public boolean queueNotification(String eventType, String entityType, long entityId, String fingerprint,
            String recipient, String subject, String text, String html) {
        try {
            return jdbc.update("""
                    INSERT INTO radar_notification_events (event_type,entity_type,entity_id,fingerprint,recipient,
                      status,subject,text_body,html_body,created_at,updated_at)
                    VALUES (?,?,?,?,?,'PENDING',?,?,?,?,?)
                    """, eventType, entityType, entityId, fingerprint, recipient, subject, text, html,
                    LocalDateTime.now(), LocalDateTime.now()) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public List<NotificationEvent> pendingNotifications(int limit) {
        return jdbc.query("""
                SELECT id,recipient,subject,text_body,html_body,attempt_count FROM radar_notification_events
                WHERE status IN ('PENDING','FAILED') AND attempt_count < 3 ORDER BY created_at LIMIT ?
                """, (rs, row) -> new NotificationEvent(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getInt(6)), Math.max(1, Math.min(limit, 20)));
    }

    public void notificationSent(long id, String messageId) {
        jdbc.update("""
                UPDATE radar_notification_events SET status='SENT',resend_message_id=?,attempt_count=attempt_count+1,
                  last_error=NULL,sent_at=?,updated_at=? WHERE id=?
                """, messageId, LocalDateTime.now(), LocalDateTime.now(), id);
    }

    public void notificationFailed(long id, String error) {
        jdbc.update("""
                UPDATE radar_notification_events SET status='FAILED',attempt_count=attempt_count+1,last_error=?,
                  updated_at=? WHERE id=?
                """, bounded(error, 2000), LocalDateTime.now(), id);
    }

    public Diagnostics diagnostics(boolean resendConfigured) {
        JobSummary job = jdbc.query("""
                SELECT status,started_at,completed_at,summary_json FROM radar_job_runs
                WHERE job_type='autonomous-diligence' ORDER BY started_at DESC LIMIT 1
                """, rs -> rs.next() ? new JobSummary(rs.getString(1), time(rs.getTimestamp(2)),
                time(rs.getTimestamp(3)), rs.getString(4)) : new JobSummary("NEVER_RUN", null, null, "{}"));
        List<PlatformDiagnostic> platforms = jdbc.query("""
                SELECT platform,last_checked_at,last_status,requests_made,campaigns_found,last_error
                FROM radar_platform_source_state ORDER BY platform
                """, (rs, row) -> new PlatformDiagnostic(rs.getString(1), time(rs.getTimestamp(2)), rs.getString(3),
                rs.getInt(4), rs.getInt(5), rs.getString(6)));
        NotificationDiagnostic notification = jdbc.query("""
                SELECT status,resend_message_id,last_error FROM radar_notification_events ORDER BY updated_at DESC LIMIT 1
                """, rs -> rs.next() ? new NotificationDiagnostic(resendConfigured, rs.getString(1), rs.getString(2), rs.getString(3))
                        : new NotificationDiagnostic(resendConfigured, "NEVER_SENT", null, null));
        Long duration = job.startedAt == null || job.completedAt == null ? null
                : java.time.Duration.between(job.startedAt, job.completedAt).toMillis();
        return new Diagnostics(job.status, job.startedAt, job.completedAt, duration,
                jobInt(job.json,"companiesConsidered"), jobInt(job.json,"offeringsConsidered"),
                jobInt(job.json,"identitiesResolved"), jobInt(job.json,"campaignsResolved"),
                jobInt(job.json,"packetsReady"), jobInt(job.json,"packetsPartial"),
                jobInt(job.json,"needsReview"), jobInt(job.json,"platformErrors"),
                jobInt(job.json,"aiFallbacks"), jobInt(job.json,"emailsQueued"),
                jobInt(job.json,"emailsSent"), jobInt(job.json,"emailsFailed"), platforms, notification);
    }

    private Packet packet(PacketRow row) {
        return new Packet(row.id, row.companyId, row.companyName, row.offeringId, row.platform,
                row.campaignUrl, row.secFilingUrl, PacketStatus.valueOf(row.status), row.identityStatus,
                row.completeness, row.confidence, row.summary, readList(row.bullCase), readList(row.bearCase),
                readList(row.keyRisks), readList(row.questions), readList(row.discrepancies), readList(row.milestones),
                readList(row.sources), readList(row.dataNotFound), row.generatedAt, row.refreshedAt, row.reviewedAt,
                row.securityType, row.minimum, row.target, row.maximum, row.raised, row.valuationOrCap, row.deadline,
                evidence(row.id), financials(row.id));
    }

    private List<Evidence> evidence(long packetId) {
        return jdbc.query("""
                SELECT * FROM radar_diligence_evidence WHERE packet_id=? ORDER BY classification,fact_key,period
                """, (rs, row) -> new Evidence(rs.getLong("id"), rs.getString("source_type"),
                rs.getString("source_url"), rs.getString("source_title"), rs.getString("fact_key"),
                rs.getString("fact_value"), rs.getString("period"),
                EvidenceClassification.valueOf(rs.getString("classification")), time(rs.getTimestamp("observed_at")),
                rs.getInt("confidence"), rs.getString("raw_excerpt"), readObjectMap(rs.getString("metadata_json"))), packetId);
    }

    private List<FinancialPeriod> financials(long packetId) {
        return jdbc.query("""
                SELECT * FROM radar_diligence_financials WHERE packet_id=? ORDER BY period DESC
                """, (rs, row) -> new FinancialPeriod(rs.getString("period"), rs.getBigDecimal("revenue"),
                rs.getBigDecimal("cost_of_goods"), rs.getBigDecimal("net_income"), rs.getBigDecimal("cash"),
                rs.getBigDecimal("assets"), rs.getBigDecimal("liabilities"), rs.getBigDecimal("short_term_debt"),
                rs.getBigDecimal("long_term_debt"), rs.getBigDecimal("taxes_paid"),
                rs.getString("source_accession_number"), rs.getString("source_url")), packetId);
    }

    private String packetSelect() { return """
            SELECT p.*,c.name company_name,o.platform,o.offering_url,o.sec_filing_url,o.security_type,
              o.minimum_investment,o.target_amount,o.maximum_amount,o.amount_raised,o.valuation_or_cap,o.deadline,
              pc.campaign_url
            FROM radar_diligence_packets p JOIN radar_companies c ON c.id=p.radar_company_id
            JOIN radar_offerings o ON o.id=p.offering_id LEFT JOIN radar_platform_campaigns pc ON pc.id=(
              SELECT x.id FROM radar_platform_campaigns x WHERE x.offering_id=o.id
              ORDER BY x.last_verified_at DESC,x.id DESC LIMIT 1)
            """; }

    private org.springframework.jdbc.core.RowMapper<PacketRow> packetRowMapper() {
        return (rs, row) -> new PacketRow(rs.getLong("id"), rs.getLong("radar_company_id"), rs.getString("company_name"),
                rs.getLong("offering_id"), rs.getString("platform"), rs.getString("campaign_url"),
                rs.getString("sec_filing_url"), rs.getString("status"), rs.getString("identity_status"),
                rs.getInt("completeness"), rs.getInt("confidence"), rs.getString("summary"), rs.getString("bull_case"),
                rs.getString("bear_case"), rs.getString("key_risks"), rs.getString("unanswered_questions"),
                rs.getString("material_discrepancies"), rs.getString("next_monitoring_milestones"),
                rs.getString("sources_checked"), rs.getString("data_not_found"), time(rs.getTimestamp("generated_at")),
                time(rs.getTimestamp("last_refreshed_at")), time(rs.getTimestamp("reviewed_at")),
                rs.getString("security_type"), rs.getBigDecimal("minimum_investment"), rs.getBigDecimal("target_amount"),
                rs.getBigDecimal("maximum_amount"), rs.getBigDecimal("amount_raised"), rs.getString("valuation_or_cap"),
                rs.getObject("deadline", LocalDate.class));
    }

    private int jobInt(String value, String field) {
        try {
            JsonNode root = json.readTree(value);
            if (root.has(field)) return root.path(field).asInt(0);
            for (JsonNode diagnostic : root.path("diagnostics")) {
                String text = diagnostic.asText("");
                if (text.startsWith(field + "=")) return Integer.parseInt(text.substring(field.length() + 1));
            }
            return 0;
        } catch (Exception error) { return 0; }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException error) { throw new IllegalArgumentException(error); } }
    private List<String> readList(String value) { try { return json.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return List.of(); } }
    private Map<String,String> readMap(String value) { try { return json.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return Map.of(); } }
    private Map<String,Object> readObjectMap(String value) { try { return json.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return Map.of(); } }
    private static String bounded(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public record PacketDraft(long companyId, long offeringId, PacketStatus status, String identityStatus,
            int completeness, int confidence, String summary, List<String> bullCase, List<String> bearCase,
            List<String> keyRisks, List<String> unansweredQuestions, List<String> discrepancies,
            List<String> milestones, List<String> sourcesChecked, List<String> dataNotFound,
            String sourceFingerprint, List<EvidenceDraft> evidence, List<FinancialPeriod> financials) { }
    public record EvidenceDraft(String sourceType, String sourceUrl, String sourceTitle, String factKey,
            String factValue, String period, EvidenceClassification classification, int confidence,
            String rawExcerpt, Map<String,Object> metadata) { }
    public record NotificationEvent(long id, String recipient, String subject, String text, String html, int attempts) { }
    private record JobSummary(String status, LocalDateTime startedAt, LocalDateTime completedAt, String json) { }
    private record PacketRow(long id,long companyId,String companyName,long offeringId,String platform,String campaignUrl,
            String secFilingUrl,String status,String identityStatus,int completeness,int confidence,String summary,
            String bullCase,String bearCase,String keyRisks,String questions,String discrepancies,String milestones,
            String sources,String dataNotFound,LocalDateTime generatedAt,LocalDateTime refreshedAt,LocalDateTime reviewedAt,
            String securityType,BigDecimal minimum,BigDecimal target,BigDecimal maximum,BigDecimal raised,
            String valuationOrCap,LocalDate deadline) { }
}
