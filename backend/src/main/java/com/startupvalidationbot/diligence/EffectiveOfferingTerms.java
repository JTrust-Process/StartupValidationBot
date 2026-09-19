package com.startupvalidationbot.diligence;

import static com.startupvalidationbot.diligence.DiligenceDomain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.startupvalidationbot.diligence.platform.PlatformUrlPolicy;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingTermNormalizer;

public final class EffectiveOfferingTerms {
    private EffectiveOfferingTerms() { }

    public static Projection resolve(Offering offering, PlatformCampaign campaign) {
        SourceValues source = new SourceValues(offering.provenance(), offering.platform(), offering.offeringUrl(),
                offering.secFilingUrl(), offering.securityType(), offering.minimumInvestment(),
                offering.targetAmount(), offering.maximumAmount(), offering.amountRaised(),
                offering.valuationOrCap(), offering.deadline(), offering.platformStatus());
        return resolve(source, campaign);
    }

    static Projection resolve(SourceValues offering, PlatformCampaign campaign) {
        boolean secFiled = "SEC_EDGAR".equalsIgnoreCase(safe(offering.provenance()));
        boolean trustedCampaign = trusted(campaign);
        List<String> issues = new ArrayList<>();

        String platformSecurity = trustedCampaign ? OfferingTermNormalizer.security(campaign.securityType()) : null;
        if (trustedCampaign && present(campaign.securityType()) && platformSecurity == null) {
            issues.add("Platform security type could not be normalized safely");
        }
        BigDecimal platformMinimum = trustedCampaign ? positive(campaign.minimumInvestment(),
                "minimum investment", issues, true) : null;
        BigDecimal platformTarget = trustedCampaign ? positive(campaign.targetAmount(),
                "target amount", issues, true) : null;
        BigDecimal platformMaximum = trustedCampaign ? positive(campaign.maximumAmount(),
                "maximum amount", issues, true) : null;
        if (platformTarget != null && platformMaximum != null && platformTarget.compareTo(platformMaximum) > 0) {
            issues.add("Platform target amount exceeds maximum amount");
            platformTarget = null;
            platformMaximum = null;
        }
        BigDecimal platformValuation = trustedCampaign ? positive(campaign.valuation(),
                "valuation", issues, true) : null;
        BigDecimal platformCap = trustedCampaign ? positive(campaign.valuationCap(),
                "valuation cap", issues, true) : null;
        BigDecimal platformRaised = trustedCampaign ? nonNegative(campaign.amountRaised(),
                "amount raised", issues, true) : null;
        if (platformRaised != null && platformMinimum != null && platformRaised.signum() > 0
                && platformRaised.compareTo(platformMinimum) < 0) {
            issues.add("Platform amount raised is below the stated minimum and requires review");
            platformRaised = null;
        }

        String secSecurity = secFiled && present(offering.securityType())
                ? first(OfferingTermNormalizer.security(offering.securityType()), offering.securityType().trim()) : null;
        BigDecimal secMinimum = secFiled ? positive(offering.minimumInvestment(), null, issues, false) : null;
        BigDecimal secTarget = secFiled ? positive(offering.targetAmount(), null, issues, false) : null;
        BigDecimal secMaximum = secFiled ? positive(offering.maximumAmount(), null, issues, false) : null;
        if (secTarget != null && secMaximum != null && secTarget.compareTo(secMaximum) > 0) {
            issues.add("SEC-filed target amount exceeds maximum amount");
            secTarget = null;
            secMaximum = null;
        }
        BigDecimal secRaised = secFiled ? nonNegative(offering.amountRaised(), null, issues, false) : null;
        BigDecimal secValuation = null;
        BigDecimal secCap = null;
        if (secFiled && present(offering.valuationOrCap())) {
            BigDecimal value = OfferingTermNormalizer.money(offering.valuationOrCap()
                    .replaceAll("(?i)\\s+(?:(?:pre-money|post-money)\\s+)?(?:valuation(?:\\s+cap)?|cap)\\s*$", ""));
            if (value != null && value.signum() > 0) {
                if (offering.valuationOrCap().toLowerCase(Locale.ROOT).contains("cap")) secCap = value;
                else secValuation = value;
            }
        }

        TermProvenance sec = new TermProvenance("SEC EDGAR Form C", offering.secFilingUrl(),
                EvidenceClassification.SEC_FILED_FACT);
        TermProvenance platform = campaign == null ? null : new TermProvenance(
                campaign.platform() + " public campaign", campaign.campaignUrl(),
                EvidenceClassification.PLATFORM_ISSUER_CLAIM);
        Map<String, TermProvenance> provenance = new LinkedHashMap<>();

        String security = choose("securityType", secSecurity, platformSecurity, sec, platform, provenance);
        BigDecimal minimum = choose("minimumInvestment", secMinimum, platformMinimum, sec, platform, provenance);
        BigDecimal target = choose("targetAmount", secTarget, platformTarget, sec, platform, provenance);
        BigDecimal maximum = choose("maximumAmount", secMaximum, platformMaximum, sec, platform, provenance);
        BigDecimal raised = choose("amountRaised", secRaised, platformRaised, sec, platform, provenance);
        BigDecimal valuation = choose("valuation", secValuation, platformValuation, sec, platform, provenance);
        BigDecimal cap = choose("valuationCap", secCap, platformCap, sec, platform, provenance);
        LocalDate deadline = choose("deadline", secFiled ? offering.deadline() : null,
                trustedCampaign ? campaign.deadline() : null, sec, platform, provenance);

        String campaignUrl = null;
        if (trustedCampaign) {
            campaignUrl = campaign.campaignUrl();
            provenance.put("campaignUrl", "SEC_OFFERING_URL".equals(campaign.campaignUrlSource())
                    ? new TermProvenance("SEC EDGAR Form C", offering.secFilingUrl(),
                            EvidenceClassification.SEC_FILED_FACT)
                    : platform);
        }
        String platformStatus = null;
        if (trustedCampaign && campaign.status() != CampaignStatus.UNAVAILABLE) {
            platformStatus = campaign.status().name();
            provenance.put("platformStatus", platform);
        } else if (knownStatus(offering.platformStatus()) && present(offering.offeringUrl())) {
            platformStatus = offering.platformStatus().toUpperCase(Locale.ROOT);
            provenance.put("platformStatus", new TermProvenance(offering.platform() + " public listing",
                    offering.offeringUrl(), EvidenceClassification.PLATFORM_ISSUER_CLAIM));
        }

        String platformIdentity = trustedCampaign
                ? "Verified from official " + displayPlatform(campaign.platform()) + " campaign"
                : "Not established";
        String secReconciliation = secFiled ? "Established from SEC-filed offering" : "Not established";
        return new Projection(security, minimum, target, maximum, raised, valuation, cap, deadline,
                campaignUrl, platformStatus, platformIdentity, secReconciliation,
                Map.copyOf(provenance), List.copyOf(issues), trustedCampaign);
    }

