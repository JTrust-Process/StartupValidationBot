package com.startupvalidationbot.offering;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecFilingClient {
    private static final Set<String> HOSTS = Set.of("www.sec.gov", "sec.gov", "data.sec.gov");
    private final HttpClient client;
    private final String userAgent;
    private final long intervalNanos;
    private long nextRequestAt;

    @org.springframework.beans.factory.annotation.Autowired
    public SecFilingClient(@Value("${offering.sec.user-agent:}") String userAgent,
            @Value("${offering.sec.requests-per-second:2}") double requestsPerSecond) {
        this(userAgent, requestsPerSecond, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    SecFilingClient(String userAgent, double requestsPerSecond, HttpClient client) {
        this.userAgent = userAgent == null ? "" : userAgent.trim();
        double boundedRate = Math.max(0.2, Math.min(requestsPerSecond, 2.0));
        this.intervalNanos = (long) (1_000_000_000d / boundedRate);
        this.client = client;
    }

    public byte[] get(String url, int maxBytes) {
        if (userAgent.isBlank()) {
            throw new IllegalStateException("SEC_EDGAR_USER_AGENT is required for live SEC access");
        }
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
            throw new IllegalArgumentException("SEC client only permits official HTTPS SEC hosts");
        }
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            throttle();
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", userAgent)
                        .header("Accept", "*/*").GET().build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    if (response.body().length > maxBytes) throw new IllegalStateException("SEC response exceeded size limit");
                    return response.body();
                }
                if (status != 429 && status < 500) throw new IllegalStateException("SEC returned HTTP " + status);
                failure = new IllegalStateException("SEC returned retryable HTTP " + status);
            } catch (IOException error) {
                failure = new IllegalStateException("SEC request failed", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("SEC request interrupted", error);
            }
            sleep(500L * (attempt + 1));
        }
        throw failure == null ? new IllegalStateException("SEC request failed") : failure;
    }

    public String getText(String url, int maxBytes) {
        return new String(get(url, maxBytes), java.nio.charset.StandardCharsets.UTF_8);
    }

    private synchronized void throttle() {
        long now = System.nanoTime();
        if (now < nextRequestAt) sleep(Math.max(1, (nextRequestAt - now) / 1_000_000));
        nextRequestAt = System.nanoTime() + intervalNanos;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SEC request interrupted", error);
        }
    }
}
