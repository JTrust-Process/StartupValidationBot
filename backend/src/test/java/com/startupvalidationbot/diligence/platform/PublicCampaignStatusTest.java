package com.startupvalidationbot.diligence.platform;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PublicCampaignStatusTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Offering is open for investment.|ACTIVE", "Currently accepting investments.|ACTIVE",
        "Accepting reservations only.|RESERVATION", "Reserve your investment.|RESERVATION",
        "Offering coming soon.|COMING_SOON", "Offering is closing soon.|CLOSING_SOON",
        "Successfully funded.|FUNDED", "Offering is closed.|CLOSED", "Campaign ended.|CLOSED",
        "Offering withdrawn.|WITHDRAWN", "Offering terminated.|TERMINATED"
    })
    void distinguishesExplicitPublicStatuses(String statement, String status) {
        var value = PublicCampaignStatus.inspect("<main><h1>Acme</h1><p>" + statement + "</p></main>");
        assertThat(value.status().name()).isEqualTo(status);
        assertThat(value.excerpt()).contains(statement.replace(".", ""));
    }
    @ParameterizedTest @ValueSource(strings = {
        "Not open for investment.", "Not currently open for investment.", "We will be open for investment.",
        "If approved, invest now.", "We may be open for investment.", "The previous offering is closed.",
        "We would be open for investment if approved.", "We are not accepting reservations.", "Private company research."
    })
    void negatedConditionalFutureAndMissingClaimsRemainUnknown(String statement) {
        assertThat(PublicCampaignStatus.inspect("<p>" + statement + "</p>").status().name()).isEqualTo("UNKNOWN");
    }
    @Test void siteNavigationScriptsAndFootersDoNotEstablishOpening() {
        assertThat(PublicCampaignStatus.inspect("<header>Invest now</header><nav>Invest now</nav><footer>Invest now</footer><script>Invest now</script><main><h1>Acme</h1></main>").status().name()).isEqualTo("UNKNOWN");
    }
    @Test void inconsistentCampaignStatementsDoNotSelectTheMoreFavorableStatus() {
        var result = PublicCampaignStatus.inspect("<p>Offering is open for investment.</p><p>Offering closed.</p>");
        assertThat(result.status().name()).isEqualTo("UNKNOWN");
        assertThat(result.explanation()).startsWith("Conflicting public status");
    }
    @ParameterizedTest @CsvSource(delimiter = '|', value = {
        "<title>Just a moment...</title>|BOT_CHALLENGE", "<p>Verify you are human</p>|BOT_CHALLENGE",
        "<h1>Sign in</h1>|LOGIN_WALL", "<input type='password'>|LOGIN_WALL"
    })
    void rejectsSuccessResponseWalls(String html, String outcome) { assertThat(PublicCampaignStatus.wall(html)).isEqualTo(outcome); }
}
