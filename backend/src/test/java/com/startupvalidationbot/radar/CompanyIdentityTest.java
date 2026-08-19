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

    @Test
    void refusesPublisherAndAggregatorHostsAsCompanyDomains() {
        // radar_companies.domain is UNIQUE. Accepting a publisher host would merge every article
        // from that publisher into a single company record.
        assertThat(CompanyIdentity.normalizeDomain("https://techcrunch.com/2026/08/18/acme-raises-20m/"))
                .isNull();
        assertThat(CompanyIdentity.normalizeDomain("https://www.producthunt.com/posts/acme")).isNull();
        assertThat(CompanyIdentity.normalizeDomain("https://www.ycombinator.com/companies/acme")).isNull();
        assertThat(CompanyIdentity.normalizeDomain("https://a16z.com/portfolio/acme")).isNull();
        assertThat(CompanyIdentity.normalizeDomain("https://acme.substack.com/p/launch")).isNull();
    }

    @Test
    void stillAcceptsRealCompanyDomains() {
        assertThat(CompanyIdentity.normalizeDomain("https://acme-robotics.com/about"))
                .isEqualTo("acme-robotics.com");
        assertThat(CompanyIdentity.normalizeDomain("https://app.acme.io")).isEqualTo("app.acme.io");
    }

    @Test
    void detectsNonCompanyHostsIncludingSubdomains() {
        assertThat(CompanyIdentity.isNonCompanyHost("news.ycombinator.com")).isTrue();
        assertThat(CompanyIdentity.isNonCompanyHost("www.techcrunch.com")).isTrue();
        assertThat(CompanyIdentity.isNonCompanyHost("acme.com")).isFalse();
        assertThat(CompanyIdentity.isNonCompanyHost("")).isFalse();
        assertThat(CompanyIdentity.isNonCompanyHost(null)).isFalse();
    }
}
