package com.startupvalidationbot.diligence;

import static com.startupvalidationbot.diligence.DiligenceDomain.*;
import static com.startupvalidationbot.offering.OfferingRefreshMerge.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

final class CampaignRefreshMerge {
    private CampaignRefreshMerge() { }

    static PlatformCampaign merge(PlatformCampaign old, PlatformCampaign next) {
        if (old != null && (!Objects.equals(old.platform(), next.platform())
                || !Objects.equals(old.campaignUrl(), next.campaignUrl()))) old = null;
        // PostgreSQL rounds timestamps to microseconds; replaying the same nanosecond observation
        // must not look older merely because its stored timestamp rounded up.
        if (old != null && next.lastCheckedAt() != null && old.lastCheckedAt() != null
                && next.lastCheckedAt().isBefore(old.lastCheckedAt().minusNanos(1000))) return old;
        if (old != null && next.status() == CampaignStatus.UNAVAILABLE) return old;
        var values = new Values(old == null ? Map.of() : old.facts(), next.facts(), next.lastCheckedAt());
        String issuer = values.field("issuerName", known(next.issuerName()), old == null ? null : old.issuerName());
        String security = values.field("securityType", security(next.securityType()), old == null ? null : old.securityType());
        BigDecimal minimum = values.field("minimumInvestment", positive(next.minimumInvestment()), old == null ? null : old.minimumInvestment());
        BigDecimal price = values.field("pricePerShare", positive(next.pricePerShare()), old == null ? null : old.pricePerShare());
        BigDecimal valuation = values.field("valuation", positive(next.valuation()), old == null ? null : old.valuation());
        BigDecimal cap = values.field("valuationCap", positive(next.valuationCap()), old == null ? null : old.valuationCap());
        BigDecimal discount = values.field("discountPercent", next.discountPercent() != null && next.discountPercent().compareTo(BigDecimal.valueOf(100)) <= 0
                ? nonNegative(next.discountPercent()) : null, old == null ? null : old.discountPercent());
        BigDecimal target = positive(next.targetAmount()), maximum = positive(next.maximumAmount());
        BigDecimal mergedTarget = first(target, old == null ? null : old.targetAmount());
        BigDecimal mergedMax = first(maximum, old == null ? null : old.maximumAmount());
        if (mergedTarget != null && mergedMax != null && mergedTarget.compareTo(mergedMax) > 0) {
            values.facts.put("_rejected.amounts", "Incoming target/maximum conflicts with established bounds");
            target = null;
            maximum = null;
        }
        target = values.field("targetAmount", target, old == null ? null : old.targetAmount());
        maximum = values.field("maximumAmount", maximum, old == null ? null : old.maximumAmount());
        BigDecimal incomingRaised = nonNegative(next.amountRaised());
        if (incomingRaised != null && incomingRaised.signum() > 0 && minimum != null && incomingRaised.compareTo(minimum) < 0) incomingRaised = null;
        BigDecimal raised = values.field("amountRaised", incomingRaised, old == null ? null : old.amountRaised());
        Integer investors = values.field("investorCount", next.investorCount() != null && next.investorCount() >= 0 ? next.investorCount() : null,
                old == null ? null : old.investorCount());
        var deadline = values.field("deadline", next.deadline(), old == null ? null : old.deadline());
        CampaignStatus status = next.status() == CampaignStatus.UNKNOWN && old != null ? old.status() : next.status();
        boolean preserved = old != null && (next.securityType() == null && old.securityType() != null
                || next.minimumInvestment() == null && old.minimumInvestment() != null
                || next.valuationCap() == null && old.valuationCap() != null
                || next.targetAmount() == null && old.targetAmount() != null
                || next.maximumAmount() == null && old.maximumAmount() != null
                || next.deadline() == null && old.deadline() != null);
        values.facts.put("_retrievalQuality", preserved ? "PARTIAL_DETAIL" : next.facts().getOrDefault("_retrievalQuality", "PARTIAL_DETAIL"));
        // Older records have only a campaign-level observation time. Preserve it for absent fields.
        if (old != null && old.lastVerifiedAt() != null) {
            for (String key : java.util.List.of("securityType", "minimumInvestment", "valuation", "valuationCap",
                    "targetAmount", "maximumAmount", "amountRaised", "deadline")) {
                if (values.facts.containsKey(key)) values.facts.putIfAbsent("_observedAt." + key, old.lastVerifiedAt().toString());
            }
        }
        return new PlatformCampaign(old == null ? next.id() : old.id(), next.offeringId(), next.platform(),
                next.campaignUrl(), next.campaignUrlSource(), next.campaignUrlConfidence(), status, issuer,
                security, minimum, price, valuation, cap, discount, target, maximum, raised, investors,
                deadline, first(known(next.headline()), old == null ? null : old.headline()),
                Map.copyOf(values.facts), next.sourceFingerprint(), next.lastCheckedAt(),
                preserved ? old.lastVerifiedAt() : next.lastVerifiedAt());
    }
}
