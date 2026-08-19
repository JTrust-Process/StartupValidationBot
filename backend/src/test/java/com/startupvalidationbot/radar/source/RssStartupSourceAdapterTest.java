package com.startupvalidationbot.radar.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.RadarDomain.Source;

class RssStartupSourceAdapterTest {
    private static Source feed() {
        return new Source(1L, "test-feed", "RSS", "Test feed", "https://feed.test/rss", "{}",
                true, null, "NEVER_CHECKED", null);
    }

    @Test
    void parsesRssWithoutExternalEntities() throws Exception {
        String xml = """
                <rss version="2.0"><channel><item>
                  <title>Orbit Forge raises $4M seed round</title>
                  <link>https://orbitforge.test/launch</link>
                  <guid>orbit-1</guid>
                  <description><![CDATA[Developer infrastructure for satellite operations.]]></description>
                  <category>Space</category><category>Developer Tools</category>
                </item></channel></rss>
                """;

        var candidates = new RssStartupSourceAdapter().parse(feed(), xml, 10);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).companyName()).isEqualTo("Orbit Forge");
        assertThat(candidates.get(0).categories()).containsExactly("Space", "Developer Tools");
        assertThat(candidates.get(0).externalId()).isEqualTo("orbit-1");
        assertThat(candidates.get(0).sourceUrl()).isEqualTo("https://orbitforge.test/launch");
    }

    @Test
    void neverTreatsTheArticleLinkAsTheCompanyWebsite() throws SourceFetchException {
        String xml = """
                <rss version="2.0"><channel>
                  <item><title>Acme Robotics raises $20M Series A</title>
                    <link>https://techcrunch.com/2026/08/18/acme-robotics-raises-20m/</link>
                    <guid>tc-1</guid><description>Acme raised a round.</description></item>
                  <item><title>Beta Systems launches agent platform</title>
                    <link>https://techcrunch.com/2026/08/18/beta-systems-launches/</link>
                    <guid>tc-2</guid><description>Beta launched a product.</description></item>
                </channel></rss>
                """;

        var candidates = new RssStartupSourceAdapter().parse(feed(), xml, 10);

        assertThat(candidates).hasSize(2);
        // radar_companies.domain is UNIQUE: a publisher host here would merge the whole feed into one
        // company record.
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.websiteUrl()).isNull());
        assertThat(candidates).extracting("companyName")
                .containsExactly("Acme Robotics", "Beta Systems");
        assertThat(candidates.get(0).sourceUrl())
                .isEqualTo("https://techcrunch.com/2026/08/18/acme-robotics-raises-20m/");
    }

    @Test
    void skipsHeadlinesWithoutAConfidentCompanyName() throws SourceFetchException {
        String xml = """
                <rss version="2.0"><channel>
                  <item><title>Why every startup should rethink pricing</title>
                    <link>https://techcrunch.com/a</link><guid>op-1</guid><description>Opinion.</description></item>
                  <item><title>The 10 best developer tools of the year</title>
                    <link>https://techcrunch.com/b</link><guid>op-2</guid><description>Listicle.</description></item>
                  <item><title>Nimbus lands $30M Series B</title>
                    <link>https://techcrunch.com/c</link><guid>news-1</guid><description>Funding.</description></item>
                </channel></rss>
                """;

        var candidates = new RssStartupSourceAdapter().parse(feed(), xml, 10);

        // The commentary items are preserved in the feed but must not become company identities.
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).companyName()).isEqualTo("Nimbus");
    }

    @Test
    void blocksPrivateNetworkFeedTargets() {
        assertThatThrownBy(() -> PublicSourceUrlPolicy.requirePublicHttpUrl("http://127.0.0.1/private-feed"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("private or local");
    }
}
