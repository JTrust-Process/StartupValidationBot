package com.startupvalidationbot.radar.intel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LaunchHeadlineTest {

    @Test
    void parsesALaunchPostWithBatch() {
        var post = LaunchHeadline.parse("Launch HN: Arbital (YC S26) - Unified terminal for global markets");

        assertThat(post.usable()).isTrue();
        assertThat(post.companyName()).isEqualTo("Arbital");
        assertThat(post.accelerator()).isEqualTo("Y Combinator");
        assertThat(post.batch()).isEqualTo("S26");
        assertThat(post.description()).isEqualTo("Unified terminal for global markets");
    }

    @Test
    void parsesAShowPostWithoutBatch() {
        var post = LaunchHeadline.parse("Show HN: Beta – Open-source workflow engine");

        assertThat(post.usable()).isTrue();
        assertThat(post.companyName()).isEqualTo("Beta");
        assertThat(post.accelerator()).isEmpty();
        assertThat(post.description()).contains("workflow");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ask HN: how do I hire my first engineer?",
            "Tell HN: the site is down",
            "A normal article about startups"
    })
    void rejectsNonLaunchTitles(String title) {
        assertThat(LaunchHeadline.parse(title).usable()).isFalse();
    }

    @Test
    void rejectsNullAndBlankTitles() {
        assertThat(LaunchHeadline.parse(null).usable()).isFalse();
        assertThat(LaunchHeadline.parse("   ").usable()).isFalse();
    }
}
