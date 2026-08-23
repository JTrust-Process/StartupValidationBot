package com.startupvalidationbot.radar.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class RouterRadarAiProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void constructsOfficialResponsesEndpoint() {
        assertThat(RouterRadarAiProvider.responsesEndpoint("https://api.router.com/v1"))
                .isEqualTo(URI.create("https://api.router.com/v1/responses"));
        assertThat(RouterRadarAiProvider.responsesEndpoint("https://example.test/custom/"))
                .isEqualTo(URI.create("https://example.test/custom/responses"));
    }

    @Test
    void sendsResponsesRequestAndCapturesRoutedModelTelemetry() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("x-request-id", "req_123");
            exchange.getResponseHeaders().set("x-trace-id", "trace_456");
            respond(exchange, 200, response("provider/actual-model", validOutput(), 80, 30));
        });
        server.start();

        RadarAiResponse result = provider(0).analyzeCompany(input());

        assertThat(authorization).hasValue("Bearer test-router-key");
        assertThat(result.model()).isEqualTo("router/benchmark-alias");
        assertThat(result.actualModel()).isEqualTo("provider/actual-model");
        assertThat(result.inputTokens()).isEqualTo(80);
        assertThat(result.outputTokens()).isEqualTo(30);
        assertThat(result.requestId()).isEqualTo("req_123");
        assertThat(result.traceId()).isEqualTo("trace_456");
        assertThat(result.providerCostUsd()).isNull();

        JsonNode request = mapper.readTree(requestBody.get());
        assertThat(request.path("model").asText()).isEqualTo("router/benchmark-alias");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.path("text").path("format").path("strict").asBoolean()).isTrue();
        assertThat(request.path("input").asText()).contains("Public Radar data", "Public launch description")
                .doesNotContain("thesis", "mainRisk", "offeringDocument", "userNotes", "bank info");
    }

    @Test
    void retriesSchemaViolationThenReturnsValidatedOutput() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            Map<String, Object> output = requests.incrementAndGet() == 1 ? Map.of() : validOutput();
            respond(exchange, 200, response("provider/model", output, 10, 5));
        });
        server.start();

        RadarAiResponse result = provider(1).generateDeepDive(input());

        assertThat(requests).hasValue(2);
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.model()).isEqualTo("provider/pinned-deep-model");
        assertThat(result.output().summary()).isEqualTo("Public summary");
    }

    @Test
    void invalidCredentialsDoNotRetryAndRedactProviderMessage() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 401, """
                    {"error":{"type":"authentication_error","code":"invalid_key",
                    "message":"Bad router_secret_value Bearer private-token"}}
                    """);
        });
        server.start();

        assertThatThrownBy(() -> provider(2).analyzeCompany(input()))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> {
                    RadarAiException aiError = (RadarAiException) error;
                    assertThat(aiError.errorType()).isEqualTo("INVALID_CREDENTIALS");
                    assertThat(aiError.httpStatus()).isEqualTo(401);
                    assertThat(aiError.attempts()).isEqualTo(1);
                    assertThat(aiError.getMessage()).doesNotContain("router_secret_value", "private-token")
                            .contains("<redacted-key>", "Bearer <redacted>");
                });
        assertThat(requests).hasValue(1);
    }

    @Test
    void retriesRateLimitsAndServerErrorsWithinConfiguredBound() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            int current = requests.incrementAndGet();
            int status = current == 1 ? 429 : 503;
            respond(exchange, status, "{\"error\":{\"message\":\"temporary\"}}");
        });
        server.start();

        assertThatThrownBy(() -> provider(1).analyzeCompany(input()))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> {
                    RadarAiException aiError = (RadarAiException) error;
                    assertThat(aiError.errorType()).isEqualTo("PROVIDER_UNAVAILABLE");
                    assertThat(aiError.attempts()).isEqualTo(2);
                });
        assertThat(requests).hasValue(2);
    }

    @Test
    void malformedEnvelopeRetriesAndFailsClearly() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{\"model\":\"provider/model\",\"output\":[]}");
        });
        server.start();

        assertThatThrownBy(() -> provider(1).analyzeCompany(input()))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> assertThat(((RadarAiException) error).errorType())
                        .isEqualTo("MALFORMED_RESPONSE"));
        assertThat(requests).hasValue(2);
    }

    @Test
    void missingKeyAndModelFailBeforeNetworkCall() {
        URI endpoint = URI.create("http://127.0.0.1:1/v1/responses");
        RouterRadarAiProvider missingKey = new RouterRadarAiProvider(mapper, HttpClient.newHttpClient(), endpoint,
                "", "alias", "deep", 0, Duration.ofSeconds(1));
        RouterRadarAiProvider missingModel = new RouterRadarAiProvider(mapper, HttpClient.newHttpClient(), endpoint,
                "key", "", "deep", 0, Duration.ofSeconds(1));

        assertThatThrownBy(() -> missingKey.analyzeCompany(input())).isInstanceOf(RadarAiException.class)
                .satisfies(error -> assertThat(((RadarAiException) error).errorType())
                        .isEqualTo("MISSING_CREDENTIALS"));
        assertThatThrownBy(() -> missingModel.analyzeCompany(input())).isInstanceOf(RadarAiException.class)
                .satisfies(error -> assertThat(((RadarAiException) error).errorType())
                        .isEqualTo("MODEL_UNAVAILABLE"));
    }

    @Test
    void timeoutIsBoundedAndRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            requests.incrementAndGet();
            try {
                Thread.sleep(200);
                respond(exchange, 200, response("provider/model", validOutput(), 1, 1));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                exchange.close();
            }
        });
        server.start();

        assertThatThrownBy(() -> provider(1, Duration.ofMillis(40)).analyzeCompany(input()))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> {
                    RadarAiException aiError = (RadarAiException) error;
                    assertThat(aiError.errorType()).isEqualTo("TIMEOUT");
                    assertThat(aiError.attempts()).isEqualTo(2);
                });
        assertThat(requests.get()).isBetween(0, 2);
    }

    private RouterRadarAiProvider provider(int retries) {
        return provider(retries, Duration.ofSeconds(5));
    }

    private RouterRadarAiProvider provider(int retries, Duration timeout) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
        return new RouterRadarAiProvider(mapper, HttpClient.newHttpClient(), endpoint, "test-router-key",
                "router/benchmark-alias", "provider/pinned-deep-model", retries, timeout);
    }

    private String response(String model, Map<String, Object> output, int inputTokens, int outputTokens)
            throws IOException {
        return mapper.writeValueAsString(Map.of(
                "model", model,
                "output", List.of(Map.of("type", "message", "content", List.of(
                        Map.of("type", "output_text", "text", mapper.writeValueAsString(output))))),
                "usage", Map.of("input_tokens", inputTokens, "output_tokens", outputTokens)));
    }

    private static PublicCompanyAnalysisInput input() {
        return new PublicCompanyAnalysisInput(4L, "Public Co", "public.example", "https://public.example",
                "Public launch description", "Software", List.of("Automation"), "New York", 2025, "Unknown",
                List.of("Public launch"), List.of(), List.of(), List.of(),
                List.of(new PublicCompanyAnalysisInput.PublicSourceEvidence("RSS", "Launch",
                        "https://news.example/launch", "Public launch description")), 1);
    }

    private static Map<String, Object> validOutput() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summary", "Public summary");
        output.put("sector", "Software");
        output.put("problem", "Unknown");
        output.put("solution", "Automation");
        output.put("businessModel", "Unknown");
        output.put("categories", List.of("Automation"));
        output.put("stage", "Unknown");
        output.put("founders", List.of());
        output.put("fundingSummary", "Unknown");
        output.put("investors", List.of());
        output.put("tractionSignals", List.of());
        output.put("technicalDifferentiation", List.of());
        output.put("marketSignals", List.of());
        output.put("interestingSignals", List.of("Public launch"));
        output.put("risks", List.of("Limited evidence"));
        output.put("bullCase", List.of());
        output.put("bearCase", List.of());
        output.put("whyItMatters", "Public launch signal");
        output.put("whyIShouldCare", "Unknown");
        output.put("watchTriggers", List.of("New public source"));
        output.put("radarScoreInputs", List.of("Public launch"));
        output.put("personalScoreInputs", List.of());
        output.put("investmentAccessibility", "Unknown");
        output.put("careerAngle", "Unknown");
        output.put("unansweredQuestions", List.of("What traction is verified?"));
        output.put("confidence", "LOW");
        output.put("facts", List.of("A public launch source was supplied."));
        output.put("inferences", List.of("Product relevance is inferred."));
        output.put("sourceUrls", List.of("https://news.example/launch"));
        return output;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
