package com.startupvalidationbot.diligence.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.startupvalidationbot.radar.CompanyIdentity;

final class CampaignHtml {
    private static final Pattern ANCHOR = Pattern.compile(
            "(?is)<a\\b[^>]*?href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>");
    private static final Pattern HEADING = Pattern.compile("(?is)<h[1-4][^>]*>(.*?)</h[1-4]>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Set<String> NON_ISSUER_DOMAINS = Set.of(
            "wefunder.com", "republic.com", "startengine.com", "sec.gov", "fonts.googleapis.com",
            "fonts.gstatic.com", "cloudflare.com", "stripe.com");

    private CampaignHtml() { }

    static List<Link> links(String html) {
        List<Link> values = new ArrayList<>();
        Matcher matcher = ANCHOR.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String body = matcher.group(2);
            String name = first(body, HEADING);
            if (name == null) name = text(body);
            values.add(new Link(matcher.group(1).trim(), bounded(name, 300), bounded(text(body), 1000)));
        }
        return values;
    }

    static String pageName(String html) {
        String value = first(html, HEADING);
        if (value == null) value = first(html, TITLE);
        if (value == null) return null;
        return bounded(text(value)
                .replaceFirst("(?i)^\\s*(?:invest|reserve)\\s+in\\s+", "")
                .replaceFirst("(?i)\\s*[|\\-]\\s*(?:Wefunder|Republic|StartEngine).*$", "")
                .trim(), 300);
    }

    static String issuerDomain(String platform, String html) {
        Set<String> candidates = new LinkedHashSet<>();
        for (Link link : links(html)) {
            if (!link.href().matches("(?i)^https://.*")) continue;
            String domain = CompanyIdentity.normalizeDomain(link.href());
            if (domain == null || NON_ISSUER_DOMAINS.stream().anyMatch(domain::endsWith)) continue;
            String label = (link.name() + " " + link.text()).toLowerCase(Locale.ROOT);
            if (label.matches(".*\\b(company website|official website|visit website|visit site|homepage)\\b.*")) {
                return domain;
            }
            candidates.add(domain);
        }
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    static String status(String text) {
        String lower = text(text).toLowerCase(Locale.ROOT);
        if (lower.contains("successfully funded") || lower.contains("funded badge")) return "FUNDED";
        if (lower.contains("offering closed") || lower.contains("campaign ended")) return "CLOSED";
        if (lower.contains("accepting reservations") || lower.contains("reserve")) return "RESERVATION";
        if (lower.contains("invest now") || lower.contains("days left") || lower.contains("left to invest")) {
            return "ACTIVE";
        }
        return "UNKNOWN";
    }

    static boolean plausibleName(String name, CampaignDiscoveryDomain.CampaignIdentity identity) {
        String candidate = CompanyIdentity.normalizeName(name);
        if (candidate.isBlank()) return false;
        Set<String> names = new LinkedHashSet<>();
        addName(names, identity.companyName());
        addName(names, identity.issuerName());
        identity.aliases().forEach(value -> addName(names, value));
        if (names.contains(candidate)) return true;
        return names.stream().anyMatch(value -> similarity(value, candidate) >= 0.75);
    }

    static double similarity(String left, String right) {
        if (left.equals(right)) return 1;
        Set<String> a = new LinkedHashSet<>(List.of(left.split("\\s+")));
        Set<String> b = new LinkedHashSet<>(List.of(right.split("\\s+")));
        if (a.isEmpty() || b.isEmpty()) return 0;
        long intersection = a.stream().filter(b::contains).count();
        return (2d * intersection) / (a.size() + b.size());
    }

    static String text(String html) {
        if (html == null) return "";
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    static URI absolute(URI base, String href) {
        try { return base.resolve(href); }
        catch (IllegalArgumentException error) { return null; }
    }

    private static String first(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html == null ? "" : html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void addName(Set<String> names, String value) {
        String normalized = CompanyIdentity.normalizeName(value);
        if (!normalized.isBlank()) names.add(normalized);
    }

    private static String bounded(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(max, trimmed.length()));
    }

    record Link(String href, String name, String text) { }
}
