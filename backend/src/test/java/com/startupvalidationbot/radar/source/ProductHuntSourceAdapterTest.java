package com.startupvalidationbot.radar.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Source;

class ProductHuntSourceAdapterTest {
    private final ProductHuntSourceAdapter adapter = new ProductHuntSourceAdapter(new ObjectMapper(), "test-token");
    private final Source source = new Source(1L, "product-hunt", "PRODUCT_HUNT", "Product Hunt",
            "https://api.producthunt.com/v2/api/graphql", "{}", true, null, null, null);

    @Test
    void keepsProductHuntAsEvidenceWithoutTreatingItsRedirectAsTheCompanyWebsite() throws Exception {
        String response = """
                {
                  "data": {
                    "posts": {
                      "edges": [{
                        "node": {
                          "id": "1227099",
                          "name": "Example Product",
                          "tagline": "A concise fallback",
                          "description": "A fuller public description",
                          "website": "https://www.producthunt.com/r/ABC123?utm_source=radar",
                          "url": "https://www.producthunt.com/products/example-product?utm_source=radar",
                          "createdAt": "2026-08-20T07:01:00Z",
                          "topics": {"edges": [{"node": {"name": "Developer Tools"}}]}
                        }
                      }]
                    }
                  }
                }
                """;

        Candidate candidate = adapter.parse(source, response).getFirst();

        assertThat(candidate.websiteUrl()).isBlank();
        assertThat(candidate.sourceUrl()).isEqualTo("https://www.producthunt.com/products/example-product");
        assertThat(candidate.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 7, 1));
        assertThat(candidate.description()).isEqualTo("A fuller public description");
        assertThat(candidate.categories()).containsExactly("Developer Tools");
    }

    @Test
    void preservesARealCompanyWebsiteIfTheApiEverReturnsOneDirectly() throws Exception {
        String response = """
                {"data":{"posts":{"edges":[{"node":{
                  "id":"1","name":"Acme","tagline":"Build things","description":"",
                  "website":"https://acme.example/launch","url":"https://www.producthunt.com/products/acme",
                  "createdAt":"2026-08-20T07:01:00-04:00","topics":{"edges":[]}
                }}]}}}
                """;

        Candidate candidate = adapter.parse(source, response).getFirst();

        assertThat(candidate.websiteUrl()).isEqualTo("https://acme.example/launch");
        assertThat(candidate.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 11, 1));
    }
}
