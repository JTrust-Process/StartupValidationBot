package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class SecCrowdfundingSourceAdapterTest {
    private static final String INDEX = "https://www.sec.gov/Archives/edgar/data/1/000000000126000001/0000000001-26-000001-index.html";

    @Test void primaryXmlAndOneExplicitSmallTermsExhibitHaveIndependentProvenance() {
        SecFilingClient client = org.mockito.Mockito.mock(SecFilingClient.class);
        String directory = INDEX.substring(0, INDEX.lastIndexOf('/') + 1);
        org.mockito.Mockito.when(client.getText(INDEX, 1_000_000)).thenReturn(row("primary.xml", "C", "Form C", 1000)
                + row("safe.html", "EX-1", "SAFE agreement", 1500) + row("deck.html", "EX-99", "Pitch deck", 2000));
        org.mockito.Mockito.when(client.getText(directory + "primary.xml", 8_000_000)).thenReturn("<nameOfIssuer>Acme</nameOfIssuer>"
                + "<companyName>Wefunder Portal LLC</companyName><commissionCik>0001670254</commissionCik>"
                + "<securityOfferedType>Other</securityOfferedType><securityOfferedOtherDesc>Simple Agreement for Future Equity (SAFE)</securityOfferedOtherDesc>"
                + "<revenueMostRecentFiscalYear>120000</revenueMostRecentFiscalYear>");
        org.mockito.Mockito.when(client.getText(directory + "safe.html", 8_000_000)).thenReturn("<html><table><tr><th>Minimum Investment</th><td>$100</td></tr>"
                + "<tr><th>Post-Money Valuation Cap</th><td>$5,000,000</td></tr></table><a href='https://wefunder.com/acme'>Campaign</a></html>");
        var parsed = new SecCrowdfundingSourceAdapter(client, 40, 32_000_000, 2025, 8).enrich(indexCandidate());
        assertThat(parsed.securityType()).isEqualTo("SAFE"); assertThat(parsed.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(parsed.valuationOrCap()).isEqualTo("5000000 cap"); assertThat(parsed.offeringUrl()).isEqualTo("https://wefunder.com/acme");
        assertThat(parsed.facts()).containsEntry("_secDocumentsAttempted", "2").containsEntry("_secPlatform.state", "SEC_CONFIRMED")
                .containsEntry("_sourceUrl.REVENUEMOSTRECENTFISCALYEAR", directory + "primary.xml")
                .containsEntry("_sourceUrl.minimumInvestment", directory + "safe.html");
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).getText(directory + "deck.html", 8_000_000);
    }

    @Test void choosesSmallPrimaryXmlAndNeverDownloadsIrrelevantOversizedExhibit() {
        SecFilingClient client = org.mockito.Mockito.mock(SecFilingClient.class);
        org.mockito.Mockito.when(client.getText(INDEX, 1_000_000)).thenReturn(
                row("deck.pdf", "EX-99", "Investor deck", 40_000_000) + row("primary.xml", "C", "Form C", 1000));
        String document = INDEX.substring(0, INDEX.lastIndexOf('/') + 1) + "primary.xml";
        org.mockito.Mockito.when(client.getText(document, 8_000_000)).thenReturn("<edgarSubmission><nameOfIssuer>Acme</nameOfIssuer>"
                + "<minimumInvestment>250</minimumInvestment><revenueMostRecentFiscalYear>120000</revenueMostRecentFiscalYear>"
                + "<revenuePriorFiscalYear>50000</revenuePriorFiscalYear><offeringUrl>https://wefunder.com/acme</offeringUrl></edgarSubmission>");
        var result = new SecCrowdfundingSourceAdapter(client, 40, 32_000_000, 2025, 8).enrich(indexCandidate());
        assertThat(result.minimumInvestment()).isEqualByComparingTo("250");
        assertThat(result.facts()).containsEntry("REVENUEMOSTRECENTFISCALYEAR", "120000")
                .containsEntry("_secStatus", "SUCCESS").containsEntry("_secDocumentsAttempted", "1");
        assertThat(result.offeringUrl()).isEqualTo("https://wefunder.com/acme");
        assertThat(result.valuationOrCap()).isNull();
        assertThat(result.amountRaised()).isNull();
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(2)).getText(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test void skipsOversizedPrimaryAndUsesRelevantStructuredAlternativeWithinBound() {
        SecFilingClient client = org.mockito.Mockito.mock(SecFilingClient.class);
        org.mockito.Mockito.when(client.getText(INDEX, 1_000_000)).thenReturn(row("primary.xml", "C", "Form C", 9_000_000)
                + row("alternate.xml", "XML", "Form C structured submission", 1000)
                + row("hostile.xml", "C", "Form C", 100));
        String directory = INDEX.substring(0, INDEX.lastIndexOf('/') + 1);
        org.mockito.Mockito.when(client.getText(directory + "hostile.xml", 8_000_000)).thenReturn("<broken>");
        org.mockito.Mockito.when(client.getText(directory + "alternate.xml", 8_000_000)).thenReturn("<nameOfIssuer>Acme</nameOfIssuer><offeringAmount>10000</offeringAmount>");
        var result = new SecCrowdfundingSourceAdapter(client, 40, 32_000_000, 2025, 8).enrich(indexCandidate());
        assertThat(result.facts()).containsEntry("_secStatus", "SUCCESS").containsEntry("_secOversizedSkipped", "1")
                .containsEntry("_secDocumentsAttempted", "2").containsEntry("_secParseFailures", "1");
        assertThat(result.targetAmount()).isEqualByComparingTo("10000");
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).getText(directory + "primary.xml", 8_000_000);
    }

    @Test void retrievalFailureIsNotAbsenceAndSafeMetadataCannotNominateOtherFilings() {
        SecFilingClient client = org.mockito.Mockito.mock(SecFilingClient.class);
        org.mockito.Mockito.when(client.getText(INDEX, 1_000_000)).thenReturn(row("primary.xml", "C", "Form C", 1000));
        org.mockito.Mockito.when(client.getText(INDEX.substring(0, INDEX.lastIndexOf('/') + 1) + "primary.xml", 8_000_000))
                .thenThrow(new IllegalStateException("SEC returned HTTP 403"));
        var result = new SecCrowdfundingSourceAdapter(client, 40, 32_000_000, 2025, 8).enrich(indexCandidate());
        assertThat(result.facts()).containsEntry("_secStatus", "REQUEST_FAILED");
        assertThat(result.retrievalQuality()).isEqualTo(OfferingDomain.RetrievalQuality.INDEX_ONLY);
        assertThat(SecCrowdfundingSourceAdapter.filingDocuments(INDEX,
                row("https://attacker.example/file.xml", "C", "Form C", 10)
                + row("/Archives/other.xml", "C", "Form C", 10))).isEmpty();
        var absent = SecCrowdfundingSourceAdapter.parseSubmission(new SecCrowdfundingSourceAdapter.IndexRecord("1", "Acme", "C", LocalDate.now(), "", "0000000001-26-000001"),
                "<nameOfIssuer>Acme</nameOfIssuer><intermediaryWebsite>https://wefunder.com</intermediaryWebsite>");
        assertThat(absent.offeringUrl()).isNull();
        assertThat(absent.minimumInvestment()).isNull();
        var explicitCap = SecCrowdfundingSourceAdapter.parseSubmission(new SecCrowdfundingSourceAdapter.IndexRecord("1", "Acme", "C/A", LocalDate.now(), "", "0000000001-26-000002"),
                "<nameOfIssuer>Acme</nameOfIssuer><valuationCap>18000000</valuationCap>");
        assertThat(explicitCap.valuationOrCap()).isEqualTo("18000000 cap");
    }

    private static String row(String file, String type, String description, int size) {
        return "<tr><td>1</td><td>" + description + "</td><td><a href=\"" + file + "\">file</a></td><td>" + type + "</td><td>" + size + "</td></tr>";
    }
    private static OfferingDomain.Candidate indexCandidate() {
        return new OfferingDomain.Candidate("Acme", "1", null, "UNKNOWN", null, null, null, INDEX,
                "0000000001-26-000001", null, "C", LocalDate.now(), null, null, null, null, null, null, null,
                "SEC_EDGAR_RECENT_INDEX", Map.of(), OfferingDomain.RetrievalQuality.INDEX_ONLY);
    }
    @Test
    void boundsRecentIndexResponseSizeConfiguration() {
        assertThat(SecCrowdfundingSourceAdapter.boundedIndexBytes(32_000_000)).isEqualTo(32_000_000);
        assertThat(SecCrowdfundingSourceAdapter.boundedIndexBytes(100)).isEqualTo(1_000_000);
        assertThat(SecCrowdfundingSourceAdapter.boundedIndexBytes(100_000_000)).isEqualTo(50_000_000);
    }

    @Test
    void resolvesFlatMasterIndexSubmissionPathThroughItsAccessionDirectory() {
        assertThat(SecCrowdfundingSourceAdapter.submissionTextUrl(
                "edgar/data/2147324/0001872856-26-000296.txt"))
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/2147324/"
                        + "000187285626000296/0001872856-26-000296.txt");
        assertThat(SecCrowdfundingSourceAdapter.submissionTextUrl(
                "edgar/data/2147324/000187285626000296/0001872856-26-000296.txt"))
                .isEqualTo("https://www.sec.gov/Archives/edgar/data/2147324/"
                        + "000187285626000296/0001872856-26-000296.txt");
    }

    @Test
    void parsesRecentIndexAndCompleteSubmissionWithoutGuessingLifecycle() {
        String index = "Header\n0001234567|Acme Technologies Inc.|C/A|2026-08-20|edgar/data/1234567/000123456726000002/0001234567-26-000002.txt\n"
                + "0001234567|Acme Technologies Inc.|C-W|2026-08-21|edgar/data/1234567/000123456726000003/0001234567-26-000003.txt\n"
                + "0001234567|Acme Technologies Inc.|C-U|2026-08-19|edgar/data/1234567/000123456726000004/0001234567-26-000004.txt\n"
                + "0001234567|Acme Technologies Inc.|C-U/A|2026-08-18|edgar/data/1234567/000123456726000005/0001234567-26-000005.txt\n"
                + "0001234567|Acme Technologies Inc.|C-TR|2026-08-17|edgar/data/1234567/000123456726000006/0001234567-26-000006.txt";
        List<SecCrowdfundingSourceAdapter.IndexRecord> records = SecCrowdfundingSourceAdapter.parseIndex(index);
        assertThat(records).extracting(SecCrowdfundingSourceAdapter.IndexRecord::form)
                .containsExactly("C-W", "C/A", "C-U", "C-U/A", "C-TR");

        String submission = "SEC FILE NUMBER: 020-12345\n<nameOfIssuer>Acme Technologies Inc.</nameOfIssuer>"
                + "<issuerWebsite>https://acme.example</issuerWebsite><companyName>Wefunder Portal LLC</companyName>"
                + "<securityOfferedType>Crowd SAFE</securityOfferedType><offeringAmount>100000</offeringAmount>"
                + "<maximumOfferingAmount>1235000</maximumOfferingAmount><deadlineDate>2026-12-31</deadlineDate>";
        var candidate = SecCrowdfundingSourceAdapter.parseSubmission(records.get(1), submission);
        assertThat(candidate.issuerName()).isEqualTo("Acme Technologies Inc.");
        assertThat(candidate.platform()).isEqualTo("Wefunder");
        assertThat(candidate.targetAmount()).isEqualByComparingTo("100000");
        assertThat(candidate.deadline()).isEqualTo(LocalDate.of(2026, 12, 31));

        var usDeadline = SecCrowdfundingSourceAdapter.parseSubmission(records.get(1), submission
                .replace("2026-12-31", "12-31-2026"));
        assertThat(usDeadline.deadline()).isEqualTo(LocalDate.of(2026, 12, 31));

        var malformed = SecCrowdfundingSourceAdapter.parseSubmission(records.get(4), "<unexpected><xml/></unexpected>");
        assertThat(malformed.issuerName()).isEqualTo("Acme Technologies Inc.");
        assertThat(malformed.filingType()).isEqualTo("C-TR");
        var hostileXml = SecCrowdfundingSourceAdapter.parseSubmission(records.get(4),
                "<XML><!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><nameOfIssuer>&x;</nameOfIssuer></XML>");
        assertThat(hostileXml.issuerName()).isEqualTo("Acme Technologies Inc.");
    }

    @Test
    void joinsActualSecDatasetTablesAndToleratesMalformedRows() throws Exception {
        byte[] zip = zip(
                "ACCESSION_NUMBER\tSUBMISSION_TYPE\tFILING_DATE\tCIK\tFILE_NUMBER\tPERIOD\n"
                        + "0001234567-26-000001\tC\t20260820\t0001234567\t020-12345\t\n"
                        + "bad\tC\tbroken\t\t\t\n",
                "ACCESSION_NUMBER\tNAMEOFISSUER\tISSUERWEBSITE\tCOMPANYNAME\tCOMMISSIONCIK\tCOMMISSIONFILENUMBER\n"
                        + "0001234567-26-000001\tAcme Technologies Inc.\thttps://acme.example\tOpenDeal Portal LLC\t0001234567\t020-12345\n",
                "ACCESSION_NUMBER\tSECURITYOFFEREDTYPE\tOFFERINGAMOUNT\tMAXIMUMOFFERINGAMOUNT\tDEADLINEDATE\n"
                        + "0001234567-26-000001\tEquity\t100000\t500000\t20261231\n");
        var candidates = SecCrowdfundingSourceAdapter.parseDatasetZip(zip);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).platform()).isEqualTo("Republic");
        assertThat(candidates.get(0).maximumAmount()).isEqualByComparingTo("500000");
        assertThat(candidates.get(0).secFilingUrl()).contains("000123456726000001");
    }

    @Test
    void selectsOnlyBoundedRecentOfficialDatasetLinks() {
        String html = "<a href=\"/files/dera/data/crowdfunding-offerings-data-sets/2026q2_cf.zip\">x</a>"
                + "<a href=\"/files/dera/data/crowdfunding-offerings-data-sets/2024q4_cf.zip\">old</a>"
                + "<a href=\"https://attacker.example/2026q1_cf.zip\">bad</a>";
        assertThat(SecCrowdfundingSourceAdapter.datasetUrls(html, 2025))
                .containsExactly("https://www.sec.gov/files/dera/data/crowdfunding-offerings-data-sets/2026q2_cf.zip");
    }

    private static byte[] zip(String submissions, String issuers, String disclosures) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "fixture/FORM_C_SUBMISSION.tsv", submissions);
            add(zip, "fixture/FORM_C_ISSUER_INFORMATION.tsv", issuers);
            add(zip, "fixture/FORM_C_DISCLOSURE.tsv", disclosures);
        }
        return bytes.toByteArray();
    }

    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
