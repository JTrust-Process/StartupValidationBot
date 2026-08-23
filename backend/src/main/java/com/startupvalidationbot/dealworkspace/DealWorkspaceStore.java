package com.startupvalidationbot.dealworkspace;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Repository
public class DealWorkspaceStore {
    static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private static final String SELECT = """
            SELECT id, payload, created_at, updated_at
              FROM deal_workspaces
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final boolean postgres;

    public DealWorkspaceStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.postgres = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())));
    }

    public List<JsonNode> list() {
        return jdbc.query(SELECT + " ORDER BY updated_at DESC", (result, row) -> response(result.getLong("id"),
                result.getString("payload"), result.getTimestamp("created_at"), result.getTimestamp("updated_at")));
    }

    public Optional<JsonNode> find(long id) {
        return jdbc.query(SELECT + " WHERE id = ?", (result, row) -> response(result.getLong("id"),
                result.getString("payload"), result.getTimestamp("created_at"), result.getTimestamp("updated_at")), id)
                .stream().findFirst();
    }

    @Transactional
    public JsonNode create(JsonNode request) {
        ObjectNode payload = validate(request);
        Instant now = Instant.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(("""
                    INSERT INTO deal_workspaces (
                        payload, company_name, platform, offering_url, radar_company_id, created_at, updated_at
                    ) VALUES (%s, ?, ?, ?, ?, ?, ?)
                    """).formatted(jsonParameter()), new String[] { "id" });
            statement.setString(1, "{}");
            statement.setString(2, requiredText(payload, "companyName"));
            statement.setString(3, requiredText(payload, "platform"));
            statement.setString(4, optionalText(payload, "offeringUrl"));
            setNullableLong(statement, 5, optionalLong(payload, "radarCompanyId"));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a deal workspace id");
        long id = key.longValue();
        applyServerFields(payload, id, now, now);
        updatePayload(id, payload);
        return find(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public JsonNode update(long id, JsonNode request) {
        ObjectNode payload = validate(request);
        Timestamp created = jdbc.query("SELECT created_at FROM deal_workspaces WHERE id = ?",
                (result, row) -> result.getTimestamp(1), id).stream().findFirst()
                .orElseThrow(() -> notFound(id));
        Instant updatedAt = Instant.now();
        applyServerFields(payload, id, created.toInstant(), updatedAt);
        int changed = jdbc.update(("""
                UPDATE deal_workspaces
                   SET payload = %s, company_name = ?, platform = ?, offering_url = ?,
                       radar_company_id = ?, updated_at = ?
                 WHERE id = ?
                """).formatted(jsonParameter()), serialize(payload), requiredText(payload, "companyName"), requiredText(payload, "platform"),
                optionalText(payload, "offeringUrl"), optionalLong(payload, "radarCompanyId"),
                Timestamp.from(updatedAt), id);
        if (changed == 0) throw notFound(id);
        return find(id).orElseThrow(() -> notFound(id));
    }

    public void delete(long id) {
        if (jdbc.update("DELETE FROM deal_workspaces WHERE id = ?", id) == 0) throw notFound(id);
    }

    private void updatePayload(long id, ObjectNode payload) {
        jdbc.update("UPDATE deal_workspaces SET payload = " + jsonParameter() + " WHERE id = ?", serialize(payload), id);
    }

    private String jsonParameter() {
        return postgres ? "CAST(? AS jsonb)" : "? FORMAT JSON";
    }

    private ObjectNode validate(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw badRequest("Deal workspace body must be a JSON object");
        }
        ObjectNode payload = ((ObjectNode) request).deepCopy();
        requiredText(payload, "companyName");
        requiredText(payload, "platform");
        optionalLong(payload, "radarCompanyId");
        serialize(payload);
        return payload;
    }

    private JsonNode response(long id, String rawPayload, Timestamp createdAt, Timestamp updatedAt) {
        try {
            JsonNode parsed = objectMapper.readTree(rawPayload);
            if (!parsed.isObject()) throw new IllegalStateException("Stored deal workspace payload is not an object");
            ObjectNode payload = (ObjectNode) parsed;
            applyServerFields(payload, id, createdAt.toInstant(), updatedAt.toInstant());
            return payload;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored deal workspace payload is invalid", error);
        }
    }

    private void applyServerFields(ObjectNode payload, long id, Instant createdAt, Instant updatedAt) {
        payload.put("id", id);
        payload.put("createdAt", createdAt.toString());
        payload.put("updatedAt", updatedAt.toString());
        JsonNode imports = payload.get("importRecords");
        if (imports instanceof ArrayNode array) {
            array.forEach(record -> {
                if (record instanceof ObjectNode object) object.put("dealId", id);
            });
        }
    }

    private String serialize(JsonNode payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Deal workspace payload exceeds the 1 MB limit");
            }
            return json;
        } catch (JsonProcessingException error) {
            throw badRequest("Deal workspace body is not valid JSON");
        }
    }

    private static String requiredText(ObjectNode payload, String field) {
        String value = optionalText(payload, field);
        if (value == null || value.isBlank()) throw badRequest(field + " is required");
        if (value.length() > 500) throw badRequest(field + " is too long");
        return value;
    }

    private static String optionalText(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw badRequest(field + " must be a string");
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    private static Long optionalLong(ObjectNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.canConvertToLong() || value.asLong() <= 0) throw badRequest(field + " must be a positive integer");
        return value.asLong();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal workspace " + id + " not found");
    }
}
