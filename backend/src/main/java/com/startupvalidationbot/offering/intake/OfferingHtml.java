package com.startupvalidationbot.offering.intake;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OfferingHtml {
    private static final Pattern ANCHOR = Pattern.compile(
            "(?is)<a\\b([^>]*?)href=[\\\"']([^\\\"']+)[\\\"']([^>]*)>(.*?)</a>");
    private static final Pattern HEADING = Pattern.compile("(?is)<h[1-4][^>]*>(.*?)</h[1-4]>");
    private static final Pattern TITLE_ATTRIBUTE = Pattern.compile(
            "(?is)(?:aria-label|title)=[\\\"']([^\\\"']{2,300})[\\\"']");

    private OfferingHtml() { }

    static List<Link> links(String html) {
        List<Link> result = new ArrayList<>();
        Matcher matcher = ANCHOR.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String attributes = matcher.group(1) + " " + matcher.group(3);
            String body = matcher.group(4);
            String name = first(body, HEADING);
            if (blank(name)) name = first(attributes, TITLE_ATTRIBUTE);
            result.add(new Link(matcher.group(2).trim(), clean(name), text(body)));
        }
        return result;
    }

    static String pageName(String html) {
        String name = first(html, HEADING);
        if (blank(name)) name = field(text(html), "(?:company|issuer|offering)", "([^|]{2,120})");
        return bounded(clean(name), 300);
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

    static String field(String text, String label, String value) {
        Matcher matcher = Pattern.compile("(?i)" + label + "\\s*[:\\-]?\\s*" + value).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    static BigDecimal money(String value) {
        if (value == null) return null;
        String cleaned = value.replace("$", "").replace(",", "").trim().toUpperCase(Locale.ROOT);
        BigDecimal multiplier = BigDecimal.ONE;
        if (cleaned.endsWith("K")) { multiplier = new BigDecimal("1000"); cleaned = chop(cleaned); }
        else if (cleaned.endsWith("M")) { multiplier = new BigDecimal("1000000"); cleaned = chop(cleaned); }
        else if (cleaned.endsWith("B")) { multiplier = new BigDecimal("1000000000"); cleaned = chop(cleaned); }
        try { return new BigDecimal(cleaned).multiply(multiplier); }
        catch (NumberFormatException error) { return null; }
    }

    static Integer integer(String value) {
        try { return value == null ? null : Integer.valueOf(value.replace(",", "")); }
        catch (NumberFormatException error) { return null; }
    }

    static LocalDate date(String value) {
        if (value == null) return null;
        try { return LocalDate.parse(value); } catch (RuntimeException ignored) { }
        try { return LocalDate.parse(value, DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)); }
        catch (RuntimeException ignored) { return null; }
    }

    static URI absolute(URI base, String href) {
        try { return base.resolve(href); } catch (RuntimeException error) { return null; }
    }

    static String slugName(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) return null;
        String[] parts = path.replaceAll("/+$", "").split("/");
        String slug = parts.length == 0 ? "" : parts[parts.length - 1];
        if (slug.isBlank()) return null;
        String value = slug.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ").trim();
        StringBuilder title = new StringBuilder();
        for (String word : value.split(" ")) {
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }

    static boolean challenge(String html) {
        String value = text(html).toLowerCase(Locale.ROOT);
        return value.contains("verify that you're not a robot")
                || value.contains("attention required")
                || value.contains("please enable cookies")
                || value.contains("captcha");
    }

    static String bounded(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(max, trimmed.length()));
    }

    private static String first(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String clean(String value) {
        return blank(value) ? null : text(value).replaceAll("\\s+", " ").trim();
    }

    private static String chop(String value) { return value.substring(0, value.length() - 1); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record Link(String href, String name, String text) { }
}