    private static boolean trusted(PlatformCampaign campaign) {
        if (campaign == null || campaign.campaignUrlConfidence() < 80
                || campaign.status() == CampaignStatus.UNAVAILABLE) return false;
        try {
            PlatformUrlPolicy.require(campaign.platform(), campaign.campaignUrl());
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean knownStatus(String value) {
        if (!present(value)) return false;
        try {
            CampaignStatus.valueOf(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static BigDecimal positive(BigDecimal value, String label, List<String> issues, boolean report) {
        if (value == null) return null;
        if (value.signum() > 0) return value;
        if (report && label != null) issues.add("Platform " + label + " must be greater than zero");
        return null;
    }

    private static BigDecimal nonNegative(BigDecimal value, String label, List<String> issues, boolean report) {
        if (value == null) return null;
        if (value.signum() >= 0) return value;
        if (report && label != null) issues.add("Platform " + label + " cannot be negative");
        return null;
    }

    private static <T> T choose(String key, T secValue, T platformValue, TermProvenance sec,
            TermProvenance platform, Map<String, TermProvenance> provenance) {
        if (secValue != null) {
            provenance.put(key, sec);
            return secValue;
        }
        if (platformValue != null) {
            provenance.put(key, platform);
            return platformValue;
        }
        return null;
    }

    private static String displayPlatform(String value) {
        if (value == null || value.isBlank()) return "platform";
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static <T> T first(T first, T second) { return first == null ? second : first; }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String safe(String value) { return value == null ? "" : value; }

    public record SourceValues(String provenance, String platform, String offeringUrl, String secFilingUrl,
            String securityType, BigDecimal minimumInvestment, BigDecimal targetAmount,
            BigDecimal maximumAmount, BigDecimal amountRaised, String valuationOrCap,
            LocalDate deadline, String platformStatus) { }

    public record Projection(String securityType, BigDecimal minimumInvestment, BigDecimal targetAmount,
            BigDecimal maximumAmount, BigDecimal amountRaised, BigDecimal valuation,
            BigDecimal valuationCap, LocalDate deadline, String campaignUrl, String platformStatus,
            String platformIdentityStatus, String secReconciliationStatus,
            Map<String, TermProvenance> provenance, List<String> issues, boolean officialCampaignVerified) {
        public boolean hasUsablePlatformTerms() {
            return securityType != null || minimumInvestment != null || targetAmount != null
                    || maximumAmount != null || valuation != null || valuationCap != null || deadline != null;
        }

        public String valuationOrCap() {
            if (valuationCap != null) return valuationCap.stripTrailingZeros().toPlainString() + " valuation cap";
            return valuation == null ? null : valuation.stripTrailingZeros().toPlainString() + " valuation";
        }
    }
}
