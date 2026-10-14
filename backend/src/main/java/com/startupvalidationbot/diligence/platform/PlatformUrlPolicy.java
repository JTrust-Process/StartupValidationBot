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
            URI uri = URI.create(value);
            String normalized = platform.toUpperCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || !HOSTS.getOrDefault(normalized, Set.of()).contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unexpected " + platform + " campaign host");
            }
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getPort() != -1 || uri.getQuery() != null
                    || uri.getPath().matches("(?i).*(?:^|/)(?:login|signin|signup|register|checkout|invest|subscribe|payment|purchase|api|graphql)(?:/.*|$)")) {
                throw new IllegalArgumentException("Platform URL cannot contain credentials, ports, queries, or transaction/login paths");
            }
            PublicSourceUrlPolicy.requirePublicHttpUrl(value);
            for (var address : java.net.InetAddress.getAllByName(uri.getHost())) {
                byte[] bytes = address.getAddress();
                if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc || bytes.length == 4
                        && ((bytes[0] & 255) == 0 || (bytes[0] & 255) == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127))
                    throw new IllegalArgumentException("Platform address is not public");
            }
            return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), -1,
                    uri.getPath(), uri.getQuery(), null);
        } catch (SourceFetchException | java.net.URISyntaxException | java.net.UnknownHostException error) {
            throw new IllegalArgumentException("Campaign URL is not a permitted public platform URL", error);
        }
    }
}
