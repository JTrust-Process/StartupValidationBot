package com.startupvalidationbot.offering;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Explicit maintenance only: never called by discovery, web requests, startup, or scheduled jobs. */
@Service
public class OfferingProjectionRepair {
    private static final Map<String, String> FIELDS = Map.of(
            "security_type", "securityType", "minimum_investment", "minimumInvestment",
            "target_amount", "targetAmount", "maximum_amount", "maximumAmount",
            "valuation_or_cap", "valuationOrCap", "deadline", "deadline", "amount_raised", "amountRaised");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OfferingStore offerings;

    public OfferingProjectionRepair(JdbcTemplate jdbc, ObjectMapper json, OfferingStore offerings) {
        this.jdbc = jdbc;
        this.json = json;
        this.offerings = offerings;
    }

    @Transactional(readOnly = true)
    public Plan dryRun(int limit) { return plan(snapshot(limit)); }

    @Transactional(readOnly = true)
    public Snapshot snapshot(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Repair inspection limit must be 1-100");
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM radar_offerings WHERE provenance='SEC_EDGAR'
                  AND (security_type IS NULL OR minimum_investment IS NULL OR target_amount IS NULL
                    OR maximum_amount IS NULL OR valuation_or_cap IS NULL OR deadline IS NULL OR amount_raised IS NULL)
                ORDER BY id LIMIT ?
                """, Long.class, limit);
        return new Snapshot(ids.stream().map(this::row).toList());
    }

    private Row row(long id) {
        var offering = offerings.find(id).orElseThrow();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("security_type", offering.securityType());
        fields.put("minimum_investment", string(offering.minimumInvestment()));
        fields.put("target_amount", string(offering.targetAmount()));
        fields.put("maximum_amount", string(offering.maximumAmount()));
        fields.put("valuation_or_cap", offering.valuationOrCap());
        fields.put("deadline", string(offering.deadline()));
        fields.put("amount_raised", string(offering.amountRaised()));
        List<Fact> facts = jdbc.query("""
                SELECT e.* FROM radar_diligence_evidence e
                JOIN radar_diligence_packets p ON p.id=e.packet_id WHERE p.offering_id=?
                ORDER BY e.observed_at,e.id
                """, (rs, n) -> new Fact(rs.getLong("id"), rs.getString("fact_key"), rs.getString("fact_value"),
                rs.getString("classification"), rs.getString("source_url"), rs.getTimestamp("observed_at").toLocalDateTime().toString(),
                accession(rs.getString("metadata_json"))), id);
        return new Row(id, offering.issuerName(), offering.accessionNumber(), offering.secFilingUrl(),
                offering.matchStatus().name(), Collections.unmodifiableMap(fields), facts);
    }

    public static Plan plan(Snapshot snapshot) {
        if (snapshot.rows() == null || snapshot.rows().size() > 100) throw new IllegalArgumentException("At most 100 offering rows per dry run");
        List<Proposal> proposals = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (Row row : snapshot.rows()) {
            for (String field : FIELDS.keySet().stream().sorted().toList()) {
                if (row.values().get(field) != null) continue;
                Map<String, Fact> distinct = new LinkedHashMap<>();
                for (Fact fact : row.evidence()) {
                    if (!field.equals(fact.field()) || !"SEC_FILED_FACT".equals(fact.classification())
                            || !sameFiling(row, fact)) continue;
                    String value = canonical(field, fact.value());
                    if (value != null) distinct.putIfAbsent(value, fact);
                }
                if (distinct.size() == 1) {
                    var entry = distinct.entrySet().iterator().next();
                    Fact fact = entry.getValue();
                    proposals.add(new Proposal(row.offeringId(), row.company(), field, row.values().get(field),
                            entry.getKey(), fact.id(), fact.classification(), fact.sourceUrl(), fact.observedAt(), row.accession()));
                } else if (distinct.size() > 1) {
                    unresolved.add(row.offeringId() + ": conflicting retained evidence for " + field);
                } else {
                    unresolved.add(row.offeringId() + ": no unambiguous current-accession SEC evidence for " + field);
                }
            }
            String target = proposedOrCurrent(row, "target_amount", proposals);
            String maximum = proposedOrCurrent(row, "maximum_amount", proposals);
            if (target != null && maximum != null && new BigDecimal(target).compareTo(new BigDecimal(maximum)) > 0) {
                proposals.removeIf(p -> p.offeringId() == row.offeringId() && Set.of("target_amount", "maximum_amount").contains(p.field()));
                unresolved.add(row.offeringId() + ": proposed target exceeds maximum; no amount repair approved");
            }
            if (!"CONFIRMED".equals(row.matchStatus())) {
                unresolved.add(row.offeringId() + ": identity not repaired; current evidence rows do not prove a prior stronger Radar-company resolution");
            }
        }
        return new Plan(true, 0, List.copyOf(proposals), List.copyOf(unresolved));
    }

    /** Requires an explicitly reviewed plan and allowlist. Revalidates evidence under row locks. */
    @Transactional
    public int apply(Plan approvedPlan, Set<Long> approvedIds) {
        if (!approvedPlan.dryRun() || approvedPlan.mutationCount() != 0 || approvedIds == null
                || approvedIds.isEmpty() || approvedIds.size() > 25 || approvedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Apply requires a dry-run plan and 1-25 explicitly approved offering IDs");
        }
        if (approvedPlan.proposals().stream().anyMatch(p -> !approvedIds.contains(p.offeringId()) || !FIELDS.containsKey(p.field()))) {
            throw new IllegalArgumentException("Plan contains a field or offering outside the explicit allowlist");
        }
        int writes = 0;
        for (Long id : approvedIds.stream().sorted().toList()) {
            jdbc.queryForObject("SELECT id FROM radar_offerings WHERE id=? FOR UPDATE", Long.class, id);
            Row current = row(id);
            Plan verified = plan(new Snapshot(List.of(current)));
            Map<String, String> facts = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            facts.putAll(offerings.facts(id));
            boolean changedRow = false;
            for (Proposal proposal : approvedPlan.proposals().stream().filter(p -> p.offeringId() == id).toList()) {
                if (Objects.equals(canonical(proposal.field(), current.values().get(proposal.field())), proposal.proposedValue())) continue;
                if (!verified.proposals().contains(proposal)) throw new IllegalStateException("Repair plan is stale; run dry-run again");
                Object value = switch (proposal.field()) {
                    case "security_type", "valuation_or_cap" -> proposal.proposedValue();
                    case "deadline" -> java.time.LocalDate.parse(proposal.proposedValue());
                    default -> new BigDecimal(proposal.proposedValue());
                };
                int changed = jdbc.update("UPDATE radar_offerings SET " + proposal.field() + "=?,updated_at=? WHERE id=? AND "
                        + proposal.field() + " IS NULL", value, LocalDateTime.now(), id);
                if (changed != 1) throw new IllegalStateException("Canonical field changed; refusing repair");
                writes++;
                changedRow = true;
                facts.put(FIELDS.get(proposal.field()), proposal.proposedValue());
                facts.put("_observedAt." + FIELDS.get(proposal.field()), proposal.evidenceTimestamp());
                facts.put("_sourceUrl." + FIELDS.get(proposal.field()), proposal.evidenceSource());
                facts.put("_accession." + FIELDS.get(proposal.field()), proposal.accession());
                facts.put("_repairEvidence." + FIELDS.get(proposal.field()), Long.toString(proposal.evidenceId()));
            }
            if (changedRow) {
                try { jdbc.update("UPDATE radar_offerings SET raw_facts_json=? WHERE id=?", json.writeValueAsString(facts), id); }
                catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
            }
        }
        return writes;
    }

    private static boolean sameFiling(Row row, Fact fact) {
        if (row.accession() == null || row.sourceUrl() == null || !row.accession().equals(fact.accession())) return false;
        try {
            URI uri = URI.create(fact.sourceUrl());
            return "https".equals(uri.getScheme()) && "www.sec.gov".equals(uri.getHost()) && uri.getQuery() == null
                    && uri.getUserInfo() == null && uri.getFragment() == null && uri.getPort() == -1
                    && uri.toString().equals(row.sourceUrl()) && uri.getPath().contains(row.accession().replace("-", ""));
        } catch (RuntimeException invalid) { return false; }
    }
    private static String proposedOrCurrent(Row row, String key, List<Proposal> proposals) {
        return proposals.stream().filter(p -> p.offeringId() == row.offeringId() && key.equals(p.field()))
                .map(Proposal::proposedValue).findFirst().orElse(canonical(key, row.values().get(key)));
    }
    private static String canonical(String field, String value) {
        if (value == null || value.isBlank()) return null;
        if ("security_type".equals(field)) return OfferingRefreshMerge.security(value);
        if ("valuation_or_cap".equals(field)) return OfferingRefreshMerge.validValuation(value);
        if ("deadline".equals(field)) return string(OfferingTermNormalizer.date(value));
        BigDecimal amount = OfferingTermNormalizer.money(value);
        amount = "amount_raised".equals(field) ? OfferingRefreshMerge.nonNegative(amount) : OfferingRefreshMerge.positive(amount);
        return amount == null ? null : amount.stripTrailingZeros().toPlainString();
    }
    private String accession(String value) {
        try { return json.readTree(value).path("accessionNumber").asText(null); }
        catch (Exception ignored) { return null; }
    }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    public record Snapshot(List<Row> rows) { }
    public record Row(long offeringId, String company, String accession, String sourceUrl,
            String matchStatus, Map<String, String> values, List<Fact> evidence) { }
    public record Fact(long id, String field, String value, String classification, String sourceUrl,
            String observedAt, String accession) { }
    public record Proposal(long offeringId, String company, String field, String currentValue, String proposedValue,
            long evidenceId, String classification, String evidenceSource, String evidenceTimestamp, String accession) { }
    public record Plan(boolean dryRun, int mutationCount, List<Proposal> proposals, List<String> unresolved) { }
}
