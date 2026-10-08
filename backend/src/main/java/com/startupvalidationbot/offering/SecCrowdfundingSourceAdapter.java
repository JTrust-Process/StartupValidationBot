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
import com.startupvalidationbot.offering.OfferingDomain.RetrievalQuality;
import com.startupvalidationbot.radar.source.PublicSourceUrlPolicy;
import com.startupvalidationbot.radar.source.SourceFetchException;

@Component
public class SecCrowdfundingSourceAdapter implements OfferingSourceAdapter {
    static final String DATASETS_PAGE = "https://www.sec.gov/data-research/sec-markets-data/crowdfunding-offerings-data-sets";
    private static final Pattern DATASET_LINK = Pattern.compile("href=[\"']([^\"']*(20\\d{2})q[1-4]_cf\\.zip)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_LINE = Pattern.compile("^(\\d+)\\|([^|]+)\\|(C(?:/A|-U(?:/A)?|-W|-TR))\\|(\\d{4}-\\d{2}-\\d{2})\\|([^|]+)$");
    private static final Pattern ACCESSION = Pattern.compile("(\\d{10}-\\d{2}-\\d{6})");
    public static final int DOCUMENT_MAX_BYTES = 8_000_000;
    public static final int DOCUMENT_ATTEMPTS = 2;
    public enum ExtractionStatus { NO_FACT_PRESENT, DOCUMENT_TOO_LARGE, REQUEST_FAILED, PARSE_FAILED, UNSUPPORTED_STRUCTURE, SUCCESS }
    private final SecFilingClient client;
    private final int recentLimit;
    private final int indexMaxBytes;
    private final int baselineStartYear;
    private final int baselineMaxDatasets;

    public SecCrowdfundingSourceAdapter(SecFilingClient client,
            @Value("${offering.sec.recent-limit:40}") int recentLimit,
            @Value("${offering.sec.index-max-response-bytes:32000000}") int indexMaxBytes,
            @Value("${offering.sec.baseline-start-year:2025}") int baselineStartYear,
            @Value("${offering.sec.baseline-max-datasets:8}") int baselineMaxDatasets) {
        this.client = client;
        this.recentLimit = Math.max(1, Math.min(recentLimit, 100));
        this.indexMaxBytes = boundedIndexBytes(indexMaxBytes);
        this.baselineStartYear = Math.max(2020, baselineStartYear);
        this.baselineMaxDatasets = Math.max(1, Math.min(baselineMaxDatasets, 8));
    }

    @Override
    public List<Candidate> fetchRecent() {
        LocalDate now = LocalDate.now();
        int quarter = ((now.getMonthValue() - 1) / 3) + 1;
        String indexUrl = "https://www.sec.gov/Archives/edgar/full-index/" + now.getYear()
                + "/QTR" + quarter + "/master.idx";
        String index = client.getText(indexUrl, indexMaxBytes);
        return parseIndex(index).stream().limit(recentLimit).map(record -> new Candidate(record.issuerName(),
                padCik(record.cik()), null, "UNKNOWN", null, null, null,
                filingIndexUrl(record.cik(), record.accession()), record.accession(), null, record.form(),
                record.filingDate(), null, null, null, null, null, null, null,
                "SEC_EDGAR_RECENT_INDEX", Map.of("submissionPath", record.path()), RetrievalQuality.INDEX_ONLY)).toList();
    }

    @Override
    public Candidate enrich(Candidate candidate) {
        IndexRecord record = new IndexRecord(candidate.issuerCik(), candidate.issuerName(), candidate.filingType(),
                candidate.filingDate(), candidate.facts().get("submissionPath"), candidate.accessionNumber());
        String indexUrl = candidate.secFilingUrl();
        Map<String, String> diagnostics = new TreeMap<>();
        int attempts = 0, skipped = 0, requestFailures = 0, parseFailures = 0;
        ExtractionStatus status = ExtractionStatus.UNSUPPORTED_STRUCTURE;
        List<FilingDocument> documents;
        Candidate successful = null;
        try {
            documents = filingDocuments(indexUrl, client.getText(indexUrl, 1_000_000));
        } catch (RuntimeException error) {
            diagnostics.put("_secRequestFailures", "1");
            return extractionResult(candidate, candidate, diagnostics,
                    error instanceof SecFilingClient.DocumentTooLarge ? ExtractionStatus.DOCUMENT_TOO_LARGE : ExtractionStatus.REQUEST_FAILED);
        }
        for (FilingDocument document : documents) {
            if (document.size() > DOCUMENT_MAX_BYTES) { skipped++; status = ExtractionStatus.DOCUMENT_TOO_LARGE; continue; }
            if (attempts == DOCUMENT_ATTEMPTS) break;
            attempts++;
            if (successful != null && document.rank() < 2) { attempts--; continue; }
            if (successful == null) diagnostics.put("_secDocumentUrl", document.url());
            try {
                String body = client.getText(document.url(), DOCUMENT_MAX_BYTES);
                if (successful != null) {
                    Map<String, String> combined = new TreeMap<>(successful.facts());
                    SecOfferingTermExtractor.extract(combined, body);
                    stampTermSources(combined, successful.facts(), document.url());
                    stampCampaign(combined, body, candidate.accessionNumber(), document.url());
                    for (String key : List.of("minimumInvestment", "valuationOrCap", "amountRaised", "securityType")) {
                        if (!java.util.Objects.equals(combined.get("_secTerm.excerpt." + key), successful.facts().get("_secTerm.excerpt." + key))) combined.put("_sourceUrl." + key, document.url());
                    }
                    diagnostics.put("_secTermsDocumentUrl", document.url());
                    successful = withFacts(successful, combined);
                    continue;
                }
                Candidate parsed = parseSubmission(record, body);
                status = ExtractionStatus.valueOf(parsed.facts().getOrDefault("_secStatus", "UNSUPPORTED_STRUCTURE"));
                if (status == ExtractionStatus.SUCCESS || status == ExtractionStatus.NO_FACT_PRESENT) {
                    Map<String, String> sourced = new TreeMap<>(parsed.facts());
                    stampTermSources(sourced, Map.of(), document.url());
                    for (String key : List.copyOf(sourced.keySet())) if (!key.startsWith("_")) sourced.put("_sourceUrl." + key, document.url());
                    stampCampaign(sourced, body, candidate.accessionNumber(), document.url());
                    successful = withFacts(parsed, sourced);
                    continue;
                }
                if (status == ExtractionStatus.PARSE_FAILED) parseFailures++;
            } catch (SecFilingClient.DocumentTooLarge error) { skipped++; status = ExtractionStatus.DOCUMENT_TOO_LARGE; }
            catch (RuntimeException error) { requestFailures++; status = ExtractionStatus.REQUEST_FAILED; }
        }
        diagnostics.put("_secDocumentsAttempted", Integer.toString(attempts));
        diagnostics.put("_secPrimaryDocumentsAttempted", attempts == 0 ? "0" : "1");
        diagnostics.put("_secAlternateDocumentsAttempted", Integer.toString(Math.max(0, attempts - 1)));
        diagnostics.put("_secOversizedSkipped", Integer.toString(skipped));
        diagnostics.put("_secRequestFailures", Integer.toString(requestFailures));
        diagnostics.put("_secParseFailures", Integer.toString(parseFailures));
        return extractionResult(candidate, successful == null ? candidate : successful, diagnostics,
                successful == null ? status : ExtractionStatus.SUCCESS);
    }

    private static Candidate extractionResult(Candidate original, Candidate parsed, Map<String, String> diagnostics,
            ExtractionStatus status) {
        Map<String, String> facts = new TreeMap<>(parsed.facts());
        facts.putAll(diagnostics); facts.put("_secStatus", status.name());
        if (original.facts().get("submissionPath") != null) facts.put("submissionPath", original.facts().get("submissionPath"));
        String document = diagnostics.get("_secDocumentUrl");
        if (status == ExtractionStatus.SUCCESS && document != null) {
            for (String key : List.copyOf(facts.keySet())) if (!key.startsWith("_")) facts.putIfAbsent("_sourceUrl." + key, document);
        }
        return new Candidate(parsed.issuerName(), parsed.issuerCik(), parsed.issuerWebsite(), parsed.platform(),
                parsed.intermediaryName(), parsed.intermediaryCik(), parsed.offeringUrl(), parsed.secFilingUrl(),
                parsed.accessionNumber(), parsed.fileNumber(), parsed.filingType(), parsed.filingDate(), parsed.securityType(),
                parsed.minimumInvestment(), parsed.targetAmount(), parsed.maximumAmount(), parsed.valuationOrCap(),
                parsed.deadline(), parsed.amountRaised(), parsed.source(), Map.copyOf(facts),
                status == ExtractionStatus.SUCCESS || status == ExtractionStatus.NO_FACT_PRESENT
                        ? parsed.retrievalQuality() : RetrievalQuality.INDEX_ONLY);
    }

    static List<FilingDocument> filingDocuments(String indexUrl, String html) {
        URI index = SecFilingClient.requireOfficialUrl(indexUrl);
        List<FilingDocument> documents = new ArrayList<>();
        Matcher rows = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>").matcher(html);
        while (rows.find()) {
            List<String> cells = new ArrayList<>();
            Matcher columns = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>").matcher(rows.group(1));
            while (columns.find()) cells.add(columns.group(1));
            if (cells.size() < 5) continue;
            Matcher link = Pattern.compile("(?is)href=[\"']([^\"']+)[\"']").matcher(cells.get(2));
            if (!link.find()) continue;
            String type = plain(cells.get(3)).toUpperCase(Locale.ROOT);
            String description = plain(cells.get(1)).toLowerCase(Locale.ROOT);
            boolean termsExhibit = type.startsWith("EX-") && description.matches(".*(offering statement|security instrument|safe agreement|subscription agreement|convertible note|offering terms).*" );
            if (!isOfferingForm(type) && !termsExhibit && !(type.equals("XML") && description.matches(".*(form c|submission|offering|financial).*"))) continue;
            try {
                URI url = SecFilingClient.requireOfficialUrl(index.resolve(link.group(1).replace("&amp;", "&")).toString());
                // A filing cannot nominate unrelated official-host paths or a different accession.
                String directory = index.getPath().substring(0, index.getPath().lastIndexOf('/') + 1);
                if (!url.getPath().startsWith(directory)) continue;
                boolean xml = url.getPath().toLowerCase(Locale.ROOT).endsWith(".xml");
                boolean htmlDocument = url.getPath().toLowerCase(Locale.ROOT).matches(".*\\.html?");
                if (!xml && !htmlDocument) continue;
                String sizeText = plain(cells.get(4)).replace(",", "");
                long size = sizeText.matches("\\d+") ? Long.parseLong(sizeText) : -1;
                documents.add(new FilingDocument(url.toString(), size, termsExhibit ? 3 : xml ? isOfferingForm(type) ? 0 : 1 : 2));
            } catch (IllegalArgumentException ignored) { /* Unsafe index entries are not fetched. */ }
        }
        return documents.stream().distinct().sorted(java.util.Comparator.comparingInt(FilingDocument::rank)
                .thenComparingLong(d -> d.size() < 0 ? Long.MAX_VALUE : d.size()).thenComparing(FilingDocument::url)).toList();
    }

    private static String plain(String value) { return value.replaceAll("(?s)<[^>]*>", "").replace("&nbsp;", " ").trim(); }
    record FilingDocument(String url, long size, int rank) { }

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

    static int boundedIndexBytes(int configured) {
        return Math.max(1_000_000, Math.min(configured, 50_000_000));
    }

    static Candidate parseSubmission(IndexRecord record, String submission) {
        Map<String, String> facts = new TreeMap<>();
        Map<String, String> xml = xmlFacts(submission);
        xml.forEach((key, value) -> {
            if (!key.startsWith("_")) facts.put(key.toUpperCase(Locale.ROOT), value);
            else facts.put(key, value);
        });
        put(facts, "fileNumber", header(submission, "SEC FILE NUMBER"));
        put(facts, "issuerWebsite", tag(xml, "issuerWebsite", "website"));
        put(facts, "intermediaryName", tag(xml, "companyName", "intermediaryName"));
        put(facts, "intermediaryCik", tag(xml, "commissionCik", "intermediaryCik"));
        put(facts, "securityType", tag(xml, "securityOfferedType"));
        put(facts, "targetAmount", tag(xml, "offeringAmount"));
        put(facts, "maximumAmount", tag(xml, "maximumOfferingAmount"));
        put(facts, "deadline", tag(xml, "deadlineDate"));
        put(facts, "minimumInvestment", tag(xml, "minimumInvestment"));
        String cap = tag(xml, "valuationCap");
        put(facts, "valuationOrCap", cap == null ? tag(xml, "valuation") : cap + " cap");
        put(facts, "offeringUrl", tag(xml, "offeringUrl", "campaignUrl"));
        put(facts, "intermediaryWebsite", tag(xml, "intermediaryWebsite"));
        facts.put("_secForm", record.form());
        SecOfferingTermExtractor.extract(facts, submission.matches("(?is).*<(?:html|table)\\b.*") ? submission : "");
        if (submission.matches("(?is).*<(?:html|table)\\b.*") && facts.keySet().stream().anyMatch(key -> key.startsWith("_secTerm.fact."))) facts.put("_secStatus", "SUCCESS");
        var identity = IntermediaryRegistry.stamp(facts, record.accession());
        stampCampaign(facts, submission, record.accession(), filingIndexUrl(record.cik(), record.accession()));
        String filingUrl = filingIndexUrl(record.cik(), record.accession());
        String intermediary = facts.get("intermediaryName");
        return new Candidate(value(tag(xml, "nameOfIssuer"), record.issuerName()), padCik(record.cik()),
                facts.get("issuerWebsite"), identity.family() == null ? "UNKNOWN" : identity.family(), intermediary,
                facts.get("intermediaryCik"), publicOfferingUrl(facts), filingUrl, record.accession(),
                facts.get("fileNumber"), record.form(), record.filingDate(), facts.get("securityType"),
                decimal(facts.get("minimumInvestment")), decimal(facts.get("targetAmount")),
                decimal(facts.get("maximumAmount")), facts.get("valuationOrCap"),
                date(facts.get("deadline")), decimal(facts.get("amountRaised")), "SEC_EDGAR_RECENT", Map.copyOf(facts),
                "SUCCESS".equals(facts.get("_secStatus")) || "NO_FACT_PRESENT".equals(facts.get("_secStatus"))
                        ? RetrievalQuality.DETAIL_COMPLETE : RetrievalQuality.INDEX_ONLY);
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
            facts.put("_secForm", form);
            SecOfferingTermExtractor.extract(facts, "");
            var identity = IntermediaryRegistry.stamp(facts, accession);
            stampCampaign(facts, String.join("\n", facts.values()), accession, filingIndexUrl(cik, accession));
            candidates.add(new Candidate(issuerName, padCik(cik), issuer.get("ISSUERWEBSITE"),
                    identity.family() == null ? "UNKNOWN" : identity.family(), intermediary, issuer.get("COMMISSIONCIK"),
                    publicOfferingUrl(facts), filingIndexUrl(cik, accession), accession,
                    value(submission.get("FILE_NUMBER"), issuer.get("COMMISSIONFILENUMBER")), form,
                    compactDate(submission.get("FILING_DATE")), facts.getOrDefault("securityType", disclosure.get("SECURITYOFFEREDTYPE")), decimal(facts.get("minimumInvestment")),
                    decimal(disclosure.get("OFFERINGAMOUNT")), decimal(disclosure.get("MAXIMUMOFFERINGAMOUNT")),
                    facts.get("valuationOrCap"), compactDate(disclosure.get("DEADLINEDATE")),
                    decimal(facts.get("amountRaised")), "SEC_CF_DATASET", Map.copyOf(facts),
                    disclosure.isEmpty() ? RetrievalQuality.PARTIAL_DETAIL : RetrievalQuality.DETAIL_COMPLETE));
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
        if (xml.length() > DOCUMENT_MAX_BYTES) return Map.of("_secStatus", "DOCUMENT_TOO_LARGE");
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
                if (!hasElementChild && !value.isBlank()) {
                    String key = name.toLowerCase(Locale.ROOT);
                    facts.putIfAbsent(key, value);
                    facts.putIfAbsent("_secLabel." + name.toUpperCase(Locale.ROOT), name);
                }
            }
            boolean supported = facts.containsKey("nameofissuer") || facts.containsKey("securityofferedtype")
                    || facts.keySet().stream().anyMatch(key -> key.endsWith("fiscalyear"));
            facts.put("_secStatus", supported ? "SUCCESS" : "UNSUPPORTED_STRUCTURE");
            return facts;
        } catch (Exception ignored) {
            return Map.of("_secStatus", "PARSE_FAILED");
        }
    }

    private static String header(String text, String name) {
        Matcher matcher = Pattern.compile("(?im)^\\s*" + Pattern.quote(name) + ":\\s*([^\\r\\n]+)").matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    static String submissionTextUrl(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        Matcher flatSubmission = Pattern.compile(
                "^edgar/data/(\\d+)/(\\d{10}-\\d{2}-\\d{6})\\.txt$",
                Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (flatSubmission.matches()) {
            String accession = flatSubmission.group(2);
            normalized = "edgar/data/" + flatSubmission.group(1) + "/"
                    + accession.replace("-", "") + "/" + accession + ".txt";
        }
        return "https://www.sec.gov/Archives/" + normalized;
    }

    static String filingIndexUrl(String cik, String accession) {
        return "https://www.sec.gov/Archives/edgar/data/" + cik.replaceFirst("^0+(?!$)", "") + "/"
                + accession.replace("-", "") + "/" + accession + "-index.html";
    }

    private static String publicOfferingUrl(Map<String, String> facts) {
        if ("CONFIRMED".equals(facts.get("_secCampaign.state"))) return facts.get("_secCampaign.url");
        for (String key : List.of("OFFERINGURL", "offeringUrl")) {
            String value = facts.get(key);
            if (value != null) {
                try { return PublicSourceUrlPolicy.requirePublicHttpUrl(value).toString(); }
                catch (SourceFetchException ignored) { /* Invalid evidence URLs are not persisted. */ }
            }
        }
        return null;
    }

    private static void stampCampaign(Map<String, String> facts, String text, String accession, String sourceUrl) {
        java.util.Set<String> urls = ExplicitCampaignLinks.find(text);
        String explicit = IntermediaryRegistry.value(facts, "offeringUrl", "CAMPAIGNURL");
        if (explicit != null && ExplicitCampaignLinks.canonical(explicit) != null) urls.add(ExplicitCampaignLinks.canonical(explicit));
        if (urls.isEmpty()) return;
        facts.put("_secCampaign.state", urls.size() == 1 ? "CONFIRMED" : "AMBIGUOUS");
        facts.put("_secCampaign.reason", urls.size() == 1 ? "Exact campaign URL explicitly present in issuer SEC filing" : "Multiple explicit campaign URLs require issuer review");
        facts.put("_secCampaign.candidates", String.join("\n", urls));
        facts.put("_secCampaign.sourceUrl", sourceUrl);
        facts.put("_secCampaign.accession", accession);
        facts.put("_secCampaign.observedAt", java.time.LocalDateTime.now().toString());
        if (urls.size() == 1) facts.put("_secCampaign.url", urls.iterator().next());
    }

    private static Candidate withFacts(Candidate c, Map<String, String> facts) {
        return new Candidate(c.issuerName(), c.issuerCik(), c.issuerWebsite(), c.platform(), c.intermediaryName(), c.intermediaryCik(),
                publicOfferingUrl(facts), c.secFilingUrl(), c.accessionNumber(), c.fileNumber(), c.filingType(), c.filingDate(),
                facts.get("securityType"), decimal(facts.get("minimumInvestment")), c.targetAmount(), c.maximumAmount(),
                facts.get("valuationOrCap"), c.deadline(), decimal(facts.get("amountRaised")), c.source(), Map.copyOf(facts), c.retrievalQuality());
    }

    private static void stampTermSources(Map<String, String> facts, Map<String, String> previous, String documentUrl) {
        for (String key : List.copyOf(facts.keySet())) {
            if (key.startsWith("_secTerm.fact.") && key.endsWith(".excerpt") && !java.util.Objects.equals(facts.get(key), previous.get(key))) {
                facts.put(key.substring(0, key.length() - 8) + ".sourceUrl", documentUrl);
            }
        }
    }

    private static boolean isOfferingForm(String value) {
        return List.of("C", "C/A", "C-U", "C-U/A", "C-W", "C-TR").contains(value);
    }
    private static Map<String, String> nonBlank(Map<String, String> row) { Map<String, String> result = new TreeMap<>(); row.forEach((k,v)-> { if (!blank(v)) result.put(k,v); }); return result; }
    private static void put(Map<String, String> facts, String key, String value) { if (!blank(value)) facts.put(key, value); }
    private static String value(String first, String second) { return blank(first) ? second : first; }
    private static String padCik(String cik) { return cik == null ? null : String.format("%010d", Long.parseLong(cik.trim())); }
    private static BigDecimal decimal(String value) { return OfferingTermNormalizer.money(value); }
    private static LocalDate date(String value) { return OfferingTermNormalizer.date(value); }
    private static LocalDate compactDate(String value) { return OfferingTermNormalizer.date(value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    record IndexRecord(String cik, String issuerName, String form, LocalDate filingDate, String path, String accession) { }
}
