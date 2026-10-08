package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ExplicitCampaignLinksTest {
    @Test void exactObservedUrlsOnlyAndPlatformRootsAreNotCampaigns() {
        assertThat(ExplicitCampaignLinks.find("https://wefunder.com/acme https://republic.com/acme https://startengine.com/offering/acme" )).hasSize(3);
        assertThat(ExplicitCampaignLinks.find("Acme company. https://wefunder.com/ https://republic.com/explore https://evil.example/acme https://wefunder.com.attacker.example/acme")).isEmpty();
        assertThat(ExplicitCampaignLinks.canonical("https://wefunder.com/acme#login")).isNull();
        assertThat(ExplicitCampaignLinks.canonical("https://wefunder.com:8443/acme")).isNull();
    }
    @Test void issuerLinksMustBeVisibleAnchorsNotEmbeddedAdvertisingOrScriptUrls() {
        assertThat(ExplicitCampaignLinks.outboundAnchors("<script>https://wefunder.com/other</script><a href='https://wefunder.com/acme'>Invest</a>"))
                .containsExactly("https://wefunder.com/acme");
    }
}
