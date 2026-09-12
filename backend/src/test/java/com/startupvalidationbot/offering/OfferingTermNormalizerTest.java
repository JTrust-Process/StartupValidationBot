package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class OfferingTermNormalizerTest {
    @Test
    void parsesExplicitMoneySuffixesWithoutInferringMissingUnits() {
        assertThat(OfferingTermNormalizer.money("$100")).isEqualByComparingTo("100");
        assertThat(OfferingTermNormalizer.money("$1,000")).isEqualByComparingTo("1000");
        assertThat(OfferingTermNormalizer.money("$1.2K")).isEqualByComparingTo("1200");
        assertThat(OfferingTermNormalizer.money("$1.2M")).isEqualByComparingTo("1200000");
        assertThat(OfferingTermNormalizer.money("$500M")).isEqualByComparingTo("500000000");
        assertThat(OfferingTermNormalizer.money("$1.5B")).isEqualByComparingTo("1500000000");
        assertThat(OfferingTermNormalizer.money("$1.90")).isEqualByComparingTo("1.90");
        assertThat(OfferingTermNormalizer.money("$1.90M")).isEqualByComparingTo("1900000");
    }

    @Test
    void parsesOnlySupportedValidDates() {
        assertThat(OfferingTermNormalizer.date("2026-09-02")).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(OfferingTermNormalizer.date("09-02-2026")).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(OfferingTermNormalizer.date("9-2-2026")).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(OfferingTermNormalizer.date("09/02/2026")).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(OfferingTermNormalizer.date("9/2/2026")).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(OfferingTermNormalizer.date("02-30-2026")).isNull();
        assertThat(OfferingTermNormalizer.date("02-09-26")).isNull();
    }

    @Test
    void canonicalizesExplicitSecurityLabelsAndRejectsSentenceFragments() {
        assertThat(OfferingTermNormalizer.security("SAFE A SAFE allows an investor to make a cash investment"))
                .isEqualTo("SAFE");
        assertThat(OfferingTermNormalizer.security("Common Stock shares issued by the company"))
                .isEqualTo("Common Stock");
        assertThat(OfferingTermNormalizer.security("token standard")).isEqualTo("Token");
        assertThat(OfferingTermNormalizer.security("offered on or off this investment platform")).isNull();
        assertThat(OfferingTermNormalizer.security("and fees that may not be transparent")).isNull();
    }
}
