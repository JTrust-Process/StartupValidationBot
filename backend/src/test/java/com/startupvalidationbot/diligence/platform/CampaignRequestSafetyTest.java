package com.startupvalidationbot.diligence.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CampaignRequestSafetyTest {
    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body, String type) {
        HttpResponse<InputStream> value = mock(HttpResponse.class);
        when(value.statusCode()).thenReturn(status);
        when(value.body()).thenAnswer(ignored -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(value.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of(type), "Location", List.of("https://attacker.example/"), "Retry-After", List.of("3600")), (a,b) -> true));
        return value;
    }
    private static HttpClient transport(HttpResponse<InputStream> robots, HttpResponse<InputStream> page) throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.<InputStream>send(any(HttpRequest.class), any())).thenAnswer(invocation ->
                ((HttpRequest) invocation.getArgument(0)).uri().getPath().equals("/robots.txt") ? robots : page);
        return client;
    }
    @Test void robotsCheckedOnceAndOnlyExactPermittedDestinationsRequestedWithoutCredentials() throws Exception {
        var http = transport(response(200, "User-agent: *\nDisallow: /private", "text/plain"), response(200, "<h1>Acme</h1>", "text/html"));
        var client = new PlatformHttpClient(50000, 1, http);
        client.get("WEFUNDER", URI.create("https://wefunder.com/acme"));
        client.get("WEFUNDER", URI.create("https://wefunder.com/another"));
        var sent = ArgumentCaptor.forClass(HttpRequest.class); verify(http, times(3)).send(sent.capture(), any());
        assertThat(sent.getAllValues()).extracting(v -> v.uri().toString()).containsExactly("https://wefunder.com/robots.txt", "https://wefunder.com/acme", "https://wefunder.com/another");
        assertThat(sent.getAllValues()).allMatch(v -> v.method().equals("GET") && v.headers().firstValue("Authorization").isEmpty() && v.headers().firstValue("Cookie").isEmpty());
        assertThat(client.requests("WEFUNDER")).isEqualTo(3);
    }
    @Test void deniedRobotsAndUnavailablePolicyNeverRequestTheCampaign() throws Exception {
        for (var policy : List.of(response(200, "User-agent: *\nDisallow: /", "text/plain"), response(403, "denied", "text/plain"), response(302, "", "text/plain"))) {
            var http = transport(policy, response(200, "<h1>Acme</h1>", "text/html"));
            assertThatThrownBy(() -> new PlatformHttpClient(50000, 1, http).get("WEFUNDER", URI.create("https://wefunder.com/acme"))).isInstanceOf(PlatformHttpClient.FetchFailure.class);
            var sent = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(sent.capture(), any());
            assertThat(sent.getValue().uri().getPath()).isEqualTo("/robots.txt");
        }
    }
    @Test void redirectsNeverEscapeTheHostAndNoLocationIsFollowed() throws Exception {
        var http = transport(response(404, "", "text/plain"), response(302, "", "text/html"));
        assertThatThrownBy(() -> new PlatformHttpClient(50000, 1, http).get("REPUBLIC", URI.create("https://republic.com/acme"))).hasMessageContaining("redirect rejected");
        var sent = ArgumentCaptor.forClass(HttpRequest.class); verify(http, times(2)).send(sent.capture(), any());
        assertThat(sent.getAllValues()).allMatch(r -> r.uri().getHost().equals("republic.com"));
    }
    @Test void rateLimitPreventsImmediateRetriesAcrossCampaigns() throws Exception {
        var http = transport(response(404, "", "text/plain"), response(429, "limited", "text/html"));
        var client = new PlatformHttpClient(50000, 1, http);
        assertThatThrownBy(() -> client.get("REPUBLIC", URI.create("https://republic.com/acme"))).hasMessageContaining("429");
        assertThatThrownBy(() -> client.get("REPUBLIC", URI.create("https://republic.com/another"))).hasMessageContaining("no new public request");
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }
    @ParameterizedTest @ValueSource(strings = { "<title>Just a moment</title>", "<h1>Log in</h1>", "Verify you are human" })
    void successfulChallengeOrLoginBodyIsNotReturnedAsEvidence(String body) throws Exception {
        var http = transport(response(404, "", "text/plain"), response(200, body, "text/html"));
        assertThatThrownBy(() -> new PlatformHttpClient(50000, 1, http).get("WEFUNDER", URI.create("https://wefunder.com/acme"))).isInstanceOf(PlatformHttpClient.FetchFailure.class);
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }
    @ParameterizedTest @ValueSource(strings = {
        "https://wefunder.com:8443/acme", "https://wefunder.com/acme?token=secret", "https://wefunder.com/login",
        "https://republic.com/checkout", "https://startengine.com/offering/acme/invest", "https://127.0.0.1/acme",
        "https://[::1]/acme", "https://attacker.example/acme", "http://wefunder.com/acme"
    })
    void unsafePortsHostsPathsAndQueriesCauseZeroOutboundRequests(String url) {
        HttpClient http = mock(HttpClient.class);
        String host = URI.create(url).getHost();
        String platform = "republic.com".equals(host) ? "REPUBLIC" : "startengine.com".equals(host) ? "STARTENGINE" : "WEFUNDER";
        assertThatThrownBy(() -> new PlatformHttpClient(50000, 1, http).get(platform, URI.create(url))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(http);
    }
}
