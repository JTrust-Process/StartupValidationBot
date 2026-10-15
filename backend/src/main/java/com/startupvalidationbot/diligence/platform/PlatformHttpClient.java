package com.startupvalidationbot.diligence.platform;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlatformHttpClient {
    private final HttpClient client;
    private final int maxBytes;
    private final long intervalNanos;
    private final Map<String, Long> nextRequestAt = new ConcurrentHashMap<>();
    private final Map<String, RobotsPolicy> robots = new ConcurrentHashMap<>();
    private final Map<String, Long> rateLimitedUntil = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private record RobotsPolicy(String text, String failure, String outcome, long expiresAt) { }
    public static class FetchFailure extends IllegalStateException {
        private final String outcome;
        public FetchFailure(String outcome, String message) { super(message); this.outcome = outcome; }
        public String outcome() { return outcome; }
    }
    public int requests(String platform) { return requestCounts.getOrDefault(platform, new java.util.concurrent.atomic.AtomicInteger()).get(); }

    @Autowired
    public PlatformHttpClient(@Value("${diligence.platform.max-response-bytes:2000000}") int maxBytes,
            @Value("${diligence.platform.requests-per-second:1}") double requestsPerSecond) {
        this(maxBytes, requestsPerSecond, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    PlatformHttpClient(int maxBytes, double requestsPerSecond, HttpClient client) {
        this.maxBytes = Math.max(50_000, Math.min(maxBytes, 3_000_000));
        this.intervalNanos = (long) (1_000_000_000d / Math.max(0.1, Math.min(requestsPerSecond, 1.0)));
        this.client = client;
    }

    public String get(String platform, URI uri) {
        URI safe = PlatformUrlPolicy.require(platform, uri.toString());
        if (rateLimitedUntil.getOrDefault(platform, 0L) > System.currentTimeMillis())
            throw new FetchFailure("HTTP_429_CACHED", "Platform rate limit remains active; no new public request attempted");
        requireRobots(platform, safe);
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            throttle(platform);
            try {
                HttpResponse<InputStream> response = request(platform, safe);
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        throw new FetchFailure("REDIRECT_REJECTED", "Platform redirect rejected; campaign URL must be canonical");
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        byte[] bytes = body.readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) throw new IllegalStateException("Platform response exceeded size limit");
                        String type = response.headers() == null ? "" : response.headers().firstValue("Content-Type").orElse("");
                        if (!type.toLowerCase(java.util.Locale.ROOT).matches(".*(?:text/html|application/xhtml\\+xml).*"))
                            throw new FetchFailure("UNSUPPORTED_CONTENT", "Platform response was not public HTML");
                        String html = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        String wall = PublicCampaignStatus.wall(html);
                        if (wall != null) throw new FetchFailure(wall, "Public platform returned a login wall or browser challenge; no bypass attempted");
                        return html;
                    }
                    if (response.statusCode() == 429) {
                        String retryAfter = response.headers() == null ? "" : response.headers().firstValue("Retry-After").orElse("");
                        long delay = Duration.ofHours(2).toMillis();
                        try { delay = Math.max(delay, Math.multiplyExact(Long.parseLong(retryAfter), 1000)); }
                        catch (RuntimeException error) {
                            try { delay = Math.max(delay, java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()); }
                            catch (RuntimeException ignored) { }
                        }
                        long until;
                        try { until = Math.addExact(System.currentTimeMillis(), delay); }
                        catch (ArithmeticException error) { until = Long.MAX_VALUE; }
                        rateLimitedUntil.put(platform, until);
                    }
                    if (response.statusCode() < 500) {
                        throw new FetchFailure("HTTP_" + response.statusCode(), "Platform returned HTTP " + response.statusCode());
                    }
                }
                failure = new FetchFailure("HTTP_" + response.statusCode(), "Platform temporarily unavailable (HTTP " + response.statusCode() + ")");
            } catch (IOException error) {
                failure = new FetchFailure(error instanceof java.net.http.HttpTimeoutException ? "TIMEOUT" : "NETWORK_ERROR", "Platform request failed");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Platform request interrupted", error);
            }
            if (attempt == 0) sleep(500L);
        }
        throw failure == null ? new IllegalStateException("Platform request failed") : failure;
    }

    private synchronized void requireRobots(String platform, URI uri) {
        String origin = uri.getScheme() + "://" + uri.getHost();
        long now = System.currentTimeMillis();
        RobotsPolicy policy = robots.get(origin);
        if (policy == null || policy.expiresAt() <= now) {
            try {
                throttle(platform);
                var response = request(platform, URI.create(origin + "/robots.txt"));
                try (InputStream input = response.body()) {
                    byte[] bytes = input.readNBytes(64_001);
                    if (bytes.length > 64_000) throw new FetchFailure("ROBOTS_RESPONSE_TOO_LARGE", "Platform robots response exceeds limit; public page was not requested");
                    if (response.statusCode() != 200 && response.statusCode() != 404)
                        throw new FetchFailure("ROBOTS_HTTP_" + response.statusCode(), "Platform robots policy returned HTTP " + response.statusCode() + "; public page was not requested");
                    String text = response.statusCode() == 404 ? "" : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (text.stripLeading().startsWith("<") || PublicCampaignStatus.wall(text) != null)
                        throw new FetchFailure("ROBOTS_UNAVAILABLE", "Platform robots response is not a usable public policy");
                    policy = new RobotsPolicy(text, null, null, now + Duration.ofHours(24).toMillis());
                }
            } catch (IOException | InterruptedException | RuntimeException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                String outcome = error instanceof FetchFailure failure ? failure.outcome() : "ROBOTS_UNAVAILABLE";
                policy = new RobotsPolicy("", error instanceof FetchFailure ? error.getMessage() : "Platform robots policy unavailable; no bypass attempted", outcome, now + Duration.ofHours(2).toMillis());
            }
            robots.put(origin, policy);
        }
        if (policy.failure() != null) throw new FetchFailure(policy.outcome(), policy.failure());
        if (com.startupvalidationbot.diligence.discovery.IssuerWebsiteClient.disallowed(policy.text(), uri.getRawPath()))
            throw new FetchFailure("ROBOTS_DENIED", "Platform robots policy disallows this public path; no page requested");
    }
    private HttpResponse<InputStream> request(String platform, URI uri) throws IOException, InterruptedException {
        URI checked = PlatformUrlPolicy.require(platform, uri.toString());
        requestCounts.computeIfAbsent(platform, ignored -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        return client.send(HttpRequest.newBuilder(checked).timeout(Duration.ofSeconds(20))
                .header("Accept", "text/html,application/xhtml+xml,text/plain")
                .header("User-Agent", "StartupIntelligence/1.2D public-diligence").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    private void throttle(String platform) {
        synchronized (nextRequestAt) {
            long now = System.nanoTime();
            long next = nextRequestAt.getOrDefault(platform, 0L);
            if (now < next) sleep(Math.max(1, (next - now) / 1_000_000));
            nextRequestAt.put(platform, System.nanoTime() + intervalNanos);
        }
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("Platform request interrupted", error); }
    }
}
