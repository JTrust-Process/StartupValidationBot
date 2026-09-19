package com.startupvalidationbot.offering.intake;

import static com.startupvalidationbot.offering.intake.NativeOfferingDomain.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.diligence.platform.PlatformHttpClient;
import com.startupvalidationbot.diligence.platform.PlatformUrlPolicy;
import com.startupvalidationbot.radar.CompanyIdentity;

@Component
public class StartEngineNativeOfferingAdapter implements NativeOfferingSourceAdapter {
    static final URI DIRECTORY = URI.create("https://www.startengine.com/explore");
    private static final Pattern SEC_LINK = Pattern.compile(
            "(?is)href=[\\\"'](https://(?:www\\.)?sec\\.gov/[^\\\"']+)[\\\"']");
    private final PlatformHttpClient http;

    public StartEngineNativeOfferingAdapter(PlatformHttpClient http) { this.http = http; }

    @Override public String source() { return "STARTENGINE"; }
    @Override public String capability() { return "PUBLIC_CURRENT_DIRECTORY"; }

    @Override
    public SourceResult discover(int maxCandidates, int maxDetailRequests) {
        List<String> errors = new ArrayList<>();
        int requests = 1;
        try {
            String html = http.get(source(), DIRECTORY);
            if (OfferingHtml.challenge(html)) {
                return SourceResult.failed(source(), capability(),
                        "StartEngine requires browser verification; no CAPTCHA or anti-bot bypass was attempted.", 1);
            }
            List<Listing> listing = parseDirectory(html, maxCandidates);
            List<NativeOfferingCandidate> candidates = new ArrayList<>();
            int details = 0;
            for (Listing item : listing) {
                String detail = "";
                if (details < Math.max(0, Math.min(maxDetailRequests, 25))) {
                    try {
                        detail = http.get(source(), URI.create(item.url()));
                        requests++; details++;
                        if (OfferingHtml.challenge(detail)) {
                            errors.add("StartEngine detail access required browser verification for " + item.externalId());
                            detail = "";
                        }
                    } catch (RuntimeException error) {
                        errors.add("StartEngine detail unavailable for " + item.externalId() + ": " + safe(error));
                    }
                }
                candidates.add(parseCampaign(item, detail, LocalDateTime.now()));
            }
            return new SourceResult(source(), capability(), errors.isEmpty() ? "OK" : "DEGRADED",
                    true, requests, details, List.copyOf(candidates), List.copyOf(errors));
        } catch (RuntimeException error) {
            return SourceResult.failed(source(), capability(), safe(error), requests);
        }
    }

    static List<Listing> parseDirectory(String html, int limit) {
        Map<String, Listing> result = new LinkedHashMap<>();
        for (OfferingHtml.Link link : OfferingHtml.links(html)) {
            URI resolved = OfferingHtml.absolute(DIRECTORY, link.href());
            if (resolved == null || !offeringPath(resolved)) continue;
            URI canonical;
            try { canonical = PlatformUrlPolicy.require("STARTENGINE", stripQuery(resolved)); }
            catch (IllegalArgumentException ignored) { continue; }
            String name = link.name() == null ? OfferingHtml.slugName(canonical) : link.name();
            result.putIfAbsent(canonical.toString(), new Listing(pathId(canonical), name,
                    canonical.toString(), OfferingHtml.bounded(link.text(), 1200)));
            if (result.size() >= Math.max(1, Math.min(limit, 25))) break;
        }
        return List.copyOf(result.values());
    }

    static NativeOfferingCandidate parseCampaign(Listing listing, String html, LocalDateTime retrievedAt) {
        String detail = OfferingHtml.text(html);
        String combined = (listing.listingText() + " " + detail).trim();
        String name = first(OfferingHtml.pageName(html), listing.name(), OfferingHtml.slugName(URI.create(listing.url())));
        LocalDate deadline = OfferingHtml.date(OfferingHtml.field(combined, "(?:deadline|closing date|closes)",
                "([A-Za-z]+ \\d{1,2}, \\d{4}|\\d{4}-\\d{2}-\\d{2})"));
        Status status = withDeadline(status(combined), deadline);
        String exemption = exemption(combined);
        String website = officialWebsite(html);
        String secUrl = secUrl(html);
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("listingText", OfferingHtml.bounded(listing.listingText(), 1200));
        put(evidence, "campaignText", OfferingHtml.bounded(detail, 1800));
        put(evidence, "statusEvidence", status.name());
        if (!"REG_CF".equals(exemption)) put(evidence, "actionableExclusion", exclusion(status, exemption));

        return new NativeOfferingCandidate("STARTENGINE_DIRECTORY", "StartEngine", listing.externalId(),
                name, listing.url(), website, CompanyIdentity.normalizeDomain(website), status, exemption,
                "StartEngine Primary, LLC", security(combined),
                money(combined, "(?:amount raised|raised)"),
                money(combined, "(?:funding goal|target raise|target offering amount)"),
                money(combined, "(?:maximum raise|maximum offering amount|max raise)"), valuation(combined),
                money(combined, "(?:minimum investment|min\\.? investment|minimum)"),
                money(combined, "(?:price per share|share price)"),
                deadline,
                null, OfferingHtml.bounded(combined, 800), null, accession(secUrl), null, secUrl,
                secUrl == null ? null : "C", null, Map.copyOf(evidence), retrievedAt);
    }

