package com.startupvalidationbot.radar.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;

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

    @Test
    @SuppressWarnings("unchecked")
    void followsProductHuntPaginationUntilTheRequestedLimitIsReached() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> firstResponse = mock(HttpResponse.class);
        HttpResponse<String> secondResponse = mock(HttpResponse.class);
        when(firstResponse.statusCode()).thenReturn(200);
        when(secondResponse.statusCode()).thenReturn(200);
        when(firstResponse.body()).thenReturn(page("1", "First", true, "next-page"));
        when(secondResponse.body()).thenReturn(page("2", "Second", false, ""));
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(firstResponse, secondResponse);
        ProductHuntSourceAdapter pagedAdapter = new ProductHuntSourceAdapter(new ObjectMapper(), "test-token", client);

        List<Candidate> candidates = pagedAdapter.discover(source, 30);

        assertThat(candidates).extracting(Candidate::companyName).containsExactly("First", "Second");
        verify(client, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
    }

    private static String page(String id, String name, boolean hasNextPage, String endCursor) {
        return """
                {"data":{"posts":{
                  "pageInfo":{"hasNextPage":%s,"endCursor":"%s"},
                  "edges":[{"node":{
                    "id":"%s","name":"%s","tagline":"Description","description":"",
                    "website":"","url":"https://www.producthunt.com/products/%s",
                    "createdAt":"2026-08-20T07:01:00Z","topics":{"edges":[]}
                  }}]
                }}}
                """.formatted(hasNextPage, endCursor, id, name, name.toLowerCase());
    }
}
