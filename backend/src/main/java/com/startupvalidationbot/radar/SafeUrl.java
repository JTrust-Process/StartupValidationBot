package com.startupvalidationbot.radar;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redaction for configured source URLs.
 *
 * A feed URL can legitimately carry a credential ("?apikey=..."), and source URLs are surfaced by
 * the admin export and the admin source list. Only the scheme, host, port and path are ever
 * disclosed; user info, query strings and fragments are dropped.
 */
public final class SafeUrl {
    private static final Pattern URL_IN_TEXT = Pattern.compile("\\bhttps?://\\S+", Pattern.CASE_INSENSITIVE);

    private SafeUrl() {
    }

    /** Returns the URL without credentials, query or fragment. Null and blank pass through. */
    public static String redact(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) return value;

            StringBuilder redacted = new StringBuilder(uri.getScheme().toLowerCase()).append("://")
                    .append(uri.getHost().toLowerCase());
            if (uri.getPort() >= 0) redacted.append(':').append(uri.getPort());
            if (uri.getRawPath() != null && !uri.getRawPath().isBlank()) redacted.append(uri.getRawPath());
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
                redacted.append("?<redacted>");
            }
            return redacted.toString();
        } catch (IllegalArgumentException error) {
            return value;
        }
    }

    /** Redacts every URL embedded in free-form text such as a stored fetch error. */
    public static String redactUrlsIn(String text) {
        if (text == null || text.isBlank()) return text;
        Matcher matcher = URL_IN_TEXT.matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(redact(matcher.group())));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
