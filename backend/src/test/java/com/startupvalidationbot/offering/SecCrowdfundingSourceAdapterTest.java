package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

class SecCrowdfundingSourceAdapterTest {
    @Test
    void boundsRecentIndexResponseSizeConfiguration() {
        assertThat(SecCrowdfundingSourceAdapter.boundedIndexBytes(32_000_000)).isEqualTo(32_000_000);
        assertThat(SecCrowdfundingSourceAdapter.boundedIndexBytes(100)).isEqualTo(1_000_000);
        assertThat(SecCrowdfundingSourceAdapter.boundedIndexBytes(100_000_000)).isEqualTo(50_000_000);
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
