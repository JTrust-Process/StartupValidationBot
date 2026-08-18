package com.startupvalidationbot.radar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompanyIdentityTest {
    @Test
    void normalizesDomainsAndLegalSuffixesForDeduplication() {
        assertThat(CompanyIdentity.normalizeDomain("https://www.Example.com/products?id=2"))
                .isEqualTo("example.com");
        assertThat(CompanyIdentity.normalizeName("Acme Robotics, Inc."))
                .isEqualTo("acme robotics");
        assertThat(CompanyIdentity.normalizeName("ACME-Robotics LLC"))
                .isEqualTo("acme robotics");
    }

    @Test
    void returnsNullForUnusableDomains() {
        assertThat(CompanyIdentity.normalizeDomain("")).isNull();
        assertThat(CompanyIdentity.normalizeDomain("not a valid url / value")).isNull();
    }
}
