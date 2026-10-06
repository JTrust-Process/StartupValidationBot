package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class SecFilingClientTest {
    @Test void cancelsOversizedResponseBeforeBufferingTheEntireBody() {
        var body = new SecFilingClient.BoundedBody(10);
        var subscription = org.mockito.Mockito.mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[8])));
        body.onNext(List.of(ByteBuffer.wrap(new byte[8])));
        assertThatThrownBy(() -> body.getBody().toCompletableFuture().join()).hasCauseInstanceOf(SecFilingClient.DocumentTooLarge.class);
        org.mockito.Mockito.verify(subscription).cancel();
    }
    @Test void acceptsBoundedBodyAndRejectsCredentialAndNonOfficialUrls() {
        var body = new SecFilingClient.BoundedBody(10);
        body.onSubscribe(org.mockito.Mockito.mock(Flow.Subscription.class));
        body.onNext(List.of(ByteBuffer.wrap(new byte[10]))); body.onComplete();
        assertThat(body.getBody().toCompletableFuture().join()).hasSize(10);
        for (String url : List.of("https://www.sec.gov@attacker.example/file", "http://www.sec.gov/file",
                "https://data.sec.gov:444/file", "https://user@www.sec.gov/file", "https://www.sec.gov/file?token=secret")) {
            assertThatThrownBy(() -> SecFilingClient.requireOfficialUrl(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
