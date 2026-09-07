package com.startupvalidationbot.diligence.platform;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.radar.ContentHash;

abstract class AbstractPlatformOfferingEnricher implements PlatformOfferingEnricher {
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern H1 = Pattern.compile("(?is)<h1[^>]*>(.*?)</h1>");
    private static final Pattern JSON_NAME = Pattern.compile("(?is)\"(?:name|headline)\"\s*:\s*\"([^\"]{2,500})\"");
    private final PlatformHttpClient client;

    AbstractPlatformOfferingEnricher(PlatformHttpClient client) { this.client = client; }

    @Override
    public boolean supports(URI uri) {
        try { PlatformUrlPolicy.require(platform(), uri.toString()); return true; }
        catch (IllegalArgumentException error) { return false; }
    }

    @Override
    public PlatformCampaign enrich(Offering offering, URI campaignUrl) {
        String html = client.get(platform(), campaignUrl);
        return parse(offering, campaignUrl, html);
    }

    PlatformCampaign parse(Offering offering, URI campaignUrl, String html) {
        String text = visibleText(html);
        Map<String, String> facts = new LinkedHashMap<>();
        String issuer = first(html, H1);
        if (issuer == null) issuer = firstNonGenericJsonName(html);
        if (issuer == null) issuer = first(html, TITLE);
        issuer = cleanTitle(issuer);
        String security = field(text, "(?:security(?: type)?|instrument)", "([A-Za-z][A-Za-z /-]{2,80})");
        BigDecimal minimum = money(field(text, "(?:minimum investment|min\\.? investment|minimum)", "(\\$?[0-9][0-9,.]*[KMB]?)"));
        BigDecimal price = money(field(text, "(?:price per share|share price|price per unit)", "(\\$?[0-9][0-9,.]*)"));
        BigDecimal cap = money(field(text, "(?:valuation cap|post-money cap)", "(\\$?[0-9][0-9,.]*[KMB]?)"));
        BigDecimal valuation = money(field(text, "(?:pre-money valuation|post-money valuation|valuation)", "(\\$?[0-9][0-9,.]*[KMB]?)"));
        BigDecimal target = money(field(text, "(?:target raise|target offering amount|funding goal)", "(\\$?[0-9][0-9,.]*[KMB]?)"));
        BigDecimal maximum = money(field(text, "(?:maximum raise|maximum offering amount|max raise)", "(\\$?[0-9][0-9,.]*[KMB]?)"));
        BigDecimal raised = money(field(text, "(?:amount raised|raised|committed)", "(\\$?[0-9][0-9,.]*[KMB]?)"));
        Integer investors = integer(field(text, "(?:investors|investor count)", "([0-9][0-9,]*)"));
        BigDecimal discount = decimal(field(text, "(?:discount)", "([0-9]{1,3}(?:\\.[0-9]+)?)%"));
        LocalDate deadline = date(field(text, "(?:deadline|closes|closing date)", "([A-Za-z]+ \\d{1,2}, \\d{4}|\\d{4}-\\d{2}-\\d{2})"));
        put(facts, "headline", cleanTitle(first(html, TITLE)));
        put(facts, "issuerName", issuer); put(facts, "securityType", security);
        put(facts, "minimumInvestment", minimum); put(facts, "pricePerShare", price);
        put(facts, "valuation", valuation); put(facts, "valuationCap", cap); put(facts, "discountPercent", discount);
        put(facts, "targetAmount", target); put(facts, "maximumAmount", maximum);
        put(facts, "amountRaised", raised); put(facts, "investorCount", investors); put(facts, "deadline", deadline);
        CampaignStatus status = status(text);
        String fingerprint = ContentHash.sha256(platform() + "|" + campaignUrl + "|" + facts + "|" + status);
        LocalDateTime now = LocalDateTime.now();
        return new PlatformCampaign(null, offering.id(), platform(), campaignUrl.toString(),
                "PUBLIC_CAMPAIGN_URL", 95, status, issuer, security, minimum, price, valuation, cap,
                discount, target, maximum, raised, investors, deadline, facts.get("headline"),
                Map.copyOf(facts), fingerprint, now, now);
    }

    private static CampaignStatus status(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("successfully funded") || lower.contains("funded and closed")) return CampaignStatus.FUNDED;
        if (lower.contains("campaign ended") || lower.contains("offering closed") || lower.contains("closed offering")) return CampaignStatus.CLOSED;
        if (lower.contains("accepting reservations") || lower.contains("reservation")
                || lower.contains("reserve your investment")) return CampaignStatus.RESERVATION;
        if (lower.contains("invest now") || lower.contains("days left") || lower.contains("open for investment")) return CampaignStatus.ACTIVE;
        return CampaignStatus.UNKNOWN;
    }

    private static String visibleText(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ")
                .replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }

    private static String field(String text, String label, String value) {
        Matcher matcher = Pattern.compile("(?i)" + label + "\\s*[:\\-]?\\s*" + value).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String first(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", " ").trim() : null;
    }

    private static String firstNonGenericJsonName(String html) {
        Matcher matcher = JSON_NAME.matcher(html);
        while (matcher.find()) {
            String candidate = matcher.group(1).replaceAll("\\s+", " ").trim();
            if (!candidate.matches("(?i)home|menu|navigation|search|login|sign up|learn more")) return candidate;
        }
        return null;
    }

    private static String cleanTitle(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("(?is)<[^>]+>", " ").replace("&amp;", "&")
                .replaceAll("(?i)^\\s*(?:invest|reserve)\\s+in\\s+", "")
                .replaceAll("(?i)\\s*[|\\-–—]\\s*(Wefunder|Republic|StartEngine).*$", "")
                .replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static BigDecimal money(String value) {
        if (value == null) return null;
        String cleaned = value.replace("$", "").replace(",", "").trim().toUpperCase(Locale.ROOT);
        BigDecimal multiplier = BigDecimal.ONE;
        if (cleaned.endsWith("K")) { multiplier = new BigDecimal("1000"); cleaned = cleaned.substring(0, cleaned.length() - 1); }
        else if (cleaned.endsWith("M")) { multiplier = new BigDecimal("1000000"); cleaned = cleaned.substring(0, cleaned.length() - 1); }
        else if (cleaned.endsWith("B")) { multiplier = new BigDecimal("1000000000"); cleaned = cleaned.substring(0, cleaned.length() - 1); }
        try { return new BigDecimal(cleaned).multiply(multiplier); } catch (NumberFormatException error) { return null; }
    }

    private static BigDecimal decimal(String value) { try { return value == null ? null : new BigDecimal(value); } catch (NumberFormatException error) { return null; } }
    private static Integer integer(String value) { try { return value == null ? null : Integer.valueOf(value.replace(",", "")); } catch (NumberFormatException error) { return null; } }
    private static LocalDate date(String value) {
        if (value == null) return null;
        try { return LocalDate.parse(value); } catch (RuntimeException ignored) { }
        try { return LocalDate.parse(value, DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)); }
        catch (RuntimeException ignored) { return null; }
    }
    private static void put(Map<String, String> facts, String key, Object value) { if (value != null && !value.toString().isBlank()) facts.put(key, value.toString()); }
}
