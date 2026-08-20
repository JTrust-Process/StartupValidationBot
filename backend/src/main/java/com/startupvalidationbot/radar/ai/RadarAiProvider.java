package com.startupvalidationbot.radar.ai;

public interface RadarAiProvider {
    String providerId();

    boolean isConfigured();

    String routineModel();

    String deepDiveModel();

    RadarAiResponse analyzeCompany(PublicCompanyAnalysisInput input);

    RadarAiResponse generateDeepDive(PublicCompanyAnalysisInput input);
}
