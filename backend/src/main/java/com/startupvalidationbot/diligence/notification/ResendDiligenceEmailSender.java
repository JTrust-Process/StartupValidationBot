package com.startupvalidationbot.diligence.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ResendDiligenceEmailSender implements DiligenceEmailSender {
    private final String provider;
    private final String apiKey;
    private final String from;
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI endpoint;

    @Autowired
    public ResendDiligenceEmailSender(@Value("${email.provider:preview}") String provider,
            @Value("${resend.api-key:}") String apiKey, @Value("${resend.from:}") String from,
            ObjectMapper json) {
        this(provider, apiKey, from, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    ResendDiligenceEmailSender(String provider, String apiKey, String from, ObjectMapper json, HttpClient client) {
        this(provider, apiKey, from, json, client, URI.create("https://api.resend.com/emails"));
    }

    ResendDiligenceEmailSender(String provider, String apiKey, String from, ObjectMapper json, HttpClient client,
            URI endpoint) {
        this.provider = provider == null ? "" : provider.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
        this.client = client;
        this.json = json;
        this.endpoint = endpoint;
    }

    @Override public boolean configured() {
        return "resend".equalsIgnoreCase(provider) && !apiKey.isBlank() && !from.isBlank();
    }

    @Override
    public SendResult send(String to, String subject, String text, String html) {
        if (!"resend".equalsIgnoreCase(provider)) return new SendResult(false, null, "EMAIL_PROVIDER is not resend");
        if (apiKey.isBlank()) return new SendResult(false, null, "RESEND_API_KEY is missing");
        if (from.isBlank()) return new SendResult(false, null, "RESEND_FROM is missing");
        if (to == null || to.isBlank()) return new SendResult(false, null, "STARTUP_INTELLIGENCE_EMAIL_RECIPIENT is missing");
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String body = json.writeValueAsString(Map.of("from", from, "to", new String[] { to.trim() },
                        "subject", subject, "text", text, "html", html));
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode payload = json.readTree(response.body());
                    return new SendResult(true, payload.path("id").asText(null), null);
                }
                String error = "Resend returned HTTP " + response.statusCode();
                if (response.statusCode() < 500 && response.statusCode() != 429) return new SendResult(false, null, error);
                failure = new IllegalStateException(error);
            } catch (IOException error) { failure = new IllegalStateException("Resend request failed", error); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); return new SendResult(false, null, "Resend request interrupted"); }
            if (attempt == 0) sleep(500L);
        }
        return new SendResult(false, null, failure == null ? "Resend request failed" : failure.getMessage());
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
