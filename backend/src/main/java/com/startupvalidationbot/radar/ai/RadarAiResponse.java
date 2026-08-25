package com.startupvalidationbot.radar.ai;

import java.math.BigDecimal;

public record RadarAiResponse(RadarAiOutput output, String model, String actualModel, int retryCount,
        long latencyMs, Long inputTokens, Long outputTokens, BigDecimal providerCostUsd,
        String requestId, String traceId) {
    public RadarAiResponse(RadarAiOutput output, String model, int retryCount, long latencyMs,
            Long inputTokens, Long outputTokens) {
        this(output, model, model, retryCount, latencyMs, inputTokens, outputTokens, null, null, null);
    }
}
