package com.startupvalidationbot.radar.ai;

final class RadarAiPrompt {
    private RadarAiPrompt() {
    }

    static String instructions(boolean deepDive) {
        String task = deepDive
                ? "Generate a detailed startup research memo from only the supplied public Radar evidence."
                : "Enrich this startup record from only the supplied public Radar evidence.";
        return "You are a startup research analyst. " + task
                + " Separate facts from inferences. Use 'Unknown' or an empty array whenever evidence is absent."
                + " Do not invent founders, funding, investors, revenue, customers, users, traction, or access terms."
                + " Do not make investment recommendations or use external knowledge. Cite only supplied source URLs."
                + " Personal preferences are not supplied: use 'Unknown' for whyIShouldCare and careerAngle,"
                + " and return an empty personalScoreInputs array.";
    }
}
