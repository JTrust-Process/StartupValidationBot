package com.startupvalidationbot.diligence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class ResendDiligenceEmailSenderTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test
    void returnsMessageIdAfterSuccessfulResendDelivery() throws Exception {
        AtomicInteger calls = serve(200, "{\"id\":\"email_123\"}");
        var result = sender().send("person@example.com", "Research shortlist", "text", "<p>text</p>");
        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("email_123");
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryPermanentResendFailure() throws Exception {
        AtomicInteger calls = serve(422, "{\"message\":\"invalid sender\"}");
        var result = sender().send("person@example.com", "Research shortlist", "text", "<p>text</p>");
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("Resend returned HTTP 422");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesTransientFailureOnlyTwice() throws Exception {
        AtomicInteger calls = serve(503, "{\"message\":\"temporary\"}");
        var result = sender().send("person@example.com", "Research shortlist", "text", "<p>text</p>");
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("Resend returned HTTP 503");
        assertThat(calls).hasValue(2);
    }

    @Test
    void refusesMissingServerOnlyConfiguration() {
        var sender = new ResendDiligenceEmailSender("preview", "", "", new ObjectMapper(), HttpClient.newHttpClient());
        assertThat(sender.configured()).isFalse();
        assertThat(sender.send("person@example.com", "subject", "text", "html").error())
                .isEqualTo("EMAIL_PROVIDER is not resend");
    }

    private AtomicInteger serve(int status, String body) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] value = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, value.length);
            exchange.getResponseBody().write(value);
            exchange.close();
        });
        server.start();
        return calls;
    }

    private ResendDiligenceEmailSender sender() {
        return new ResendDiligenceEmailSender("resend", "secret-test-key", "Startup Intelligence <from@example.com>",
                new ObjectMapper(), HttpClient.newHttpClient(),
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"));
    }
}
