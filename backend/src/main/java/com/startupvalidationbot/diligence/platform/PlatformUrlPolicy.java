package com.startupvalidationbot.diligence.platform;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.startupvalidationbot.radar.source.PublicSourceUrlPolicy;
import com.startupvalidationbot.radar.source.SourceFetchException;

public final class PlatformUrlPolicy {
    private static final Map<String, Set<String>> HOSTS = Map.of(
            "WEFUNDER", Set.of("wefunder.com", "www.wefunder.com"),
            "REPUBLIC", Set.of("republic.com", "www.republic.com"),
            "STARTENGINE", Set.of("startengine.com", "www.startengine.com"));

    private PlatformUrlPolicy() { }

    public static URI require(String platform, String value) {
        try {
            URI uri = PublicSourceUrlPolicy.requirePublicHttpUrl(value);
            String normalized = platform.toUpperCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !HOSTS.getOrDefault(normalized, Set.of()).contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unexpected " + platform + " campaign host");
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Campaign URL cannot contain credentials or fragments");
            }
            return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), -1,
                    uri.getPath(), uri.getQuery(), null);
        } catch (SourceFetchException | java.net.URISyntaxException error) {
            throw new IllegalArgumentException("Campaign URL is not a permitted public platform URL", error);
        }
    }
}
