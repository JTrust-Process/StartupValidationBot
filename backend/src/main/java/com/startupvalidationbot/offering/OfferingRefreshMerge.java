package com.startupvalidationbot.offering;

import static com.startupvalidationbot.offering.OfferingDomain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.startupvalidationbot.radar.CompanyIdentity;

/** Missing retrieval data is not a deletion. Metadata records actual observations, not carry-forwards. */
public final class OfferingRefreshMerge {
    private OfferingRefreshMerge() { }

    public static Candidate merge(Candidate previous, Candidate incoming, LocalDateTime now) {
        Map<String, String> previousFacts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (previous != null) previousFacts.putAll(previous.facts());
        if (previous != null && known(incoming.accessionNumber()) != null
                && !Objects.equals(previous.accessionNumber(), incoming.accessionNumber())) {
            // Raw fiscal-year aliases cannot be attributed to a different filing. Their evidence history
            // remains retained; canonical carried terms below keep their own original filing references.
            previousFacts.keySet().removeIf(key -> !key.startsWith("_"));
        }
        var values = new Values(previousFacts, incoming.facts(), now);
        values.sourceUrl = incoming.secFilingUrl();
        values.accession = incoming.accessionNumber();
        if (previous != null) {
            for (String key : java.util.List.of("securityType", "minimumInvestment", "targetAmount", "maximumAmount",
                    "valuationOrCap", "deadline", "amountRaised")) {
                if (previous.secFilingUrl() != null) values.facts.putIfAbsent("_sourceUrl." + key, previous.secFilingUrl());
                if (previous.accessionNumber() != null) values.facts.putIfAbsent("_accession." + key, previous.accessionNumber());
            }
        }
        boolean detail = incoming.retrievalQuality() != RetrievalQuality.INDEX_ONLY;
        String name = values.field("issuerName", known(incoming.issuerName()), previous == null ? null : previous.issuerName());
        String cik = values.field("issuerCik", known(incoming.issuerCik()), previous == null ? null : previous.issuerCik());
        String website = values.field("issuerWebsite", detail ? known(incoming.issuerWebsite()) : null,
                previous == null ? null : previous.issuerWebsite());
        String platform = values.field("platform", detail ? known(incoming.platform()) : null,
                previous == null ? null : previous.platform());
        String intermediary = values.field("intermediaryName", detail ? known(incoming.intermediaryName()) : null,
                previous == null ? null : previous.intermediaryName());
        String intermediaryCik = values.field("intermediaryCik", detail ? known(incoming.intermediaryCik()) : null,
                previous == null ? null : previous.intermediaryCik());
        String url = values.field("offeringUrl", detail ? known(incoming.offeringUrl()) : null,
                previous == null ? null : previous.offeringUrl());
        String security = values.field("securityType", detail ? security(incoming.securityType()) : null,
                previous == null ? null : previous.securityType());
        BigDecimal minimum = values.field("minimumInvestment", detail ? positive(incoming.minimumInvestment()) : null,
                previous == null ? null : previous.minimumInvestment());
        BigDecimal target = detail ? positive(incoming.targetAmount()) : null;
        BigDecimal maximum = detail ? positive(incoming.maximumAmount()) : null;
        BigDecimal effectiveTarget = first(target, previous == null ? null : previous.targetAmount());
        BigDecimal effectiveMaximum = first(maximum, previous == null ? null : previous.maximumAmount());
        if (effectiveTarget != null && effectiveMaximum != null && effectiveTarget.compareTo(effectiveMaximum) > 0) {
            values.facts.put("_rejected.amounts", "Incoming target/maximum conflicts with established bounds");
            target = null;
            maximum = null;
        }
        target = values.field("targetAmount", target, previous == null ? null : previous.targetAmount());
        maximum = values.field("maximumAmount", maximum, previous == null ? null : previous.maximumAmount());
        String valuation = values.field("valuationOrCap", detail ? validValuation(incoming.valuationOrCap()) : null,
                previous == null ? null : previous.valuationOrCap());
        var deadline = values.field("deadline", detail ? incoming.deadline() : null,
                previous == null ? null : previous.deadline());
        BigDecimal raised = values.field("amountRaised", detail ? nonNegative(incoming.amountRaised()) : null,
                previous == null ? null : previous.amountRaised());
        if (detail && known(incoming.securityType()) != null && security(incoming.securityType()) == null) {
            values.facts.put("_rejected.securityType", incoming.securityType());
        }
        values.facts.put("_retrievalQuality", incoming.retrievalQuality().name());
        values.facts.put("_lastRefreshAt", now.toString());
        return new Candidate(name, cik, website, first(platform, "UNKNOWN"), intermediary, intermediaryCik, url,
                first(known(incoming.secFilingUrl()), previous == null ? null : previous.secFilingUrl()),
                first(known(incoming.accessionNumber()), previous == null ? null : previous.accessionNumber()),
                first(known(incoming.fileNumber()), previous == null ? null : previous.fileNumber()),
                first(known(incoming.filingType()), previous == null ? null : previous.filingType()),
                first(incoming.filingDate(), previous == null ? null : previous.filingDate()),
                security, minimum, target, maximum, valuation, deadline, raised,
                first(known(incoming.source()), previous == null ? null : previous.source()), Map.copyOf(values.facts),
                incoming.retrievalQuality());
    }

