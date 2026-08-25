package com.startupvalidationbot.radar.ai;

import java.util.List;

public record RadarAiOutput(
        String summary,
        String sector,
        String problem,
        String solution,
        String businessModel,
        List<String> categories,
        String stage,
        List<String> founders,
        String fundingSummary,
        List<String> investors,
        List<String> tractionSignals,
        List<String> technicalDifferentiation,
        List<String> marketSignals,
        List<String> interestingSignals,
        List<String> risks,
        List<String> bullCase,
        List<String> bearCase,
        String whyItMatters,
        String whyIShouldCare,
        List<String> watchTriggers,
        List<String> radarScoreInputs,
        List<String> personalScoreInputs,
        String investmentAccessibility,
        String careerAngle,
        List<String> unansweredQuestions,
        String confidence,
        List<String> facts,
        List<String> inferences,
        List<String> sourceUrls) {
}
