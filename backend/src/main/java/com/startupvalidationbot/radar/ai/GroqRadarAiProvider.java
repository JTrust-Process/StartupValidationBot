package com.startupvalidationbot.radar.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
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
public class GroqRadarAiProvider implements RadarAiProvider {
    private static final Logger log = LoggerFactory.getLogger(GroqRadarAiProvider.class);
    private static final String PROVIDER_ID = "groq";

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String routineModel;
    private final String deepDiveModel;
    private final int maxRetries;
    private final Duration requestTimeout;

    @Autowired
    public GroqRadarAiProvider(ObjectMapper mapper,
            @Value("${radar.ai.groq-base-url:https://api.groq.com/openai/v1/chat/completions}") String endpoint,
            @Value("${radar.ai.groq-api-key:}") String apiKey,
            @Value("${radar.ai.model:openai/gpt-oss-20b}") String routineModel,
            @Value("${radar.ai.deep-dive-model:openai/gpt-oss-120b}") String deepDiveModel,
            @Value("${radar.ai.max-retries:2}") int maxRetries,
            @Value("${radar.ai.timeout-seconds:60}") int timeoutSeconds) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), URI.create(endpoint),
                apiKey, routineModel, deepDiveModel, maxRetries, Duration.ofSeconds(Math.max(5, timeoutSeconds)));
    }

    GroqRadarAiProvider(ObjectMapper mapper, HttpClient httpClient, URI endpoint, String apiKey,
            String routineModel, String deepDiveModel, int maxRetries, Duration requestTimeout) {
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.routineModel = routineModel;
        this.deepDiveModel = deepDiveModel;
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

    private RadarAiResponse request(PublicCompanyAnalysisInput input, String model, boolean deepDive) {
        if (!isConfigured()) {
            throw new RadarAiException("MISSING_CREDENTIALS", "GROQ_API_KEY is not configured", false, 0);
        }
        RadarAiException lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long started = System.nanoTime();
            try {
                HttpResponse<String> response = httpClient.send(buildRequest(input, model, deepDive),
                        HttpResponse.BodyHandlers.ofString());
                long latencyMs = elapsedMs(started);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    RadarAiException error = httpError(response.statusCode(), response.body(), attempt + 1);
                    log.warn("radar_ai_call companyId={} provider={} model={} cache=miss latencyMs={} success=false retry={} errorType={} httpStatus={} providerErrorType={} providerErrorCode={} providerMessage={}",
                            input.companyId(), providerId(), model, latencyMs, attempt, error.errorType(),
                            error.httpStatus(), error.providerErrorType(), error.providerErrorCode(),
                            error.getMessage());
                    if (!error.retryable() || attempt == maxRetries) {
                        throw error;
                    }
                    sleepBeforeRetry(response, attempt);
                    lastError = error;
                    continue;
                }
                JsonNode body = mapper.readTree(response.body());
                String content = body.path("choices").path(0).path("message").path("content").asText("");
                RadarAiOutput output = RadarAiSchema.parseAndValidate(mapper, content, input);
                Long inputTokens = nullableLong(body.path("usage").path("prompt_tokens"));
                Long outputTokens = nullableLong(body.path("usage").path("completion_tokens"));
                log.info("radar_ai_call companyId={} provider={} model={} cache=miss latencyMs={} success=true retry={} inputTokens={} outputTokens={}",
                        input.companyId(), providerId(), model, latencyMs, attempt, inputTokens, outputTokens);
                return new RadarAiResponse(output, model, attempt, latencyMs, inputTokens, outputTokens);
            } catch (RadarAiException error) {
                lastError = withAttempts(error, attempt + 1);
                if (!error.retryable() || attempt == maxRetries) {
                    throw lastError;
                }
                log.warn("radar_ai_call companyId={} provider={} model={} cache=miss latencyMs={} success=false retry={} errorType={} httpStatus={} providerErrorType={} providerErrorCode={}",
                        input.companyId(), providerId(), model, elapsedMs(started), attempt, error.errorType(),
                        error.httpStatus(), error.providerErrorType(), error.providerErrorCode());
                sleepBeforeRetry(null, attempt);
            } catch (JsonProcessingException error) {
                lastError = new RadarAiException("MALFORMED_RESPONSE", "Groq returned malformed response JSON",
                        true, attempt + 1, error);
                if (attempt == maxRetries) throw lastError;
                sleepBeforeRetry(null, attempt);
            } catch (HttpTimeoutException error) {
                lastError = new RadarAiException("TIMEOUT", "Groq request timed out", true, attempt + 1, error);
                if (attempt == maxRetries) throw lastError;
                sleepBeforeRetry(null, attempt);
            } catch (IOException error) {
                lastError = new RadarAiException("PROVIDER_UNAVAILABLE", "Groq is unavailable", true, attempt + 1,
                        error);
                if (attempt == maxRetries) throw lastError;
                sleepBeforeRetry(null, attempt);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RadarAiException("INTERRUPTED", "Groq request was interrupted", false, attempt + 1,
                        error);
            }
        }
        throw lastError == null
                ? new RadarAiException("PROVIDER_UNAVAILABLE", "Groq request failed", false, 0)
                : lastError;
    }

    private HttpRequest buildRequest(PublicCompanyAnalysisInput input, String model, boolean deepDive) {
        try {
            Map<String, Object> publicPayload = publicPayload(input);
            String task = deepDive
                    ? "Generate a detailed startup research memo from only the supplied public Radar evidence."
                    : "Enrich this startup record from only the supplied public Radar evidence.";
            String system = "You are a startup research analyst. " + task
                    + " Separate facts from inferences. Use 'Unknown' or an empty array whenever evidence is absent."
                    + " Do not invent founders, funding, investors, revenue, customers, users, traction, or access terms."
                    + " Do not make investment recommendations or use external knowledge. Cite only supplied source URLs."
                    + " Personal preferences are not supplied: use 'Unknown' for whyIShouldCare and careerAngle,"
                    + " and return an empty personalScoreInputs array.";
            Map<String, Object> body = Map.of(
                    "model", model,
                    "reasoning_effort", deepDive ? "medium" : "low",
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content",
                                    "Public Radar data:\n" + mapper.writeValueAsString(publicPayload))),
                    "response_format", Map.of(
                            "type", "json_schema",
                            "json_schema", Map.of("name", "startup_radar_analysis", "strict", true,
                                    "schema", RadarAiSchema.jsonSchema(mapper))));
            return HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        } catch (JsonProcessingException error) {
            throw new RadarAiException("REQUEST_SERIALIZATION", "Unable to serialize public Radar input", false, 0,
                    error);
        }
    }

    static Map<String, Object> publicPayload(PublicCompanyAnalysisInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("companyName", value(input.companyName()));
        payload.put("domain", value(input.domain()));
        payload.put("websiteUrl", value(input.websiteUrl()));
        payload.put("publicDescription", value(input.publicDescription()));
        payload.put("sector", value(input.sector()));
        payload.put("categories", input.categories());
        payload.put("headquarters", value(input.headquarters()));
        payload.put("foundedYear", input.foundedYear() == null ? "Unknown" : input.foundedYear());
        payload.put("acceleratorBatch", value(input.acceleratorBatch()));
        payload.put("publicLaunchInformation", input.publicLaunchInformation());
        payload.put("publicFundingInformation", input.publicFundingInformation());
        payload.put("publicInvestorInformation", input.publicInvestorInformation());
        payload.put("publicTractionInformation", input.publicTractionInformation());
        payload.put("sources", input.sources());
        payload.put("sourceCount", input.sourceCount());
        return payload;
    }

    private RadarAiException httpError(int status, String responseBody, int attempts) {
        ProviderError providerError = parseProviderError(status, responseBody);
        String fingerprint = String.join(" ", providerError.type(), providerError.code(), providerError.message())
                .toLowerCase(Locale.ROOT);
        boolean structuredOutputRejection = fingerprint.contains("json_validate")
                || fingerprint.contains("json schema")
                || fingerprint.contains("structured output")
                || fingerprint.contains("failed_generation")
                || fingerprint.contains("expected schema");
        String errorType;
        boolean retryable;
        if (status == 400 && structuredOutputRejection) {
            errorType = "STRUCTURED_OUTPUT_REJECTED";
            retryable = true;
        } else {
            errorType = switch (status) {
                case 401, 403 -> "INVALID_CREDENTIALS";
                case 404 -> "MODEL_UNAVAILABLE";
                case 429 -> "RATE_LIMITED";
                case 408, 409, 422, 498 -> "RETRYABLE_HTTP";
                default -> status >= 500 ? "PROVIDER_UNAVAILABLE" : "PROVIDER_REQUEST_REJECTED";
            };
            retryable = switch (status) {
                case 408, 409, 422, 429, 498 -> true;
                default -> status >= 500;
            };
        }
        return new RadarAiException(errorType, providerError.message(), retryable, attempts, status,
                providerError.type(), providerError.code());
    }

    private ProviderError parseProviderError(int status, String responseBody) {
        try {
            JsonNode error = mapper.readTree(responseBody).path("error");
            String type = safeToken(error.path("type").asText("unknown"));
            String code = safeToken(error.path("code").asText("unknown"));
            String message = sanitizeProviderMessage(error.path("message").asText(""));
            if (message.isBlank()) message = "Groq returned HTTP " + status + ".";
            return new ProviderError(type, code, message);
        } catch (JsonProcessingException | RuntimeException error) {
            return new ProviderError("unknown", "unknown",
                    "Groq returned HTTP " + status + " with an unreadable error body.");
        }
    }

    private static String sanitizeProviderMessage(String value) {
        if (value == null) return "";
        String sanitized = value.replaceAll("(?i)bearer\\s+\\S+", "Bearer <redacted>")
                .replaceAll("(?i)gsk_[a-z0-9_-]+", "<redacted-key>")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.length() > 160 ? sanitized.substring(0, 160) : sanitized;
    }

    /*
     * The provider's status/type/code/message are safe diagnostics. The request body,
     * Authorization header, and failed generation are deliberately not retained.
     */
    private record ProviderError(String type, String code, String message) {
    }

    private static RadarAiException withAttempts(RadarAiException error, int attempts) {
        return new RadarAiException(error.errorType(), error.getMessage(), error.retryable(), attempts,
                error.httpStatus(), error.providerErrorType(), error.providerErrorCode());
    }

    private static void sleepBeforeRetry(HttpResponse<?> response, int attempt) {
        long delayMs = Math.min(5_000, 250L * (1L << Math.min(attempt, 4)));
        if (response != null) {
            String retryAfter = response.headers().firstValue("retry-after").orElse("");
            try {
                delayMs = Math.min(5_000, Math.max(delayMs, Long.parseLong(retryAfter) * 1_000));
            } catch (NumberFormatException ignored) {
                // Exponential delay remains in effect when Retry-After is absent or date-formatted.
            }
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RadarAiException("INTERRUPTED", "Groq retry wait was interrupted", false, attempt + 1,
                    error);
        }
    }

    private static long elapsedMs(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static Long nullableLong(JsonNode node) {
        return node.isNumber() ? node.longValue() : null;
    }

    private static String value(Object value) {
        return value == null || value.toString().isBlank() ? "Unknown" : value.toString();
    }
}
