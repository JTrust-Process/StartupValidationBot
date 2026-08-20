package com.startupvalidationbot.radar.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.RadarDomain.Source;

class HackerNewsLaunchSourceAdapterTest {
    private final HackerNewsLaunchSourceAdapter adapter = new HackerNewsLaunchSourceAdapter(new ObjectMapper());

    private static Source source() {
        return new Source(9L, "hacker-news-launch", "HACKER_NEWS", "Hacker News launches",
                "https://hn.algolia.com/api/v1/search_by_date", "{}", true, null, "NEVER_CHECKED", null);
    }

    @Test
    void supportsOnlyItsOwnSourceType() {
        assertThat(adapter.supports("HACKER_NEWS")).isTrue();
        assertThat(adapter.supports("hacker_news")).isTrue();
        assertThat(adapter.supports("RSS")).isFalse();
    }

    @Test
    void parsesLaunchPostsIncludingAcceleratorProvenance() throws Exception {
        String body = """
                {"hits":[
                  {"objectID":"1001","title":"Launch HN: Arbital (YC S26) - Unified terminal for global markets",
                   "url":"https://arbital.test","created_at":"2026-08-01T10:00:00.000Z","story_text":"We built..."},
                  {"objectID":"1002","title":"Show HN: Beta - Open-source workflow engine",
                   "url":"https://beta.test","created_at":"2026-08-02T10:00:00.000Z","story_text":""},
                  {"objectID":"1003","title":"Ask HN: how do I hire my first engineer?",
                   "url":"","created_at":"2026-08-03T10:00:00.000Z","story_text":""}
                ]}
                """;

        var candidates = adapter.parse(source(), body, 10);

        // The Ask HN post is not a company announcement and must be skipped.
        assertThat(candidates).hasSize(2);

        var arbital = candidates.get(0);
        assertThat(arbital.companyName()).isEqualTo("Arbital");
        assertThat(arbital.accelerator()).isEqualTo("Y Combinator");
        assertThat(arbital.acceleratorBatch()).isEqualTo("S26");
        assertThat(arbital.websiteUrl()).isEqualTo("https://arbital.test");
        assertThat(arbital.sourceUrl()).isEqualTo("https://news.ycombinator.com/item?id=1001");
        assertThat(arbital.externalId()).isEqualTo("1001");

        var beta = candidates.get(1);
        assertThat(beta.companyName()).isEqualTo("Beta");
        assertThat(beta.accelerator()).isEmpty();
    }

    @Test
    void honoursTheRequestedLimit() throws Exception {
        String body = """
                {"hits":[
                  {"objectID":"1","title":"Launch HN: One (YC W25) - A","url":"https://one.test"},
                  {"objectID":"2","title":"Launch HN: Two (YC W25) - B","url":"https://two.test"}
                ]}
                """;

        assertThat(adapter.parse(source(), body, 1)).hasSize(1);
    }

    @Test
    void rejectsAResponseWithoutHits() {
        assertThatThrownBy(() -> adapter.parse(source(), "{\"error\":\"nope\"}", 10))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("hits");
    }

    @Test
    void refusesPrivateNetworkTargets() {
        assertThatThrownBy(() -> PublicSourceUrlPolicy.requirePublicHttpUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(SourceFetchException.class);
    }
}
