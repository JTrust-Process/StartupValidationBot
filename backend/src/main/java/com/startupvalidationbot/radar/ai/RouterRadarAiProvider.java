package com.startupvalidationbot.radar.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RouterRadarAiProvider implements RadarAiProvider {
    private static final Logger log = LoggerFactory.getLogger(RouterRadarAiProvider.class);
    private static final String PROVIDER_ID = "router";

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String routineModel;
    private final String deepDiveModel;
    private final int maxRetries;
    private final Duration requestTimeout;

    @Autowired
    public RouterRadarAiProvider(ObjectMapper mapper,
            @Value("${radar.ai.router-base-url:https://api.router.com/v1}") String baseUrl,
            @Value("${radar.ai.router-api-key:}") String apiKey,
            @Value("${radar.ai.router-routine-model:}") String routineModel,
            @Value("${radar.ai.router-deep-dive-model:}") String deepDiveModel,
            @Value("${radar.ai.max-retries:2}") int maxRetries,
            @Value("${radar.ai.timeout-seconds:60}") int timeoutSeconds) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                responsesEndpoint(baseUrl), apiKey, routineModel, deepDiveModel, maxRetries,
                Duration.ofSeconds(Math.max(5, timeoutSeconds)));
    }

    RouterRadarAiProvider(ObjectMapper mapper, HttpClient httpClient, URI endpoint, String apiKey,
            String routineModel, String deepDiveModel, int maxRetries, Duration requestTimeout) {
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = clean(apiKey);
        this.routineModel = clean(routineModel);
        this.deepDiveModel = clean(deepDiveModel);
        this.maxRetries = Math.max(0, Math.min(maxRetries, 5));
        this.requestTimeout = requestTimeout;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String routineModel() {
        return routineModel;
    }

    @Override
    public String deepDiveModel() {
        return deepDiveModel;
    }

    @Override
    public RadarAiResponse analyzeCompany(PublicCompanyAnalysisInput input) {
        return request(input, routineModel, false);
    }

    @Override
    public RadarAiResponse generateDeepDive(PublicCompanyAnalysisInput input) {
        return request(input, deepDiveModel, true);
    }

    private RadarAiResponse request(PublicCompanyAnalysisInput input, String requestedModel, boolean deepDive) {
        validateConfiguration(requestedModel);
        RadarAiException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long started = System.nanoTime();
            try {
                HttpResponse<String> response = httpClient.send(buildRequest(input, requestedModel, deepDive),
                        HttpResponse.BodyHandlers.ofString());
                long latencyMs = elapsedMs(started);
                String requestId = safeToken(response.headers().firstValue("x-request-id").orElse(""));
                String traceId = safeToken(response.headers().firstValue("x-trace-id").orElse(""));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    RadarAiException error = httpError(response.statusCode(), response.body(), attempt + 1,
                            latencyMs, requestId, traceId);
                    logFailure(input, requestedModel, deepDive, latencyMs, attempt, error);
                    if (!error.retryable() || attempt == maxRetries) throw error;
                    sleepBeforeRetry(response, attempt);
                    lastError = error;
                    continue;
                }
                JsonNode body = mapper.readTree(response.body());
                String content = outputText(body);
                RadarAiOutput output;
                try {
                    output = RadarAiSchema.parseAndValidate(mapper, content, input);
                } catch (RadarAiException error) {
                    throw withDiagnostics(error, attempt + 1, latencyMs, requestId, traceId);
                }
                Long inputTokens = nullableLong(body.path("usage").path("input_tokens"));
                Long outputTokens = nullableLong(body.path("usage").path("output_tokens"));
                String actualModel = clean(body.path("model").asText(requestedModel));
                log.info("radar_ai_call companyId={} provider={} kind={} requestedModel={} actualModel={} cache=miss latencyMs={} success=true retry={} inputTokens={} outputTokens={} requestId={} traceId={}",
                        input.companyId(), providerId(), kind(deepDive), requestedModel, actualModel, latencyMs,
                        attempt, inputTokens, outputTokens, requestId, traceId);
                return new RadarAiResponse(output, requestedModel, actualModel, attempt, latencyMs,
                        inputTokens, outputTokens, null, blankToNull(requestId), blankToNull(traceId));
            } catch (RadarAiException error) {
                lastError = withAttempts(error, attempt + 1);
                if (!error.retryable() || attempt == maxRetries) throw lastError;
                logFailure(input, requestedModel, deepDive, elapsedMs(started), attempt, error);
                sleepBeforeRetry(null, attempt);
            } catch (JsonProcessingException error) {
                lastError = diagnosticError("MALFORMED_RESPONSE", "Router returned malformed response JSON",
                        attempt + 1, elapsedMs(started), error);
                if (attempt == maxRetries) throw lastError;
                sleepBeforeRetry(null, attempt);
            } catch (HttpTimeoutException error) {
                lastError = diagnosticError("TIMEOUT", "Router request timed out", attempt + 1,
                        elapsedMs(started), error);
                if (attempt == maxRetries) throw lastError;
                sleepBeforeRetry(null, attempt);
            } catch (IOException error) {
                lastError = diagnosticError("PROVIDER_UNAVAILABLE", "Router is unavailable", attempt + 1,
                        elapsedMs(started), error);
                if (attempt == maxRetries) throw lastError;
                sleepBeforeRetry(null, attempt);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RadarAiException("INTERRUPTED", "Router request was interrupted", false, attempt + 1,
                        error);
            }
        }
        throw lastError == null
                ? new RadarAiException("PROVIDER_UNAVAILABLE", "Router request failed", false, 0)
                : lastError;
    }

    private HttpRequest buildRequest(PublicCompanyAnalysisInput input, String model, boolean deepDive) {
        try {
            Map<String, Object> format = Map.of(
                    "type", "json_schema",
                    "name", "startup_radar_analysis",
                    "strict", true,
                    "schema", RadarAiSchema.jsonSchema(mapper));
            Map<String, Object> body = Map.of(
                    "model", model,
                    "instructions", RadarAiPrompt.instructions(deepDive),
                    "input", "Public Radar data:\n" + mapper.writeValueAsString(PublicRadarPayload.from(input)),
                    "max_output_tokens", deepDive ? 6_000 : 3_500,
                    "reasoning", Map.of("effort", deepDive ? "medium" : "low"),
                    "text", Map.of("format", format),
                    "store", false);
            return HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        } catch (JsonProcessingException error) {
            throw new RadarAiException("REQUEST_SERIALIZATION", "Unable to serialize public Radar input", false,
                    0, error);
        }
    }

    private void validateConfiguration(String model) {
        if (!isConfigured()) {
            throw new RadarAiException("MISSING_CREDENTIALS", "ROUTER_API_KEY is not configured", false, 0);
        }
        if (model == null || model.isBlank()) {
            throw new RadarAiException("MODEL_UNAVAILABLE",
                    "Router model or benchmark alias is not configured", false, 0);
        }
    }

    private RadarAiException httpError(int status, String responseBody, int attempts, long latencyMs,
            String requestId, String traceId) {
        ProviderError providerError = parseProviderError(status, responseBody);
        String fingerprint = String.join(" ", providerError.type(), providerError.code(), providerError.message())
                .toLowerCase(Locale.ROOT);
        boolean schemaRejection = fingerprint.contains("json schema") || fingerprint.contains("structured output")
                || fingerprint.contains("schema validation") || fingerprint.contains("invalid schema");
        String errorType;
        boolean retryable;
        if (status == 400 && schemaRejection) {
            errorType = "STRUCTURED_OUTPUT_REJECTED";
            retryable = true;
        } else {
            errorType = switch (status) {
                case 401 -> "INVALID_CREDENTIALS";
                case 402 -> "INSUFFICIENT_CREDITS";
                case 404 -> "MODEL_UNAVAILABLE";
                case 429 -> "RATE_LIMITED";
                case 408, 409 -> "RETRYABLE_HTTP";
                default -> status >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_REQUEST_REJECTED";
            };
            retryable = status == 408 || status == 409 || status == 429 || status >= 500;
        }
        return new RadarAiException(errorType, providerError.message(), retryable, attempts, status,
                providerError.type(), providerError.code(), latencyMs, blankToNull(requestId), blankToNull(traceId));
    }

    private ProviderError parseProviderError(int status, String responseBody) {
        try {
            JsonNode error = mapper.readTree(responseBody).path("error");
            String type = safeToken(error.path("type").asText("unknown"));
            String code = safeToken(error.path("code").asText("unknown"));
            String message = sanitizeProviderMessage(error.path("message").asText(""));
            if (message.isBlank()) message = "Router returned HTTP " + status + ".";
            return new ProviderError(type, code, message);
        } catch (JsonProcessingException | RuntimeException error) {
            return new ProviderError("unknown", "unknown",
                    "Router returned HTTP " + status + " with an unreadable error body.");
        }
    }

    static URI responsesEndpoint(String baseUrl) {
        String value = clean(baseUrl);
        if (value.isBlank()) value = "https://api.router.com/v1";
        URI base = URI.create(value.endsWith("/") ? value : value + "/");
        return base.resolve("responses");
    }

    static Map<String, Object> publicPayload(PublicCompanyAnalysisInput input) {
        return PublicRadarPayload.from(input);
    }

    private static String outputText(JsonNode body) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : body.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    String text = content.path("text").asText("");
                    if (!text.isBlank()) values.add(text);
                }
            }
        }
        if (values.isEmpty()) {
            throw new RadarAiException("MALFORMED_RESPONSE", "Router response contained no output_text", true, 1);
        }
        return String.join("\n", values);
    }

    private void logFailure(PublicCompanyAnalysisInput input, String model, boolean deepDive, long latencyMs,
            int retry, RadarAiException error) {
        log.warn("radar_ai_call companyId={} provider={} kind={} requestedModel={} cache=miss latencyMs={} success=false retry={} errorType={} httpStatus={} providerErrorType={} providerErrorCode={} requestId={} traceId={} providerMessage={}",
                input.companyId(), providerId(), kind(deepDive), model, latencyMs, retry, error.errorType(),
                error.httpStatus(), error.providerErrorType(), error.providerErrorCode(), error.requestId(),
                error.traceId(), error.getMessage());
    }

    private static RadarAiException diagnosticError(String type, String message, int attempts, long latencyMs,
            Exception cause) {
        return new RadarAiException(type, message, true, attempts, null, null, null, latencyMs, null, null);
    }

    private static RadarAiException withAttempts(RadarAiException error, int attempts) {
        return new RadarAiException(error.errorType(), error.getMessage(), error.retryable(), attempts,
                error.httpStatus(), error.providerErrorType(), error.providerErrorCode(), error.latencyMs(),
                error.requestId(), error.traceId());
    }

    private static RadarAiException withDiagnostics(RadarAiException error, int attempts, long latencyMs,
            String requestId, String traceId) {
        return new RadarAiException(error.errorType(), error.getMessage(), error.retryable(), attempts,
                error.httpStatus(), error.providerErrorType(), error.providerErrorCode(), latencyMs,
                blankToNull(requestId), blankToNull(traceId));
    }

    private static void sleepBeforeRetry(HttpResponse<?> response, int attempt) {
        long delayMs = Math.min(5_000, 250L * (1L << Math.min(attempt, 4)));
        if (response != null) {
            String retryAfterMs = response.headers().firstValue("retry-after-ms").orElse("");
            String retryAfter = response.headers().firstValue("retry-after").orElse("");
            try {
                if (!retryAfterMs.isBlank()) delayMs = Math.min(5_000, Math.max(delayMs, Long.parseLong(retryAfterMs)));
                else if (!retryAfter.isBlank()) delayMs = Math.min(5_000,
                        Math.max(delayMs, Long.parseLong(retryAfter) * 1_000));
            } catch (NumberFormatException ignored) {
                // Exponential delay remains in effect for absent or date-formatted headers.
            }
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RadarAiException("INTERRUPTED", "Router retry wait was interrupted", false, attempt + 1,
                    error);
        }
    }

    private static String sanitizeProviderMessage(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("(?i)bearer\\s+\\S+", "Bearer <redacted>")
                .replaceAll("(?i)(router|ramp)_[a-z0-9_-]+", "<redacted-key>")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) return "";
        String sanitized = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return sanitized.length() > 160 ? sanitized.substring(0, 160) : sanitized;
    }

    private static Long nullableLong(JsonNode node) {
        return node.isNumber() ? node.longValue() : null;
    }

    private static long elapsedMs(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String kind(boolean deepDive) {
        return deepDive ? "deep-dive" : "routine";
    }

    private record ProviderError(String type, String code, String message) {
    }
}
