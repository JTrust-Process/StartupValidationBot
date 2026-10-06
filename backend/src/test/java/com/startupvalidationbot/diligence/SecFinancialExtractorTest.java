package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;

class SecFinancialExtractorTest {
    @Test
    void preservesRecentAndPriorFiscalPeriodsFromStructuredSecFacts() {
        var periods = new SecFinancialExtractor().extract(offering(), Map.ofEntries(
                Map.entry("REVENUEMOSTRECENTFISCALYEAR", "35992"),
                Map.entry("NETINCOMEMOSTRECENTFISCALYEAR", "-14000"),
                Map.entry("CASHEQUIMOSTRECENTFISCALYEAR", "9000"),
                Map.entry("TOTALASSETMOSTRECENTFISCALYEAR", "25000"),
                Map.entry("SHORTTERMDEBTMOSTRECENTFISCALYEAR", "2000"),
                Map.entry("TOTALLIABILITIESMOSTRECENTFISCALYEAR", "8000"),
                Map.entry("MOSTRECENTFISCALYEAR", "2025"),
                Map.entry("REVENUEPRIORFISCALYEAR", "12000"),
                Map.entry("NETINCOMEPRIORFISCALYEAR", "-7000"),
                Map.entry("PRIORFISCALYEAR", "2024"),
                Map.entry("LONGTERMDEBTPRIORFISCALYEAR", "3000")));
        assertThat(periods).hasSize(2);
        assertThat(periods.get(0).period()).isEqualTo("2025");
        assertThat(periods.get(0).revenue()).isEqualByComparingTo("35992");
        assertThat(periods.get(0).netIncome()).isEqualByComparingTo("-14000");
        assertThat(periods.get(0).shortTermDebt()).isEqualByComparingTo("2000");
        assertThat(periods.get(0).liabilities()).isEqualByComparingTo("8000");
        assertThat(periods.get(1).period()).isEqualTo("2024");
        assertThat(periods.get(1).revenue()).isEqualByComparingTo("12000");
        assertThat(periods.get(1).longTermDebt()).isEqualByComparingTo("3000");
    }

    private static Offering offering() {
        return new Offering(1, 2L, "Bloomy", "Bloomy, Inc.", "1", "Wefunder", null, null,
                "https://www.sec.gov/example", "accession", null, "C", LocalDate.now(), "REG_CF", null,
                null, null, null, null, null, null, Status.UNKNOWN, "TEST", MatchStatus.CONFIRMED, 100,
                "domain matched", LocalDateTime.now(), LocalDateTime.now());
    }

    @Test void expandsConceptsKeepsRelativePeriodAndExplicitDateAndRejectsMalformedValues() {
        var facts = Map.ofEntries(Map.entry("revenueMostRecentFiscalYear", "$1,200.00"),
                Map.entry("costOfRevenueMostRecentFiscalYear", "200"), Map.entry("grossProfitMostRecentFiscalYear", "1000"),
                Map.entry("netLossMostRecentFiscalYear", "(80)"), Map.entry("currentAssetsMostRecentFiscalYear", "600"),
                Map.entry("currentLiabilitiesMostRecentFiscalYear", "120"), Map.entry("membersEquityMostRecentFiscalYear", "480"),
                Map.entry("fiscalYearEndMostRecent", "2025-06-30"), Map.entry("revenuePriorFiscalYear", "1,2,00"),
                Map.entry("cashPriorFiscalYear", "5M"));
        var extractor = new SecFinancialExtractor();
        var periods = extractor.extract(offering(), facts);
        assertThat(periods).hasSize(1);
        var period = periods.get(0);
        assertThat(period.period()).isEqualTo("MOST_RECENT_FISCAL_YEAR");
        assertThat(period.periodEndingDate()).isEqualTo(LocalDate.of(2025, 6, 30));
        assertThat(period.grossProfit()).isEqualByComparingTo("1000");
        assertThat(period.netIncome()).isEqualByComparingTo("-80");
        assertThat(period.currentAssets()).isEqualByComparingTo("600");
        assertThat(period.currentLiabilities()).isEqualByComparingTo("120");
        assertThat(period.equity()).isEqualByComparingTo("480");
        assertThat(extractor.evidence(offering(), facts)).hasSize(7).allSatisfy(e -> {
            assertThat(e.metadata()).containsKeys("filedLabel", "normalizedConcept", "units", "accessionNumber", "filingDocument", "periodEndingDate");
        });
        assertThat(SecFinancialExtractor.monetaryValue("2e6")).isNull();
        assertThat(SecFinancialExtractor.monetaryValue("1,00")).isNull();
        assertThat(SecFinancialExtractor.monetaryValue("12345678901234567890")).isNull();
        assertThat(SecFinancialExtractor.monetaryValue("100000000000000000")).isNull();
        assertThat(SecFinancialExtractor.monetaryValue("99999999999999999.99")).isEqualByComparingTo("99999999999999999.99");
        assertThat(extractor.extractionStatus(offering(), Map.of())).isEqualTo("NO_FACT_PRESENT");
        assertThat(extractor.extractionStatus(offering(), Map.of("REVENUEMOSTRECENTFISCALYEAR", "bad"))).isEqualTo("PARSE_FAILED");
    }
}
