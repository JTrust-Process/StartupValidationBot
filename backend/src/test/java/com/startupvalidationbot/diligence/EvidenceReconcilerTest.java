package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EvidenceReconcilerTest {
    private final EvidenceReconciler reconciler = new EvidenceReconciler();

    @Test
    void gmvAndRevenueAreDifferentMetricsRatherThanAConflict() {
        assertThat(reconciler.conflicts("revenue", "FY2025", new BigDecimal("35992"),
                "GMV", "FY2025", new BigDecimal("150000"))).isFalse();
    }

    @Test
    void sameMetricAndPeriodWithDifferentValuesConflicts() {
        assertThat(reconciler.conflicts("sales", "FY2025", new BigDecimal("35992"),
                "revenue", "FY2025", new BigDecimal("42000"))).isTrue();
    }

    @Test
    void filedAndPlatformTermsRemainSeparateAndDiscrepanciesAreNamed() {
        assertThat(reconciler.reconcile(Map.of("OFFERINGAMOUNT", "100000", "SECURITYOFFEREDTYPE", "SAFE"),
                Map.of("targetAmount", "120000", "securityType", "Preferred Stock")))
                .contains("The target amount differs between SEC and platform evidence.",
                        "Security type differs between the SEC filing and campaign page.");
    }
}
