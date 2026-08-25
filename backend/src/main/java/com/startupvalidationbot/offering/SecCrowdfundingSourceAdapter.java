package com.startupvalidationbot.offering;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.radar.source.PublicSourceUrlPolicy;
import com.startupvalidationbot.radar.source.SourceFetchException;

@Component
public class SecCrowdfundingSourceAdapter implements OfferingSourceAdapter {
    static final String DATASETS_PAGE = "https://www.sec.gov/data-research/sec-markets-data/crowdfunding-offerings-data-sets";
    private static final Pattern DATASET_LINK = Pattern.compile("href=[\"']([^\"']*(20\\d{2})q[1-4]_cf\\.zip)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_LINE = Pattern.compile("^(\\d+)\\|([^|]+)\\|(C(?:/A|-U(?:/A)?|-W|-TR))\\|(\\d{4}-\\d{2}-\\d{2})\\|([^|]+)$");
    private static final Pattern ACCESSION = Pattern.compile("(\\d{10}-\\d{2}-\\d{6})");
    private final SecFilingClient client;
    private final int recentLimit;
    private final int baselineStartYear;
    private final int baselineMaxDatasets;

    public SecCrowdfundingSourceAdapter(SecFilingClient client,
            @Value("${offering.sec.recent-limit:40}") int recentLimit,
            @Value("${offering.sec.baseline-start-year:2025}") int baselineStartYear,
            @Value("${offering.sec.baseline-max-datasets:8}") int baselineMaxDatasets) {
        this.client = client;
        this.recentLimit = Math.max(1, Math.min(recentLimit, 100));
        this.baselineStartYear = Math.max(2020, baselineStartYear);
        this.baselineMaxDatasets = Math.max(1, Math.min(baselineMaxDatasets, 8));
    }

    @Override
    public List<Candidate> fetchRecent() {
        LocalDate now = LocalDate.now();
        int quarter = ((now.getMonthValue() - 1) / 3) + 1;
        String indexUrl = "https://www.sec.gov/Archives/edgar/full-index/" + now.getYear()
                + "/QTR" + quarter + "/master.idx";
        String index = client.getText(indexUrl, 20_000_000);
        return parseIndex(index).stream().limit(recentLimit).map(record -> new Candidate(record.issuerName(),
                padCik(record.cik()), null, "UNKNOWN", null, null, null,
                filingIndexUrl(record.cik(), record.accession()), record.accession(), null, record.form(),
                record.filingDate(), null, null, null, null, null, null, null,
                "SEC_EDGAR_RECENT_INDEX", Map.of("submissionPath", record.path()))).toList();
    }

    @Override
    public Candidate enrich(Candidate candidate) {
        if (!"SEC_EDGAR_RECENT_INDEX".equals(candidate.source())) return candidate;
        String path = candidate.facts().get("submissionPath");
        if (blank(path)) return candidate;
        IndexRecord record = new IndexRecord(candidate.issuerCik(), candidate.issuerName(), candidate.filingType(),
                candidate.filingDate(), path, candidate.accessionNumber());
        return parseSubmission(record, client.getText(submissionTextUrl(path), 8_000_000));
    }

    @Override
    public List<Candidate> fetchBaseline() {
        String page = client.getText(DATASETS_PAGE, 2_000_000);
        List<String> urls = datasetUrls(page, baselineStartYear).stream().limit(baselineMaxDatasets).toList();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (String url : urls) {
            for (Candidate candidate : parseDatasetZip(client.get(url, 12_000_000))) {
                candidates.put(candidate.accessionNumber(), candidate);
            }
        }
        return List.copyOf(candidates.values());
    }

    static List<IndexRecord> parseIndex(String text) {
        List<IndexRecord> records = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher match = INDEX_LINE.matcher(line.trim());
            if (!match.matches()) continue;
            Matcher accession = ACCESSION.matcher(match.group(5));
            if (!accession.find()) continue;
            records.add(new IndexRecord(match.group(1), match.group(2).trim(), match.group(3),
                    LocalDate.parse(match.group(4)), match.group(5), accession.group(1)));
        }
        records.sort(java.util.Comparator.comparing(IndexRecord::filingDate).reversed());
        return records;
    }

