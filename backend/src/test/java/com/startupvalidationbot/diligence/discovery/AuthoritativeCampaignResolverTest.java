package com.startupvalidationbot.diligence.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;

class AuthoritativeCampaignResolverTest {
    private final IssuerWebsiteClient client = mock(IssuerWebsiteClient.class);
    private final AuthoritativeCampaignResolver resolver = new AuthoritativeCampaignResolver(client);
    private Offering offering() {
        Offering offering = mock(Offering.class);
        when(offering.matchStatus()).thenReturn(MatchStatus.CONFIRMED);
        when(offering.secFilingUrl()).thenReturn("https://www.sec.gov/Archives/example.xml");
        return offering;
    }
    @Test void secExplicitCampaignLinkDoesNotRequireAnyPlatformOrIssuerRequest() {
        var result = resolver.resolve(offering(), Map.of("_secCampaign.state", "CONFIRMED", "_secCampaign.url", "https://wefunder.com/acme", "intermediaryCik", "0001670254"));
        assertThat(result.state()).isEqualTo("CONFIRMED"); assertThat(result.url()).isEqualTo("https://wefunder.com/acme");
        assertThat(result.source()).isEqualTo("SEC_FILED_FACT"); verifyNoInteractions(client);
    }
    @Test void issuerHomepageCanProvideExplicitLinkButCannotOverrideFiledPlatformConflict() {
        when(client.homepage("https://acme.example")).thenReturn("<a href='https://wefunder.com/acme'>Invest</a>");
        var result = resolver.resolve(offering(), Map.of("issuerWebsite", "https://acme.example", "intermediaryCik", "0001670254"));
        assertThat(result.state()).isEqualTo("CONFIRMED"); assertThat(result.source()).isEqualTo("ISSUER_WEBSITE_CLAIM");
        var conflict = resolver.resolve(offering(), Map.of("issuerWebsite", "https://acme.example", "intermediaryCik", "0001751525"));
        assertThat(conflict.state()).isEqualTo("AMBIGUOUS"); assertThat(conflict.url()).isNull();
    }
    @Test void rootAndAbsentLinksAreUnresolvedAndIdentityMustRemainIndependent() {
        when(client.homepage("https://acme.example")).thenReturn("<a href='https://wefunder.com/'>Portal</a>");
        assertThat(resolver.resolve(offering(), Map.of("issuerWebsite", "https://acme.example")).url()).isNull();
        Offering ambiguous = offering(); when(ambiguous.matchStatus()).thenReturn(MatchStatus.AMBIGUOUS);
        clearInvocations(client);
        assertThat(resolver.resolve(ambiguous, Map.of("issuerWebsite", "https://acme.example")).url()).isNull();
        verifyNoInteractions(client);
    }
    @Test void blockedIssuerPageIsDegradedWithoutFallbackGuessing() {
        when(client.homepage("https://acme.example")).thenThrow(new IllegalStateException("HTTP 403"));
        var result = resolver.resolve(offering(), Map.of("issuerWebsite", "https://acme.example"));
        assertThat(result.state()).isEqualTo("UNAVAILABLE"); assertThat(result.url()).isNull();
        verify(client, times(1)).homepage("https://acme.example");
    }
    @Test void robotsRulesFailClosedForWildcardRestrictions() {
        assertThat(IssuerWebsiteClient.disallowed("User-agent: *\nDisallow: /", "/")).isTrue();
        assertThat(IssuerWebsiteClient.disallowed("User-agent: *\nDisallow: /private", "/")).isFalse();
        assertThat(IssuerWebsiteClient.disallowed("User-agent: StartupIntelligence\nDisallow: /invest*", "/invest")).isTrue();
        assertThat(IssuerWebsiteClient.disallowed("User-agent: *\nUser-agent: OtherBot\nDisallow: /", "/")).isTrue();
    }
    @Test void confirmedIssuerLinkAndObservationTimeSurviveLaterMissingFetch() {
        Offering known = offering(); when(known.offeringUrl()).thenReturn("https://wefunder.com/acme");
        var result = resolver.resolve(known, Map.of("_issuerCampaign.state", "CONFIRMED", "_issuerCampaign.url", "https://wefunder.com/acme",
                "_issuerCampaign.source", "ISSUER_WEBSITE_CLAIM", "_issuerCampaign.sourceUrl", "https://acme.example", "_issuerCampaign.observedAt", "2026-08-01T12:00:00"));
        assertThat(result.state()).isEqualTo("CONFIRMED"); assertThat(result.observedAt()).isEqualTo("2026-08-01T12:00:00");
        verifyNoInteractions(client);
    }
}
