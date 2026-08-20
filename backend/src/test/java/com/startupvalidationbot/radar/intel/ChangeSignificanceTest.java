package com.startupvalidationbot.radar.intel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;

class ChangeSignificanceTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "FUNDING_ROUND;;MAJOR",
            "ACQUISITION;;MAJOR",
            "SHUTDOWN;;MAJOR",
            "NEW_INVESTOR;led by Sequoia Capital;MAJOR",
            "NEW_INVESTOR;led by a regional fund;IMPORTANT",
            "MAJOR_CUSTOMER;signed a new customer;IMPORTANT",
            "MAJOR_CUSTOMER;multi-year enterprise agreement;MAJOR",
            "FOUNDER_CHANGE;;IMPORTANT",
            "ACCELERATOR;;IMPORTANT",
            "REGULATORY;;IMPORTANT",
            "PRODUCT_LAUNCH;;INTERESTING",
            "PARTNERSHIP;;INTERESTING",
            "PARTNERSHIP;major strategic partnership;IMPORTANT",
            "SECTOR;;INTERESTING",
            "JOB_OPENINGS;;MINOR",
            "WEBSITE;;MINOR",
            "DESCRIPTION_WORDING;;MINOR",
            "SOMETHING_ELSE;;MINOR"
    })
    void assignsTiersDeterministically(String changeType, String evidence, String expectedTier) {
        var assessment = ChangeSignificance.classify(changeType, Double.NaN, evidence);
        assertThat(assessment.tier()).isEqualTo(Tier.valueOf(expectedTier));
        assertThat(assessment.whyItMatters()).isNotBlank();
    }

    @Test
    void gradesTractionByMagnitude() {
        assertThat(ChangeSignificance.classify("TRACTION_GROWTH", 1.40, "").tier()).isEqualTo(Tier.MAJOR);
        assertThat(ChangeSignificance.classify("TRACTION_GROWTH", 0.51, "").tier()).isEqualTo(Tier.IMPORTANT);
        assertThat(ChangeSignificance.classify("TRACTION_GROWTH", 0.08, "").tier()).isEqualTo(Tier.INTERESTING);
        // A material decline is as informative as growth.
        assertThat(ChangeSignificance.classify("TRACTION_GROWTH", -0.40, "").tier()).isEqualTo(Tier.IMPORTANT);
        assertThat(ChangeSignificance.classify("TRACTION_GROWTH", Double.NaN, "").tier())
                .isEqualTo(Tier.INTERESTING);
    }

    @Test
    void tiersAreOrdered() {
        assertThat(Tier.MAJOR.atLeast(Tier.IMPORTANT)).isTrue();
        assertThat(Tier.IMPORTANT.atLeast(Tier.IMPORTANT)).isTrue();
        assertThat(Tier.INTERESTING.atLeast(Tier.IMPORTANT)).isFalse();
        assertThat(Tier.MINOR.atLeast(Tier.INTERESTING)).isFalse();
    }
}
