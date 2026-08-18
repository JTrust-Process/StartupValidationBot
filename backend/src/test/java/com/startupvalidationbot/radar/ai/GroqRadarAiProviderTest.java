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

class GroqRadarAiProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void retriesMalformedStructuredOutputThenReturnsValidatedResult() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> lastRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int requestNumber = requests.incrementAndGet();
            String content = requestNumber == 1 ? "{}" : mapper.writeValueAsString(validOutput());
            respond(exchange, 200, mapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", content))),
                    "usage", Map.of("prompt_tokens", 80, "completion_tokens", 30))));
        });
        server.start();
        GroqRadarAiProvider provider = provider(1);

        RadarAiResponse response = provider.generateDeepDive(input());

        assertThat(requests).hasValue(2);
        assertThat(response.model()).isEqualTo("openai/gpt-oss-120b");
        assertThat(response.retryCount()).isEqualTo(1);
        assertThat(response.output().summary()).isEqualTo("Public summary");
        JsonNode request = mapper.readTree(lastRequest.get());
        assertThat(request.path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        assertThat(request.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
    }

    @Test
    void rateLimitRetriesAreBounded() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 429, "{\"error\":{\"message\":\"rate limited\"}}");
        });
        server.start();

        assertThatThrownBy(() -> provider(1).analyzeCompany(input()))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> {
                    RadarAiException aiError = (RadarAiException) error;
                    assertThat(aiError.errorType()).isEqualTo("RATE_LIMITED");
                    assertThat(aiError.attempts()).isEqualTo(2);
                });
        assertThat(requests).hasValue(2);
    }

    @Test
    void rejectsUnsupportedFactsWhenNoPublicSourceEvidenceExists() throws Exception {
        Map<String, Object> output = validOutput();
        output.put("sourceUrls", List.of());
        output.put("facts", List.of());
        output.put("founders", List.of("Invented Founder"));
        PublicCompanyAnalysisInput noSources = new PublicCompanyAnalysisInput(4L, "Public Co", "public.example",
                "https://public.example", "", "Software", List.of("Automation"), "New York", 2025,
                "Unknown", List.of(), List.of(), List.of(), List.of(), List.of(), 0);

        assertThatThrownBy(() -> RadarAiSchema.parseAndValidate(mapper, mapper.writeValueAsString(output), noSources))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> assertThat(((RadarAiException) error).errorType())
                        .isEqualTo("UNSUPPORTED_FACT_VIOLATION"));
    }

    private GroqRadarAiProvider provider(int retries) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions");
        return new GroqRadarAiProvider(mapper, HttpClient.newHttpClient(), endpoint, "test-server-key",
                "openai/gpt-oss-20b", "openai/gpt-oss-120b", retries, Duration.ofSeconds(5));
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
        output.put("whyIShouldCare", "Matches automation interests");
        output.put("watchTriggers", List.of("New public source"));
        output.put("radarScoreInputs", List.of("Public launch"));
        output.put("personalScoreInputs", List.of("Automation"));
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
