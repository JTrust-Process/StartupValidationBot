package com.startupvalidationbot.diligence.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PlatformHttpClientTest {
    @Test
    void enforcesAtMostOneRequestPerSecondForEachPlatform() throws Exception {
        HttpClient transport = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenAnswer(ignored -> new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));
        when(transport.<InputStream>send(any(HttpRequest.class), any())).thenReturn(response);
        PlatformHttpClient client = new PlatformHttpClient(50_000, 10, transport);

        long started = System.nanoTime();
        client.get("STARTENGINE", URI.create("https://www.startengine.com/explore"));
        client.get("STARTENGINE", URI.create("https://www.startengine.com/offering/test"));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).isGreaterThanOrEqualTo(900);
    }
}
