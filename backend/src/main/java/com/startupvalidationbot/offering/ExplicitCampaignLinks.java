package com.startupvalidationbot.offering;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExplicitCampaignLinks {
    private static final Set<String> NON_CAMPAIGNS = Set.of("", "explore", "invest", "login", "signup", "about", "companies", "discover", "learn", "help", "blog", "privacy", "terms", "investors", "contact", "faq", "careers", "press", "search", "raise", "how-it-works", "terms-of-service");
    private ExplicitCampaignLinks() { }

    public static String canonical(String value) {
        try {
            URI uri = URI.create(value.replace("&amp;", "&").replaceAll("[.,;]+$", ""));
            String family = IntermediaryRegistry.familyForUrl(uri.toString());
            if (!List.of("Wefunder", "Republic", "StartEngine").contains(family == null ? "" : family)
                    || uri.getPort() != -1 || uri.getFragment() != null || uri.getRawQuery() != null) return null;
            String path = uri.getPath().replaceAll("^/+|/+$", "");
            String[] parts = path.split("/");
            if (NON_CAMPAIGNS.contains(path.toLowerCase(java.util.Locale.ROOT))) return null;
            if (family.equals("StartEngine") && !(parts.length == 2 && parts[0].equals("offering"))) return null;
            if (!family.equals("StartEngine") && parts.length != 1) return null;
            if (!path.matches("[A-Za-z0-9._/-]+")) return null;
            return new URI("https", null, uri.getHost().toLowerCase(java.util.Locale.ROOT), -1, "/" + path, null, null).toString();
        } catch (RuntimeException | java.net.URISyntaxException ignored) { return null; }
    }

    public static Set<String> find(String explicitlyObservedText) {
        Set<String> result = new LinkedHashSet<>();
        Matcher urls = Pattern.compile("https://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE).matcher(explicitlyObservedText);
        while (urls.find() && result.size() < 20) { String url = canonical(urls.group()); if (url != null) result.add(url); }
        return result;
    }

    public static Set<String> outboundAnchors(String html) {
        String visible = html.replaceAll("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)>", "");
        Set<String> links = new LinkedHashSet<>();
        Matcher anchors = Pattern.compile("(?is)<a\\b[^>]*\\bhref=[\"']([^\"']+)[\"'][^>]*>").matcher(visible);
        while (anchors.find() && links.size() < 20) { String url = canonical(anchors.group(1)); if (url != null) links.add(url); }
        return links;
    }
}
