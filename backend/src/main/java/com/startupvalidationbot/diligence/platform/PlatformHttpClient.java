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
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            throttle(platform);
            try {
                HttpRequest request = HttpRequest.newBuilder(safe).timeout(Duration.ofSeconds(20))
                        .header("Accept", "text/html,application/xhtml+xml,application/ld+json")
                        .header("User-Agent", "StartupIntelligence/1.1 public-diligence")
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() >= 300 && response.statusCode() < 400) {
                        throw new IllegalStateException("Platform redirect rejected; campaign URL must be canonical");
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        byte[] bytes = body.readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) throw new IllegalStateException("Platform response exceeded size limit");
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                    if (response.statusCode() != 429 && response.statusCode() < 500) {
                        throw new IllegalStateException("Platform returned HTTP " + response.statusCode());
                    }
                }
                failure = new IllegalStateException("Platform temporarily unavailable (HTTP " + response.statusCode() + ")");
            } catch (IOException error) {
                failure = new IllegalStateException("Platform request failed", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Platform request interrupted", error);
            }
            if (attempt == 0) sleep(500L);
        }
        throw failure == null ? new IllegalStateException("Platform request failed") : failure;
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
