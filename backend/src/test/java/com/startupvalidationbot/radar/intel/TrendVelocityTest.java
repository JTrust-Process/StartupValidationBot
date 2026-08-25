package com.startupvalidationbot.radar.intel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.intel.TrendVelocity.Confidence;
import com.startupvalidationbot.radar.intel.TrendVelocity.Direction;

class TrendVelocityTest {

    @Test
    void refusesToInventAPercentageWithoutAPriorWindow() {
        var velocity = TrendVelocity.compute(5, 0, false, 30);

        assertThat(velocity.direction()).isEqualTo(Direction.NEW);
        assertThat(velocity.sufficientHistory()).isFalse();
        assertThat(velocity.note()).doesNotContain("%").contains("5 companies");
    }

    @Test
    void refusesToInventAPercentageFromATinyPriorWindow() {
        var velocity = TrendVelocity.compute(4, 2, true, 30);

        assertThat(velocity.sufficientHistory()).isFalse();
        assertThat(velocity.note()).doesNotContain("%");
        assertThat(velocity.direction()).isEqualTo(Direction.RISING);
    }

    @Test
    void reportsAPercentageOnceThereIsEnoughHistory() {
        var velocity = TrendVelocity.compute(9, 4, true, 30);

        assertThat(velocity.sufficientHistory()).isTrue();
        assertThat(velocity.direction()).isEqualTo(Direction.RISING);
        assertThat(velocity.note()).contains("%");
    }

    @Test
    void detectsCoolingAndSteadyTrends() {
        assertThat(TrendVelocity.compute(3, 8, true, 30).direction()).isEqualTo(Direction.COOLING);
        assertThat(TrendVelocity.compute(5, 5, true, 30).direction()).isEqualTo(Direction.STEADY);
    }

    @Test
    void gradesConfidenceByEvidenceNotByStrength() {
        assertThat(TrendVelocity.confidence(7, true, 3)).isEqualTo(Confidence.HIGH);
        assertThat(TrendVelocity.confidence(4, false, 1)).isEqualTo(Confidence.MEDIUM);
        assertThat(TrendVelocity.confidence(2, false, 1)).isEqualTo(Confidence.LOW);
    }
}
