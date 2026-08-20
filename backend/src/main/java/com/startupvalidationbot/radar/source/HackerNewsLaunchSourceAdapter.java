package com.startupvalidationbot.radar.source;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Source;
import com.startupvalidationbot.radar.intel.LaunchHeadline;
import com.startupvalidationbot.radar.intel.LaunchHeadline.LaunchPost;

/**
 * Hacker News "Launch HN" / "Show HN" posts, via the official public HN Search API.
 *
 * Chosen over more scraping because it is genuinely reliable: a documented public JSON endpoint, no
 * authentication, no anti-bot controls to work around, and a stable title convention that yields a
 * company name, accelerator and batch without guessing. Launch HN posts are also unusually
 * high-signal - they are founders announcing a company, not journalists writing about one.
 */
@Component
public class HackerNewsLaunchSourceAdapter implements StartupSourceAdapter {
    private static final Logger log = LoggerFactory.getLogger(HackerNewsLaunchSourceAdapter.class);
    private static final String SEARCH_API = "https://hn.algolia.com/api/v1/search_by_date";
    private static final String HN_ITEM = "https://news.ycombinator.com/item?id=";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HackerNewsLaunchSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String sourceType) {
        return "HACKER_NEWS".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<Candidate> discover(Source source, int limit) throws SourceFetchException {
        int hits = Math.max(1, Math.min(limit, 50));
        // "Launch HN" is the founder-announcement convention; the query is quoted so unrelated stories
        // that merely mention the words are not returned.
        String url = SEARCH_API + "?tags=story&hitsPerPage=" + hits + "&query=%22Launch%20HN%22";
        try {
            URI target = PublicSourceUrlPolicy.requirePublicHttpUrl(url);
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "StartupValidationBot-Radar/1.0 (personal research)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SourceFetchException("Hacker News search returned HTTP " + response.statusCode());
            }
            return parse(source, response.body(), hits);
        } catch (SourceFetchException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException("Hacker News request was interrupted", error);
        } catch (Exception error) {
            throw new SourceFetchException("Unable to fetch Hacker News: " + error.getMessage(), error);
        }
    }

    List<Candidate> parse(Source source, String responseBody, int limit) throws SourceFetchException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode hits = root.path("hits");
            if (!hits.isArray()) {
                throw new SourceFetchException("Hacker News response did not contain a hits array");
            }

            List<Candidate> candidates = new ArrayList<>();
            int skipped = 0;
            for (JsonNode hit : hits) {
                if (candidates.size() >= limit) break;
                String title = hit.path("title").asText("");
                LaunchPost post = LaunchHeadline.parse(title);
                if (!post.usable()) {
                    skipped++;
                    continue;
                }

                String objectId = hit.path("objectID").asText("");
                String submittedUrl = hit.path("url").asText("");
                String storyText = hit.path("story_text").asText("");
                String description = post.description().isBlank() ? storyText : post.description();

                candidates.add(new Candidate(source.sourceKey(),
                        objectId.isBlank() ? title : objectId,
                        post.companyName(),
                        // The submitted URL is normally the company's own site. Publisher and aggregator
                        // hosts are rejected downstream by CompanyIdentity, so this cannot poison identity.
                        submittedUrl,
                        trim(description, 4000),
                        "Unknown",
                        List.of(),
                        null,
                        null,
                        objectId.isBlank() ? submittedUrl : HN_ITEM + objectId,
                        parseCreatedAt(hit.path("created_at").asText("")),
                        trim(title + "\n" + storyText, 40000),
                        post.accelerator(),
                        post.batch()));
            }
            if (skipped > 0) {
                log.info("radar_hn_skipped source={} skipped={} accepted={}", source.sourceKey(), skipped,
                        candidates.size());
            }
            return List.copyOf(candidates);
        } catch (SourceFetchException error) {
            throw error;
        } catch (Exception error) {
            throw new SourceFetchException("Unable to parse Hacker News response: " + error.getMessage(), error);
        }
    }

    private static LocalDateTime parseCreatedAt(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault());
        } catch (DateTimeParseException error) {
            return LocalDateTime.now();
        }
    }

    private static String trim(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
