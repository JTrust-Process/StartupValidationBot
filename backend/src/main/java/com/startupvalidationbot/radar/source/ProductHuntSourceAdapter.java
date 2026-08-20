package com.startupvalidationbot.radar.source;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.CompanyIdentity;
import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Source;

@Component
public class ProductHuntSourceAdapter implements StartupSourceAdapter {
    private static final URI PRODUCT_HUNT_API = URI.create("https://api.producthunt.com/v2/api/graphql");
    private static final String QUERY = """
            query RadarPosts($first: Int!) {
              posts(first: $first, order: NEWEST) {
                edges { node { id name tagline description website url createdAt topics { edges { node { name } } } } }
              }
            }
            """;

    private final ObjectMapper objectMapper;
    private final String token;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ProductHuntSourceAdapter(ObjectMapper objectMapper,
            @Value("${radar.product-hunt-token:}") String token) {
        this.objectMapper = objectMapper;
        this.token = token;
    }

    @Override
    public boolean supports(String sourceType) {
        return "PRODUCT_HUNT".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<Candidate> discover(Source source, int limit) throws SourceFetchException {
        if (token.isBlank()) {
            throw new SourceFetchException("PRODUCT_HUNT_TOKEN is missing");
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "query", QUERY,
                    "variables", Map.of("first", Math.max(1, Math.min(limit, 50)))));
            HttpRequest request = HttpRequest.newBuilder(PRODUCT_HUNT_API)
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "StartupValidationBot-Radar/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SourceFetchException("Product Hunt returned HTTP " + response.statusCode());
            }
            return parse(source, response.body());
        } catch (SourceFetchException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException("Product Hunt request was interrupted", error);
        } catch (Exception error) {
            throw new SourceFetchException("Unable to fetch Product Hunt: " + error.getMessage(), error);
        }
    }

    List<Candidate> parse(Source source, String responseBody) throws SourceFetchException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.path("errors").isArray() && !root.path("errors").isEmpty()) {
                throw new SourceFetchException("Product Hunt GraphQL error: "
                        + root.path("errors").get(0).path("message").asText("unknown error"));
            }
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode edge : root.path("data").path("posts").path("edges")) {
                JsonNode node = edge.path("node");
                String name = node.path("name").asText("").trim();
                if (name.isBlank()) {
                    continue;
                }
                List<String> categories = new ArrayList<>();
                for (JsonNode topicEdge : node.path("topics").path("edges")) {
                    String topic = topicEdge.path("node").path("name").asText("").trim();
                    if (!topic.isBlank()) {
                        categories.add(topic);
                    }
                }
                String description = firstNonBlank(node.path("description").asText(""),
                        node.path("tagline").asText(""));
                String website = officialWebsite(node.path("website").asText(""));
                String sourceUrl = canonicalSourceUrl(node.path("url").asText(""));
                candidates.add(new Candidate(source.sourceKey(), node.path("id").asText(name), name, website,
                        description, categories.isEmpty() ? "Unknown" : categories.get(0), categories, null, null,
                        sourceUrl, publishedAt(node.path("createdAt").asText("")), name + "\n" + description));
            }
            return candidates;
        } catch (SourceFetchException error) {
            throw error;
        } catch (Exception error) {
            throw new SourceFetchException("Unable to parse Product Hunt response", error);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String officialWebsite(String value) {
        if (value == null || value.isBlank() || CompanyIdentity.normalizeDomain(value) == null) {
            return "";
        }
        return value.trim();
    }

    private static String canonicalSourceUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(value.trim());
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private static LocalDateTime publishedAt(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (RuntimeException ignored) {
            return LocalDateTime.now();
        }
    }
}
