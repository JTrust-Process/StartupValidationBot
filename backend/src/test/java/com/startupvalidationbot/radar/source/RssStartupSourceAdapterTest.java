package com.startupvalidationbot.radar.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.RadarDomain.Source;

class RssStartupSourceAdapterTest {
    @Test
    void parsesRssWithoutExternalEntities() throws Exception {
        String xml = """
                <rss version="2.0"><channel><item>
                  <title>Orbit Forge</title>
                  <link>https://orbitforge.test/launch</link>
                  <guid>orbit-1</guid>
                  <description><![CDATA[Developer infrastructure for satellite operations.]]></description>
                  <category>Space</category><category>Developer Tools</category>
                </item></channel></rss>
                """;
        Source source = new Source(1L, "test-feed", "RSS", "Test feed", "https://feed.test/rss", "{}",
                true, null, "NEVER_CHECKED", null);

        var candidates = new RssStartupSourceAdapter().parse(source, xml, 10);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).companyName()).isEqualTo("Orbit Forge");
        assertThat(candidates.get(0).categories()).containsExactly("Space", "Developer Tools");
        assertThat(candidates.get(0).externalId()).isEqualTo("orbit-1");
    }

    @Test
    void blocksPrivateNetworkFeedTargets() {
        assertThatThrownBy(() -> PublicSourceUrlPolicy.requirePublicHttpUrl("http://127.0.0.1/private-feed"))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("private or local");
    }
}
