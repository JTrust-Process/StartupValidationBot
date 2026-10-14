package com.startupvalidationbot.diligence.discovery;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import com.startupvalidationbot.radar.source.PublicSourceUrlPolicy;
import com.startupvalidationbot.radar.source.SourceFetchException;

/** At most one homepage, with robots checks and same-host HTTPS redirects only. */
@Component
public class IssuerWebsiteClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private long lastRequest;

    public String homepage(String website) {
        try {
            URI uri = require(website);
            String host = uri.getHost();
            HttpResponse<InputStream> robots = request(uri.resolve("/robots.txt"));
            String rules;
            try (InputStream input = robots.body()) {
                if (robots.statusCode() != 200 && robots.statusCode() != 404) throw new IllegalStateException("Issuer robots policy unavailable");
                rules = body(input, 64_000);
            }
            if (robots.statusCode() == 200 && disallowed(rules, uri.getRawPath())) throw new IllegalStateException("Issuer robots policy disallows homepage inspection");
            for (int redirects = 0; redirects <= 2; redirects++) {
                if (robots.statusCode() == 200 && disallowed(rules, uri.getRawPath())) throw new IllegalStateException("Issuer robots policy disallows redirected path");
                HttpResponse<InputStream> response = request(uri);
                try (InputStream input = response.body()) {
                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        URI next = require(uri.resolve(response.headers().firstValue("Location").orElseThrow()).toString());
                        if (!next.getHost().equalsIgnoreCase(host)) throw new IllegalStateException("Issuer cross-host redirect not followed");
                        uri = next;
                        continue;
                    }
                    if (response.statusCode() != 200) throw new IllegalStateException("Issuer homepage HTTP " + response.statusCode());
                    if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).contains("text/html")) throw new IllegalStateException("Issuer homepage is not HTML");
                    String html = body(input, 1_000_000);
                    if (html.matches("(?is).*(verify you are human|checking your browser|cf-chl-|captcha|access denied).*")) throw new IllegalStateException("Issuer browser verification; no bypass attempted");
                    return html;
                }
            }
            throw new IllegalStateException("Issuer redirect bound reached");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Issuer inspection interrupted", error);
        } catch (java.io.IOException | SourceFetchException error) {
            throw new IllegalStateException("Issuer public homepage unavailable", error);
        }
    }

    public static boolean disallowed(String robots, String path) {
        return RobotsRules.disallowed(robots, path);
    }
    private synchronized HttpResponse<InputStream> request(URI uri) throws java.io.IOException, InterruptedException, SourceFetchException {
        require(uri.toString());
        long delay = 1000 - (System.currentTimeMillis() - lastRequest);
        if (delay > 0) Thread.sleep(delay);
        lastRequest = System.currentTimeMillis();
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "StartupIntelligence/1.2B public offering research")
                .header("Accept", "text/html,text/plain").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }
    private static URI require(String value) throws SourceFetchException, java.io.IOException {
        URI uri = PublicSourceUrlPolicy.requirePublicHttpUrl(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getPort() != -1 || uri.getFragment() != null) throw new SourceFetchException("Issuer URL must use public HTTPS without custom ports or fragments");
        for (var address : java.net.InetAddress.getAllByName(uri.getHost())) {
            byte[] bytes = address.getAddress();
            if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc || bytes.length == 4 && ((bytes[0] & 255) == 0 || (bytes[0] & 255) == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127)) throw new SourceFetchException("Issuer URL resolves to a non-public address");
        }
        return uri;
    }
    private static String body(InputStream input, int bound) throws java.io.IOException {
        byte[] bytes = input.readNBytes(bound + 1);
        if (bytes.length > bound) throw new IllegalStateException("Issuer response exceeds inspection bound");
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
