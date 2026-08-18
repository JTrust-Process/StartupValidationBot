package com.startupvalidationbot.radar.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class RadarAiSchema {
    public static final String VERSION = "radar-analysis-v1";
    private static final List<String> STRING_FIELDS = List.of(
            "summary", "problem", "solution", "businessModel", "stage", "fundingSummary",
            "whyItMatters", "whyIShouldCare", "investmentAccessibility", "careerAngle", "confidence");
    private static final List<String> LIST_FIELDS = List.of(
            "categories", "founders", "investors", "tractionSignals", "technicalDifferentiation",
            "marketSignals", "interestingSignals", "risks", "bullCase", "bearCase", "watchTriggers",
            "radarScoreInputs", "personalScoreInputs", "unansweredQuestions", "facts", "inferences",
            "sourceUrls");
    private static final Set<String> ALL_FIELDS;

    static {
        Set<String> fields = new LinkedHashSet<>(STRING_FIELDS);
        fields.addAll(LIST_FIELDS);
        ALL_FIELDS = Set.copyOf(fields);
    }

    private RadarAiSchema() {
    }

    public static ObjectNode jsonSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        for (String field : STRING_FIELDS) {
            ObjectNode definition = properties.putObject(field);
            definition.put("type", "string");
            if ("confidence".equals(field)) {
                definition.putArray("enum").add("HIGH").add("MEDIUM").add("LOW");
            }
        }
        for (String field : LIST_FIELDS) {
            ObjectNode definition = properties.putObject(field);
            definition.put("type", "array");
            definition.putObject("items").put("type", "string");
        }
        ArrayNode required = schema.putArray("required");
        ALL_FIELDS.forEach(required::add);
        schema.put("additionalProperties", false);
        return schema;
    }

    public static RadarAiOutput parseAndValidate(ObjectMapper mapper, String content,
            PublicCompanyAnalysisInput input) {
        if (content == null || content.isBlank()) {
            throw new RadarAiException("EMPTY_RESPONSE", "AI provider returned an empty response", true, 1);
        }
        try {
            JsonNode node = mapper.readTree(content);
            if (!node.isObject()) {
                throw new RadarAiException("SCHEMA_VIOLATION", "AI response must be a JSON object", true, 1);
            }
            Set<String> actualFields = new LinkedHashSet<>();
            node.fieldNames().forEachRemaining(actualFields::add);
            if (!actualFields.equals(ALL_FIELDS)) {
                throw new RadarAiException("SCHEMA_VIOLATION",
                        "AI response fields did not match the Radar analysis schema", true, 1);
            }
            for (String field : STRING_FIELDS) {
                if (!node.path(field).isTextual() || node.path(field).asText().length() > 4_000) {
                    throw schemaViolation(field);
                }
            }
            for (String field : LIST_FIELDS) {
                JsonNode list = node.path(field);
                if (!list.isArray() || list.size() > 50) {
                    throw schemaViolation(field);
                }
                for (JsonNode item : list) {
                    if (!item.isTextual() || item.asText().length() > 2_000) {
                        throw schemaViolation(field);
                    }
                }
            }
            RadarAiOutput output = mapper.treeToValue(node, RadarAiOutput.class);
            if (!Set.of("HIGH", "MEDIUM", "LOW").contains(output.confidence())) {
                throw schemaViolation("confidence");
            }
            if (!input.suppliedSourceUrls().containsAll(output.sourceUrls())) {
                throw new RadarAiException("SOURCE_PROVENANCE_VIOLATION",
                        "AI response cited a URL that was not supplied to the provider", true, 1);
            }
            if (!output.facts().isEmpty() && output.sourceUrls().isEmpty()) {
                throw new RadarAiException("SOURCE_PROVENANCE_VIOLATION",
                        "AI facts require at least one supplied public source URL", true, 1);
            }
            if (input.sources().isEmpty() && (!output.founders().isEmpty() || !output.investors().isEmpty()
                    || !output.tractionSignals().isEmpty() || !output.facts().isEmpty()
                    || !"Unknown".equalsIgnoreCase(output.fundingSummary()))) {
                throw new RadarAiException("UNSUPPORTED_FACT_VIOLATION",
                        "AI claimed unsupported facts for a company without public source evidence", true, 1);
            }
            return output;
        } catch (RadarAiException error) {
            throw error;
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw new RadarAiException("MALFORMED_RESPONSE", "AI provider returned malformed JSON", true, 1,
                    error);
        }
    }

    private static RadarAiException schemaViolation(String field) {
        return new RadarAiException("SCHEMA_VIOLATION",
                "AI response field failed validation: " + field, true, 1);
    }
}