    static Candidate parseSubmission(IndexRecord record, String submission) {
        Map<String, String> facts = new TreeMap<>();
        Map<String, String> xml = xmlFacts(submission);
        put(facts, "fileNumber", header(submission, "SEC FILE NUMBER"));
        put(facts, "issuerWebsite", tag(xml, "issuerWebsite", "website"));
        put(facts, "intermediaryName", tag(xml, "companyName", "intermediaryName"));
        put(facts, "intermediaryCik", tag(xml, "commissionCik", "intermediaryCik"));
        put(facts, "securityType", tag(xml, "securityOfferedType"));
        put(facts, "targetAmount", tag(xml, "offeringAmount"));
        put(facts, "maximumAmount", tag(xml, "maximumOfferingAmount"));
        put(facts, "deadline", tag(xml, "deadlineDate"));
        put(facts, "amountRaised", tag(xml, "totalOfferingAmount"));
        put(facts, "minimumInvestment", tag(xml, "minimumInvestment"));
        String filingUrl = filingIndexUrl(record.cik(), record.accession());
        String intermediary = facts.get("intermediaryName");
        return new Candidate(value(tag(xml, "nameOfIssuer"), record.issuerName()), padCik(record.cik()),
                facts.get("issuerWebsite"), PlatformNormalizer.normalize(intermediary), intermediary,
                facts.get("intermediaryCik"), publicOfferingUrl(facts), filingUrl, record.accession(),
                facts.get("fileNumber"), record.form(), record.filingDate(), facts.get("securityType"),
                decimal(facts.get("minimumInvestment")), decimal(facts.get("targetAmount")),
                decimal(facts.get("maximumAmount")), tag(xml, "valuation", "valuationCap"),
                date(facts.get("deadline")), decimal(facts.get("amountRaised")), "SEC_EDGAR_RECENT", Map.copyOf(facts));
    }

    static List<Candidate> parseDatasetZip(byte[] zipBytes) {
        Map<String, List<Map<String, String>>> tables = readZipTables(zipBytes);
        Map<String, Map<String, String>> submissions = byAccession(tables.get("FORM_C_SUBMISSION.TSV"));
        Map<String, Map<String, String>> issuers = byAccession(tables.get("FORM_C_ISSUER_INFORMATION.TSV"));
        Map<String, Map<String, String>> disclosures = byAccession(tables.get("FORM_C_DISCLOSURE.TSV"));
        List<Candidate> candidates = new ArrayList<>();
        submissions.forEach((accession, submission) -> {
            String form = submission.get("SUBMISSION_TYPE");
            if (!isOfferingForm(form)) return;
            Map<String, String> issuer = issuers.getOrDefault(accession, Map.of());
            Map<String, String> disclosure = disclosures.getOrDefault(accession, Map.of());
            String cik = value(submission.get("CIK"), issuer.get("COMMISSIONCIK"));
            String issuerName = issuer.get("NAMEOFISSUER");
            if (blank(cik) || blank(issuerName)) return;
            String intermediary = issuer.get("COMPANYNAME");
            Map<String, String> facts = new TreeMap<>();
            facts.putAll(nonBlank(submission)); facts.putAll(nonBlank(issuer)); facts.putAll(nonBlank(disclosure));
            candidates.add(new Candidate(issuerName, padCik(cik), issuer.get("ISSUERWEBSITE"),
                    PlatformNormalizer.normalize(intermediary), intermediary, issuer.get("COMMISSIONCIK"),
                    publicOfferingUrl(facts), filingIndexUrl(cik, accession), accession,
                    value(submission.get("FILE_NUMBER"), issuer.get("COMMISSIONFILENUMBER")), form,
                    compactDate(submission.get("FILING_DATE")), disclosure.get("SECURITYOFFEREDTYPE"), null,
                    decimal(disclosure.get("OFFERINGAMOUNT")), decimal(disclosure.get("MAXIMUMOFFERINGAMOUNT")),
                    null, compactDate(disclosure.get("DEADLINEDATE")),
                    decimal(disclosure.get("TOTALOFFERINGAMOUNT")), "SEC_CF_DATASET", Map.copyOf(facts)));
        });
        return candidates;
    }

