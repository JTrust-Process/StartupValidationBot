package com.startupvalidationbot.offering;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

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
        URI uri = requireOfficialUrl(url);
        if (maxBytes < 1) throw new IllegalArgumentException("SEC response bound must be positive");
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            throttle();
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", userAgent)
                        .header("Accept", "*/*").GET().build();
                HttpResponse<byte[]> response = client.send(request, info -> new BoundedBody(maxBytes));
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    if (response.body().length > maxBytes) throw new DocumentTooLarge();
                    return response.body();
                }
                if (status != 429 && status < 500) throw new IllegalStateException("SEC returned HTTP " + status);
                failure = new IllegalStateException("SEC returned retryable HTTP " + status);
            } catch (IOException error) {
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof DocumentTooLarge tooLarge) throw tooLarge;
                }
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

    public static URI requireOfficialUrl(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOSTS.contains(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("SEC client only permits official HTTPS SEC hosts");
        }
        return uri;
    }

    public static final class DocumentTooLarge extends IllegalStateException {
        public DocumentTooLarge() { super("SEC response exceeded size limit"); }
    }

    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private Flow.Subscription subscription;
        private long received;
        private boolean failed;
        BoundedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> items) {
            if (failed) return;
            for (ByteBuffer item : items) received += item.remaining();
            if (received > limit) {
                failed = true; subscription.cancel(); delegate.onError(new DocumentTooLarge());
            } else delegate.onNext(items);
        }
        @Override public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
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
