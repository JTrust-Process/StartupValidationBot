package com.startupvalidationbot.radar.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.startupvalidationbot.radar.source.HeadlineCompanyName.Confidence;

class HeadlineCompanyNameTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            // The four behaviours specified for this extractor.
            "Acme Robotics raises $20M Series A;Acme Robotics",
            "Beta Systems launches agent platform;Beta Systems",
            "Fintech startup Acme raises $15M led by Sequoia;Acme",
            "Acme secures $8M seed round;Acme",

            // Verb coverage.
            "Orbit Forge raised $4M in seed funding;Orbit Forge",
            "Nimbus lands $30M Series B;Nimbus",
            "Cirrus Data closes $12M round;Cirrus Data",
            "Helix unveils its new inference engine;Helix",
            "Vector Labs emerges from stealth with $9M;Vector Labs",
            "Quanta exits stealth;Quanta",
            "Stripe acquires Bridge;Stripe",
            "Loom acquired by Atlassian;Loom",
            "Northwind Systems announces $50M growth round;Northwind Systems",

            // Descriptor stripping.
            "AI coding startup Cursor raises $100M;Cursor",
            "Israeli cybersecurity company Wiz secures new funding;Wiz",
            "Robotics firm Figure lands a major partnership;Figure",

            // Editorial prefixes and publisher suffixes.
            "Exclusive: Acme Robotics raises $20M Series A;Acme Robotics",
            "Acme Robotics raises $20M Series A - TechCrunch;Acme Robotics",
            "Breaking: Nimbus lands $30M Series B | VentureBeat;Nimbus"
    })
    void extractsCompanyNamesFromCommonHeadlines(String headline, String expected) {
        var extraction = HeadlineCompanyName.extract(headline);
        assertThat(extraction.usable()).as(headline).isTrue();
        assertThat(extraction.name()).isEqualTo(expected);
        assertThat(extraction.confidence()).isNotEqualTo(Confidence.NONE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Why every startup should rethink pricing",
            "How to raise a seed round in 2026",
            "The 10 best developer tools of the year",
            "Meet the founders building agent security",
            "Venture funding rebounds in the third quarter",
            "A new startup raises questions about AI safety",
            "This company launches a product nobody asked for",
            "Investors closed a record number of deals",
            "Startup raises $5M",
            "raises $20M",
            "Orbit Forge"
    })
    void refusesHeadlinesWithoutAConfidentCompanyName(String headline) {
        var extraction = HeadlineCompanyName.extract(headline);
        assertThat(extraction.usable()).as(headline).isFalse();
        assertThat(extraction.confidence()).isEqualTo(Confidence.NONE);
        assertThat(extraction.name()).isEmpty();
    }

    @Test
    void treatsNullAndBlankHeadlinesAsUnusable() {
        assertThat(HeadlineCompanyName.extract(null).usable()).isFalse();
        assertThat(HeadlineCompanyName.extract("   ").usable()).isFalse();
    }

    @Test
    void gradesShortUnambiguousNamesHigherThanDescriptorDerivedNames() {
        assertThat(HeadlineCompanyName.extract("Nimbus lands $30M Series B").confidence())
                .isEqualTo(Confidence.HIGH);
        assertThat(HeadlineCompanyName.extract("AI coding startup Cursor raises $100M").confidence())
                .isEqualTo(Confidence.MEDIUM);
    }
}
