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
import java.util.Set;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.diligence.platform.PlatformHttpClient;
import com.startupvalidationbot.diligence.platform.PlatformUrlPolicy;
import com.startupvalidationbot.offering.OfferingTermNormalizer;

@Component
public class RepublicNativeOfferingAdapter implements NativeOfferingSourceAdapter {
    static final URI DIRECTORY = URI.create("https://republic.com/companies");
    private static final Set<String> EXCLUDED_PATHS = Set.of("", "companies", "investment-opportunities",
            "login", "signup", "help", "about", "insights", "events", "raise", "investors",
            "portfolio", "trading", "wallet", "mobile", "learn", "privacy", "terms");

    private final PlatformHttpClient http;

    public RepublicNativeOfferingAdapter(PlatformHttpClient http) { this.http = http; }

    @Override public String source() { return "REPUBLIC"; }
    @Override public String capability() { return "PUBLIC_LIVE_DIRECTORY"; }

    @Override
    public SourceResult discover(int maxCandidates, int maxDetailRequests) {
        try {
            String html = http.get(source(), DIRECTORY);
            if (OfferingHtml.challenge(html)) {
                return SourceResult.failed(source(), capability(),
                        "Republic returned an access-verification page; no bypass was attempted.", 1);
            }
            List<NativeOfferingCandidate> candidates = parseDirectory(html, maxCandidates, LocalDateTime.now());
            return new SourceResult(source(), capability(), "OK", true, 1, 0,
                    candidates, List.of());
        } catch (RuntimeException error) {
            return SourceResult.failed(source(), capability(), safe(error), 1);
        }
    }

    static List<NativeOfferingCandidate> parseDirectory(String html, int limit, LocalDateTime retrievedAt) {
        Map<String, NativeOfferingCandidate> candidates = new LinkedHashMap<>();
        for (OfferingHtml.Link link : OfferingHtml.links(html)) {
            URI resolved = OfferingHtml.absolute(DIRECTORY, link.href());
            if (resolved == null || !campaignPath(resolved)) continue;
            String text = link.text();
            if (!looksLikeOffering(text)) continue;
            URI canonical;
            try { canonical = PlatformUrlPolicy.require("REPUBLIC", stripQuery(resolved)); }
            catch (IllegalArgumentException ignored) { continue; }
            String companyName = first(link.name(), OfferingHtml.slugName(canonical));
            if (blank(companyName)) continue;

            String exemption = exemption(text);
            LocalDate deadline = OfferingHtml.date(OfferingHtml.field(text, "",
                    "([A-Za-z]+ \\d{1,2}, \\d{4}|\\d{4}-\\d{2}-\\d{2})\\s+deadline"));
            Status status = withDeadline(status(text), deadline);
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("listingText", OfferingHtml.bounded(text, 1800));
            String rawSecurity = rawSecurity(text);
            String security = OfferingTermNormalizer.security(rawSecurity);
            put(evidence, "securityTypeRaw", rawSecurity);
            put(evidence, "securityType", security);
            put(evidence, "investorCount", OfferingHtml.field(text, "", "([0-9][0-9,]*)\\s+investors?"));
            put(evidence, "statusEvidence", status.name());
            if (!"REG_CF".equals(exemption)) put(evidence, "actionableExclusion", exclusion(text, exemption));

            NativeOfferingCandidate candidate = new NativeOfferingCandidate("REPUBLIC_DIRECTORY", "Republic",
                    pathId(canonical), companyName, canonical.toString(), null, null, status, exemption,
                    intermediary(text), security,
                    money(text, "raised|reserved|committed"),
                    money(text, "target(?: offering)? amount|target raise|funding goal"),
                    money(text, "maximum(?: offering)? amount|maximum raise|max raise"), valuation(text),
                    money(text, "min\\.? investment|minimum investment"),
                    null, deadline,
                    null, OfferingHtml.bounded(text, 800), null, null, null, null, null, null,
                    Map.copyOf(evidence), retrievedAt);
            candidates.putIfAbsent(canonical.toString(), candidate);
            if (candidates.size() >= Math.max(1, Math.min(limit, 25))) break;
        }
        return List.copyOf(candidates.values());
    }

    private static boolean campaignPath(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        return !path.contains("/") && !EXCLUDED_PATHS.contains(path.toLowerCase(Locale.ROOT));
    }

    private static boolean looksLikeOffering(String text) {
        String value = lower(text);
        return value.contains("reg cf") || value.contains("reg a+") || value.contains("reg d")
                || value.contains("registered fund") || value.contains("valuation cap")
                || value.contains("minimum investment") || value.contains("min. investment");
    }

    private static String exemption(String text) {
        String value = lower(text);
        if (value.contains("regulation crowdfunding") || value.contains("reg cf")) return "REG_CF";
        if (value.contains("reg a+") || value.contains("regulation a")) return "REG_A";
        if (value.contains("reg d") || value.contains("506(c)")) return "REG_D";
        if (value.contains("registered fund") || value.contains("1940 act")) return "FUND";
        return "UNKNOWN";
    }

    private static Status status(String text) {
        String value = lower(text);
        if (value.contains("accepting reservations") || value.contains(" reserved ")) return Status.RESERVATION;
        if (value.matches(".*\\b(?:hours?|days?) (?:left|remaining)\\b.*") || value.contains("left to invest")) {
            return Status.CLOSING_SOON;
        }
        if (value.contains("offering closed") || value.contains("campaign ended")) return Status.CLOSED;
        if (value.contains(" raised") || value.contains("investors") || value.contains("live opportunity")) {
            return Status.ACTIVE;
        }
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

    private static String intermediary(String text) {
        String value = lower(text);
        if (value.contains("republic funding portal")) return "Republic Funding Portal";
        if (value.contains("capital r")) return "Capital R";
        return "Republic";
    }

    private static String rawSecurity(String text) {
        return OfferingHtml.field(text, "", "([A-Za-z][A-Za-z /-]{1,80})\\s+security type");
    }

    private static java.math.BigDecimal money(String text, String label) {
        String raw = OfferingHtml.field(text, "", "(\\$[0-9][0-9,.]*[KMB]?)\\s+(?:" + label + ")");
        if (raw == null) raw = OfferingHtml.field(text, "(?:" + label + ")",
                "(\\$[0-9][0-9,.]*[KMB]?)");
        return OfferingTermNormalizer.money(raw);
    }

    private static String valuation(String text) {
        String value = OfferingHtml.field(text, "", "(\\$[0-9][0-9,.]*[KMB]?)\\s+valuation cap");
        return value == null ? null : value + " valuation cap";
    }

    private static String exclusion(String text, String exemption) {
        String value = lower(text);
        if (value.contains("accredited only") || "REG_D".equals(exemption)) return "Accredited-only or Reg D";
        if ("REG_A".equals(exemption)) return "Reg A+ is outside the V1.1.2 Reg CF feed";
        if ("FUND".equals(exemption) || value.contains(" fund")) return "Fund is outside the V1.1.2 company feed";
        if (value.contains("spv")) return "SPV is outside the V1.1.2 company feed";
        return "Not a confirmed Reg CF company offering";
    }

    private static String stripQuery(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
    }

    private static String pathId(URI uri) {
        String path = uri.getPath().replaceAll("^/+|/+$", "");
        return path.isBlank() ? uri.toString() : path;
    }

    private static String first(String first, String second) { return blank(first) ? second : first.trim(); }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void put(Map<String, String> values, String key, String value) { if (!blank(value)) values.put(key, value); }
    private static String safe(RuntimeException error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName()
                : value.replaceAll("https?://\\S+", "[external URL]");
    }
}