    private static Status status(String text) {
        String value = lower(text);
        if (value.contains("this offering is closed") || value.contains("offering closed")
                || value.contains("past offering") || value.contains("campaign ended")) return Status.CLOSED;
        if (value.contains("withdrawn")) return Status.WITHDRAWN;
        if (value.contains("accepting reservations") || value.contains("reserve your investment")) return Status.RESERVATION;
        if (value.matches(".*\\b(?:hours?|days?) (?:left|remaining)\\b.*")) return Status.CLOSING_SOON;
        if (value.contains("invest now") || value.contains("open for investment")
                || value.contains("amount raised") || value.contains("funding progress")) return Status.ACTIVE;
        return Status.UNKNOWN;
    }

    private static Status withDeadline(Status status, LocalDate deadline) {
        if (deadline == null) return status;
        if (deadline.isBefore(LocalDate.now())) return Status.CLOSED;
        if (status == Status.ACTIVE && !deadline.isAfter(LocalDate.now().plusDays(7))) {
            return Status.CLOSING_SOON;
        }
        return status;
    }

    private static String exemption(String text) {
        String value = lower(text);
        if (value.contains("regulation crowdfunding") || value.contains("reg cf")) return "REG_CF";
        if (value.contains("reg a+") || value.contains("regulation a")) return "REG_A";
        if (value.contains("reg d") || value.contains("506(c)")) return "REG_D";
        return "UNKNOWN";
    }

    private static String security(String text) {
        return OfferingHtml.field(text, "(?:security(?: type)?|instrument|type of security)",
                "([A-Za-z][A-Za-z /-]{2,80})");
    }

    private static java.math.BigDecimal money(String text, String label) {
        return OfferingHtml.money(OfferingHtml.field(text, label, "(\\$?[0-9][0-9,.]*[KMB]?)"));
    }

    private static String valuation(String text) {
        String value = OfferingHtml.field(text, "(?:valuation cap|post-money cap|pre-money valuation|valuation)",
                "(\\$?[0-9][0-9,.]*[KMB]?)");
        return value == null ? null : value;
    }

    private static String secUrl(String html) {
        Matcher matcher = SEC_LINK.matcher(html == null ? "" : html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String accession(String secUrl) {
        if (secUrl == null) return null;
        Matcher matcher = Pattern.compile("(\\d{10}-\\d{2}-\\d{6})").matcher(secUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String officialWebsite(String html) {
        for (OfferingHtml.Link link : OfferingHtml.links(html)) {
            String label = (value(link.name()) + " " + value(link.text())).toLowerCase(Locale.ROOT);
            if (!label.matches(".*\\b(company website|official website|visit website|visit site)\\b.*")) continue;
            String domain = CompanyIdentity.normalizeDomain(link.href());
            if (domain != null && !domain.endsWith("startengine.com")) return link.href();
        }
        return null;
    }

    private static boolean offeringPath(URI uri) {
        String host = value(uri.getHost()).toLowerCase(Locale.ROOT);
        return (host.equals("startengine.com") || host.equals("www.startengine.com"))
                && value(uri.getPath()).matches("/offering/[A-Za-z0-9._-]+/?");
    }

    private static String exclusion(Status status, String exemption) {
        if (!status.actionable()) return "Offering is not confirmed active";
        if ("REG_A".equals(exemption)) return "Reg A+ is outside the V1.1.2 Reg CF feed";
        if ("REG_D".equals(exemption)) return "Reg D is outside the non-accredited Reg CF feed";
        return "Reg CF exemption is not confirmed";
    }

    private static String stripQuery(URI uri) { return uri.getScheme() + "://" + uri.getHost() + uri.getPath(); }
    private static String pathId(URI uri) { return uri.getPath().replaceAll("^/+|/+$", "").replaceFirst("^offering/", ""); }
    private static String first(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value.trim(); return null; }
    private static String value(String value) { return value == null ? "" : value; }
    private static String lower(String value) { return value(value).toLowerCase(Locale.ROOT); }
    private static void put(Map<String, String> values, String key, String value) { if (value != null && !value.isBlank()) values.put(key, value); }
    private static String safe(RuntimeException error) { String value = error.getMessage(); return value == null ? error.getClass().getSimpleName() : value.replaceAll("https?://\\S+", "[external URL]"); }

    record Listing(String externalId, String name, String url, String listingText) { }
}
