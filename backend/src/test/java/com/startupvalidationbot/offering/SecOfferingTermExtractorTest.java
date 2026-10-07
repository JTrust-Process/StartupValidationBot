package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecOfferingTermExtractorTest {
    private Map<String, String> extract(String text) {
        Map<String, String> facts = new TreeMap<>();
        SecOfferingTermExtractor.extract(facts, text);
        return facts;
    }
    @Test void extractsLabeledTermsWithoutDerivingValuationFromRaiseOrPrice() {
        var facts = extract("<table><tr><th>Minimum Investment</th><td>$100</td></tr>"
                + "<tr><th>Valuation Cap</th><td>$16,000,000</td></tr></table>"
                + "Target raise: $500,000. Price per share: $2.50. Discount rate: 20%. Interest rate: 6%. Maturity: 24 months.");
        assertThat(facts).containsEntry("minimumInvestment", "100").containsEntry("valuationOrCap", "16000000 cap")
                .containsEntry("_secTerm.type.valuationOrCap", "VALUATION_CAP")
                .containsEntry("_secTerm.fact.pricePerShare.0.value", "2.5")
                .containsEntry("_secTerm.fact.discountRate.0.value", "20").containsEntry("_secTerm.fact.interestRate.0.value", "6")
                .containsEntry("_secTerm.fact.maturity.0.value", "24 months");
        assertThat(extract("Target raise: $500,000. Price per share: $2.50. 200,000 securities offered.")).doesNotContainKey("valuationOrCap");
    }
    @ParameterizedTest @ValueSource(strings = {
        "The minimum investment amount is reduced from $1,000 to $100.",
        "This amendment will change the Minimum Individual Investment Amount to $100.",
        "We reduce the minimum individual purchase amount from $1,000 to $100.",
        "Minimum subscription: $100", "Minimum investment in this offering: $100"
    }) void minimumLanguageAndExplicitAmendments(String text) { assertThat(extract(text)).containsEntry("minimumInvestment", "100"); }
    @Test void rejectsStatutoryLimitsAndTieredMinimums() {
        assertThat(extract("Reg CF non-accredited investors may invest up to $2,500 or 5% of income. Statutory minimum limit $2,500.")).doesNotContainKey("minimumInvestment");
        var tiered = extract("Minimum investment: $100 for one class. Minimum investment: $1,000 for another class.");
        assertThat(tiered).doesNotContainKey("minimumInvestment").containsKey("_secTerm.ambiguity.minimumInvestment");
    }
    @Test void preservesPostMoneyCapAndPreMoneyValuationMeaning() {
        assertThat(extract("Post-Money Valuation Cap is $5,000,000")).containsEntry("valuationOrCap", "5000000 cap")
                .containsEntry("_secTerm.type.valuationOrCap", "POST_MONEY_VALUATION_CAP");
        assertThat(extract("pre-money valuation of $119,567,315")).containsEntry("valuationOrCap", "119567315 valuation")
                .containsEntry("_secTerm.type.valuationOrCap", "PRE_MONEY_VALUATION");
    }
    @Test void multipleCapsAndConditionalValuationsAreRetainedNotChosen() {
        var multi = extract("SAFE with $25M cap; convertible note with $30M cap.");
        assertThat(multi).doesNotContainKey("valuationOrCap").containsKey("_secTerm.ambiguity.valuationOrCap");
        var conditional = extract("Pre-money valuation of $58M. Early-bird investors in the first $250,000 have a pre-money valuation of $52.2M.");
        assertThat(conditional).doesNotContainKey("valuationOrCap").containsKey("_secTerm.ambiguity.valuationOrCap");
    }
    @Test void specifiedSecuritySubtypeIsSeparateFromRawCategoryAndMultiSecurityIsAmbiguous() {
        Map<String, String> facts = new TreeMap<>(Map.of("SECURITYOFFEREDTYPE", "Other", "SECURITYOFFEREDOTHERDESC", "Simple Agreement for Future Equity (SAFE)"));
        SecOfferingTermExtractor.extract(facts, "");
        assertThat(facts).containsEntry("securityType", "SAFE").containsEntry("_secTerm.rawSecurity", "Other");
        facts = new TreeMap<>(Map.of("SECURITYOFFEREDTYPE", "Other", "SECURITYOFFEREDOTHERDESC", "SAFE and convertible note"));
        SecOfferingTermExtractor.extract(facts, "");
        assertThat(facts).doesNotContainKey("securityType").containsKey("_secTerm.ambiguity.securityType");
    }
    @ParameterizedTest @ValueSource(strings = {
        "Offering completed. Total amount of securities sold: $2,999,063.75",
        "The offering closed having raised $2,999,063.75.",
        "Offering final amount raised: $2,999,063.75."
    }) void explicitCompletedSalesAreCanonical(String text) {
        assertThat(extract(text)).containsEntry("amountRaised", "2999063.75").containsEntry("_secTerm.type.amountRaised", "FINAL_SECURITIES_SOLD");
    }
    @Test void campaignRaisedRequiresCompletionAndCommitmentsNeverBecomeFinalSales() {
        assertThat(extract("The campaign completed. The campaign raised $2,952,013.78.")).containsEntry("amountRaised", "2952013.78");
        assertThat(extract("The campaign raised $2,952,013.78 so far.")).doesNotContainKey("amountRaised");
        var commitments = extract("The offering closed successfully and received investment commitments of $164,575; final accounting has not taken place.");
        assertThat(commitments).doesNotContainKey("amountRaised").containsEntry("_secTerm.fact.investmentCommitments.0.value", "164575")
                .containsEntry("_secTerm.completed", "true");
    }
    @Test void provisionalAndFeeInclusiveAmountsRemainSeparatelyTypedEvidence() {
        assertThat(extract("Final number $974,351 in investments subject to final accounting.")).doesNotContainKey("amountRaised");
        var fees = extract("Offering final amount raised $1,048,901.42 including investor ancillary fees.");
        assertThat(fees).doesNotContainKey("amountRaised").containsEntry("_secTerm.fact.amountRaised.0.type", "FINAL_SECURITIES_SOLD_INCLUDING_FEES");
        assertThat(extract("The offering closed on November 30, 2025. Final closing date: 2025-11-30.")).containsKey("_secTerm.fact.closingDate.0.value");
    }
    @Test void malformedMarkupAndScriptsCannotProvideCanonicalTerms() {
        assertThat(extract("<script>Valuation Cap: $7M</script><p>No filed valuation.</p>")).doesNotContainKey("valuationOrCap");
        assertThat(extract("<p>Minimum Investment: $100</p>")).containsEntry("minimumInvestment", "100");
    }
}