    static List<String> datasetUrls(String html, int startYear) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = DATASET_LINK.matcher(html);
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(2)) < startYear) continue;
            URI resolved = URI.create(DATASETS_PAGE).resolve(matcher.group(1));
            if ("https".equals(resolved.getScheme()) && "www.sec.gov".equals(resolved.getHost())) urls.add(resolved.toString());
        }
        return urls;
    }

    private static Map<String, List<Map<String, String>>> readZipTables(byte[] bytes) {
        Map<String, List<Map<String, String>>> tables = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                name = name.substring(name.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);
                if (!name.endsWith(".TSV")) continue;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                tables.put(name, parseTsv(output.toString(StandardCharsets.UTF_8)));
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid SEC crowdfunding ZIP", error);
        }
        return tables;
    }

    private static List<Map<String, String>> parseTsv(String text) {
        String[] lines = text.split("\\R");
        if (lines.length == 0) return List.of();
        String[] headers = lines[0].replace("\uFEFF", "").split("\\t", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split("\\t", -1);
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < headers.length && j < values.length; j++) row.put(headers[j], values[j].trim());
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Map<String, String>> byAccession(List<Map<String, String>> rows) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (rows != null) for (Map<String, String> row : rows) if (!blank(row.get("ACCESSION_NUMBER"))) result.put(row.get("ACCESSION_NUMBER"), row);
        return result;
    }

    private static String tag(Map<String, String> facts, String... names) {
        for (String name : names) {
            String value = facts.get(name.toLowerCase(Locale.ROOT));
            if (!blank(value)) return value;
        }
        return null;
    }

    private static Map<String, String> xmlFacts(String submission) {
        StringBuilder fragments = new StringBuilder();
        Matcher blocks = Pattern.compile("(?is)<XML>(.*?)</XML>").matcher(submission);
        while (blocks.find()) fragments.append(blocks.group(1)).append('\n');
        String xml = fragments.isEmpty() ? submission : fragments.toString();
        xml = xml.replaceAll("(?is)<\\?xml[^>]*\\?>", "");
        if (xml.length() > 8_000_000) return Map.of();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            org.w3c.dom.Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader("<secSubmission>" + xml + "</secSubmission>")));
            Map<String, String> facts = new HashMap<>();
            org.w3c.dom.NodeList elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
                boolean hasElementChild = false;
                for (org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) { hasElementChild = true; break; }
                }
                String name = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
                String value = element.getTextContent() == null ? "" : element.getTextContent().trim();
                if (!hasElementChild && !value.isBlank()) facts.putIfAbsent(name.toLowerCase(Locale.ROOT), value);
            }
            return facts;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String header(String text, String name) {
        Matcher matcher = Pattern.compile("(?im)^\\s*" + Pattern.quote(name) + ":\\s*([^\\r\\n]+)").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String submissionTextUrl(String path) {
        String normalized = path.startsWith("edgar/") ? path.substring(6) : path;
        return "https://www.sec.gov/Archives/" + normalized;
    }

    static String filingIndexUrl(String cik, String accession) {
        return "https://www.sec.gov/Archives/edgar/data/" + cik.replaceFirst("^0+(?!$)", "") + "/"
                + accession.replace("-", "") + "/" + accession + "-index.html";
    }

    private static String publicOfferingUrl(Map<String, String> facts) {
        for (String key : List.of("OFFERINGURL", "offeringUrl", "INTERMEDIARYWEBSITE")) {
            String value = facts.get(key);
            if (value != null) {
                try { return PublicSourceUrlPolicy.requirePublicHttpUrl(value).toString(); }
                catch (SourceFetchException ignored) { /* Invalid evidence URLs are not persisted. */ }
            }
        }
        return null;
    }

    private static boolean isOfferingForm(String value) {
        return List.of("C", "C/A", "C-U", "C-U/A", "C-W", "C-TR").contains(value);
    }
    private static Map<String, String> nonBlank(Map<String, String> row) { Map<String, String> result = new TreeMap<>(); row.forEach((k,v)-> { if (!blank(v)) result.put(k,v); }); return result; }
    private static void put(Map<String, String> facts, String key, String value) { if (!blank(value)) facts.put(key, value); }
    private static String value(String first, String second) { return blank(first) ? second : first; }
    private static String padCik(String cik) { return cik == null ? null : String.format("%010d", Long.parseLong(cik.trim())); }
    private static BigDecimal decimal(String value) { try { return blank(value) ? null : new BigDecimal(value.replace(",", "").replace("$", "")); } catch (NumberFormatException e) { return null; } }
    private static LocalDate date(String value) { try { return blank(value) ? null : LocalDate.parse(value); } catch (RuntimeException e) { return compactDate(value); } }
    private static LocalDate compactDate(String value) { try { return blank(value) ? null : LocalDate.parse(value, java.time.format.DateTimeFormatter.BASIC_ISO_DATE); } catch (RuntimeException e) { return null; } }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record IndexRecord(String cik, String issuerName, String form, LocalDate filingDate, String path, String accession) { }
}