    public static Match match(Offering previous, Match incoming, Candidate observed, Candidate stored) {
        boolean conflict = incoming.contradictoryEvidence();
        if (observed != null && stored != null) {
            String oldDomain = CompanyIdentity.normalizeDomain(stored.issuerWebsite());
            String newDomain = observed.retrievalQuality() == RetrievalQuality.INDEX_ONLY ? null
                    : CompanyIdentity.normalizeDomain(observed.issuerWebsite());
            conflict |= oldDomain != null && newDomain != null && !oldDomain.equals(newDomain);
            conflict |= known(stored.issuerCik()) != null && known(observed.issuerCik()) != null
                    && !stored.issuerCik().equals(observed.issuerCik());
        }
        if (conflict && incoming.status() == MatchStatus.CONFIRMED) {
            return new Match(previous.radarCompanyId(), MatchStatus.REJECTED, 15,
                    "New issuer evidence contradicts the established offering identity.", true);
        }
        if (!conflict && previous.matchStatus() == MatchStatus.CONFIRMED
                && (incoming.status() != MatchStatus.CONFIRMED
                    || Objects.equals(previous.radarCompanyId(), incoming.companyId()))) {
            return new Match(previous.radarCompanyId(), previous.matchStatus(), previous.matchConfidence(),
                    previous.matchReason());
        }
        return incoming;
    }

    public static String known(String value) {
        if (value == null || value.isBlank() || value.trim().matches("(?i)unknown|unavailable|not disclosed|n/a")) return null;
        return value.trim();
    }
    public static BigDecimal positive(BigDecimal value) { return value != null && value.signum() > 0 ? value : null; }
    public static String security(String value) {
        return value != null && value.trim().equalsIgnoreCase("Other") ? "Other" : OfferingTermNormalizer.security(value);
    }
    public static BigDecimal nonNegative(BigDecimal value) { return value != null && value.signum() >= 0 ? value : null; }
    public static String validValuation(String value) {
        if (known(value) == null) return null;
        String number = value.replaceAll("(?i)\\s+(?:(?:pre-money|post-money)\\s+)?(?:valuation(?:\\s+cap)?|cap)\\s*$", "");
        return positive(OfferingTermNormalizer.money(number)) == null ? null : value.trim();
    }
    public static <T> T first(T incoming, T previous) { return incoming == null ? previous : incoming; }

    public static final class Values {
        public final Map<String, String> facts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        public String sourceUrl;
        public String accession;
        private final LocalDateTime now;
        public Values(Map<String, String> previous, Map<String, String> incoming, LocalDateTime now) {
            this.now = now;
            facts.putAll(previous);
            incoming.forEach((key, value) -> { if (!key.startsWith("_") && known(value) != null) facts.put(key, value); });
        }
        public <T> T field(String key, T incoming, T previous) {
            if (incoming != null) {
                facts.put(key, incoming.toString());
                if (now != null) facts.put("_observedAt." + key, now.toString());
                if (sourceUrl != null) facts.put("_sourceUrl." + key, sourceUrl);
                if (accession != null) facts.put("_accession." + key, accession);
            } else if (previous != null) {
                facts.put(key, previous.toString());
            }
            return first(incoming, previous);
        }
    }
}
