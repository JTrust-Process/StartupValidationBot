package com.startupvalidationbot.radar.ai;

public record RadarAiResponse(RadarAiOutput output, String model, int retryCount, long latencyMs,
        Long inputTokens, Long outputTokens) {
}
