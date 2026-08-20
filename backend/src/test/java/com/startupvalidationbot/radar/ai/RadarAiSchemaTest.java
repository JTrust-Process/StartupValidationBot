package com.startupvalidationbot.radar.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RadarAiSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void strictSchemaRequiresEveryPropertyAndClosesEveryObject() {
        JsonNode schema = RadarAiSchema.jsonSchema(mapper);

        assertStrictNode(schema);
        assertThat(schema.path("properties").path("sector").path("type").asText()).isEqualTo("string");
    }

    @Test
    void canonicalEquivalentPublicCitationIsAccepted() throws Exception {
        PublicCompanyAnalysisInput input = input("https://EXAMPLE.com:443/launch/");
        var output = validOutput(List.of("https://example.com/launch#details"), List.of("Public launch supplied."));

        assertThat(RadarAiSchema.parseAndValidate(mapper, mapper.writeValueAsString(output), input).sourceUrls())
                .containsExactly("https://example.com/launch#details");
    }

    @Test
    void inventedCitationIsRejectedWithSanitizedDiagnostic() throws Exception {
        var output = validOutput(List.of("https://invented.example/path?token=gsk_secret"), List.of());

        assertThatThrownBy(() -> RadarAiSchema.parseAndValidate(mapper, mapper.writeValueAsString(output),
                input("https://example.com/launch")))
                .isInstanceOf(RadarAiException.class)
                .satisfies(error -> {
                    RadarAiException aiError = (RadarAiException) error;
                    assertThat(aiError.errorType()).isEqualTo("SOURCE_PROVENANCE_VIOLATION");
                    assertThat(aiError.getMessage()).contains("invented.example", "<redacted-key>")
                            .doesNotContain("gsk_secret");
                });
    }

    private static void assertStrictNode(JsonNode schema) {
        String type = schema.path("type").asText();
        if ("object".equals(type)) {
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            Set<String> properties = new HashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(properties::add);
            Set<String> required = new HashSet<>();
            schema.path("required").forEach(node -> required.add(node.asText()));
            assertThat(required).isEqualTo(properties);
            schema.path("properties").forEach(RadarAiSchemaTest::assertStrictNode);
        } else if ("array".equals(type)) {
            assertStrictNode(schema.path("items"));
        } else {
            assertThat(type).isEqualTo("string");
        }
    }

    private static PublicCompanyAnalysisInput input(String sourceUrl) {
        return new PublicCompanyAnalysisInput(4L, "Public Co", "public.example", "https://public.example",
                "Public launch description", "Software", List.of("Automation"), null, 2025, "Unknown",
                List.of(), List.of(), List.of(), List.of(),
                List.of(new PublicCompanyAnalysisInput.PublicSourceEvidence("RSS", "Launch", sourceUrl,
                        "Public launch description")), 1);
    }

    private static java.util.Map<String, Object> validOutput(List<String> sourceUrls, List<String> facts) {
        java.util.Map<String, Object> output = new java.util.LinkedHashMap<>();
        for (String field : List.of("summary", "sector", "problem", "solution", "businessModel", "stage",
                "fundingSummary", "whyItMatters", "whyIShouldCare", "investmentAccessibility", "careerAngle")) {
            output.put(field, "Unknown");
        }
        output.put("confidence", "LOW");
        for (String field : List.of("categories", "founders", "investors", "tractionSignals",
                "technicalDifferentiation", "marketSignals", "interestingSignals", "risks", "bullCase",
                "bearCase", "watchTriggers", "radarScoreInputs", "personalScoreInputs", "unansweredQuestions",
                "inferences")) {
            output.put(field, List.of());
        }
        output.put("facts", facts);
        output.put("sourceUrls", sourceUrls);
        return output;
    }
}
