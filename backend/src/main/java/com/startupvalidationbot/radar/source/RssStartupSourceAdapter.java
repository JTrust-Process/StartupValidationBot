package com.startupvalidationbot.radar.source;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.startupvalidationbot.radar.ContentHash;
import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Source;
import com.startupvalidationbot.radar.source.HeadlineCompanyName.Extraction;

@Component
public class RssStartupSourceAdapter implements StartupSourceAdapter {
    private static final Logger log = LoggerFactory.getLogger(RssStartupSourceAdapter.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public boolean supports(String sourceType) {
        return "RSS".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<Candidate> discover(Source source, int limit) throws SourceFetchException {
        if (source.url() == null || source.url().isBlank()) {
            throw new SourceFetchException("RSS source is missing a URL");
        }
        try {
            HttpResponse<String> response = fetch(PublicSourceUrlPolicy.requirePublicHttpUrl(source.url()), 0);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SourceFetchException("RSS source returned HTTP " + response.statusCode());
            }
            return parse(source, response.body(), limit);
        } catch (SourceFetchException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException("RSS request was interrupted", error);
        } catch (Exception error) {
            throw new SourceFetchException("Unable to fetch RSS source: " + error.getMessage(), error);
        }
    }

    private HttpResponse<String> fetch(URI uri, int redirectCount) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .header("User-Agent", "StartupValidationBot-Radar/1.0 (+personal research tool)")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            if (redirectCount >= 3) {
                throw new SourceFetchException("RSS source redirected too many times");
            }
            String location = response.headers().firstValue("location")
                    .orElseThrow(() -> new SourceFetchException("RSS redirect is missing a location"));
            URI next = PublicSourceUrlPolicy.requirePublicHttpUrl(uri.resolve(location).toString());
            return fetch(next, redirectCount + 1);
        }
        return response;
    }

    public List<Candidate> parse(Source source, String xml, int limit) throws SourceFetchException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("item");
            if (items.getLength() == 0) {
                items = document.getElementsByTagName("entry");
            }

            List<Candidate> candidates = new ArrayList<>();
            int skipped = 0;
            for (int index = 0; index < items.getLength() && candidates.size() < limit; index++) {
                Element item = (Element) items.item(index);
                String title = text(item, "title");
                String link = atomLink(item);
                String description = firstNonBlank(text(item, "description"), text(item, "summary"),
                        text(item, "content"));
                if (title.isBlank()) {
                    continue;
                }
                List<String> categories = categories(item);
                String externalId = firstNonBlank(text(item, "guid"), text(item, "id"), link,
                        ContentHash.sha256(title).substring(0, 24));
                String cleanDescription = stripHtml(description);
                // Categories classify the item but are not prose evidence. Keeping them out of raw
                // text prevents a tag such as "Thrive Capital" from becoming a claimed investor.
                String rawText = String.join("\n", title, cleanDescription);

                // A headline is not a company name. Extract one deterministically, and skip company
                // creation entirely when we cannot do so confidently: a junk identity is worse than a
                // missed article, and the article itself stays available in the feed.
                Extraction extraction = HeadlineCompanyName.extract(title);
                if (!extraction.usable()) {
                    skipped++;
                    log.info("radar_headline_skipped source={} reason=no_confident_company_name title=\"{}\"",
                            source.sourceKey(), title);
                    continue;
                }

                // The feed link is the article, not the startup's own site. Passing it as websiteUrl
                // would store a publisher host in the UNIQUE company domain column and merge every
                // article from that feed into one company. Identity falls back to the company name.
                candidates.add(new Candidate(source.sourceKey(), externalId, extraction.name(), null,
                        cleanDescription,
                        categories.isEmpty() ? "Unknown" : categories.get(0), categories, null, null, link,
                        parseDate(firstNonBlank(text(item, "pubDate"), text(item, "published"), text(item, "updated"))),
                        rawText));
            }
            if (skipped > 0) {
                log.info("radar_headline_skipped_total source={} skipped={} accepted={}", source.sourceKey(),
                        skipped, candidates.size());
            }
            return candidates;
        } catch (Exception error) {
            throw new SourceFetchException("Unable to parse RSS/Atom source: " + error.getMessage(), error);
        }
    }

    private static List<String> categories(Element item) {
        List<String> categories = new ArrayList<>();
        NodeList nodes = item.getElementsByTagName("category");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element category = (Element) nodes.item(index);
            String value = firstNonBlank(category.getAttribute("term"), category.getTextContent());
            if (!value.isBlank() && categories.stream().noneMatch(value::equalsIgnoreCase)) {
                categories.add(value.trim());
            }
        }
        return categories;
    }

    private static String atomLink(Element item) {
        String link = text(item, "link");
        if (!link.isBlank()) {
            return link;
        }
        NodeList nodes = item.getElementsByTagName("link");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (!element.getAttribute("href").isBlank()) {
                return element.getAttribute("href").trim();
            }
        }
        return "";
    }

    private static String text(Element item, String tagName) {
        NodeList nodes = item.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME, DateTimeFormatter.ISO_ZONED_DATE_TIME)) {
            try {
                return ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (RuntimeException ignored) {
                try {
                    return OffsetDateTime.parse(value, formatter).toLocalDateTime();
                } catch (RuntimeException ignoredAgain) {
                    // Try the next supported feed date format.
                }
            }
        }
        return LocalDateTime.now();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
