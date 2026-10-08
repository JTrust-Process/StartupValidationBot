package com.startupvalidationbot.offering;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Exact identifiers observed in SEC filings; a family match does not establish a campaign. */
public final class IntermediaryRegistry {
    public enum State { SEC_CONFIRMED, EXPLICIT_DOMAIN_CONFIRMED, LIKELY, AMBIGUOUS, UNKNOWN }
    public record Entry(String family, Set<String> ciks, Set<String> crds, Set<String> files,
            Set<String> legalNames, Set<String> domains) { }
    public record Identity(String family, State state, String reason) { }
    private static final List<Entry> ENTRIES = List.of(
        entry("Wefunder", "1670254", "283503", "007-00033", "Wefunder Portal LLC", "wefunder.com"),
        entry("Republic", "1751525", "283874", "007-00167", "OpenDeal Portal LLC", "republic.com"),
        entry("StartEngine", "1725012,1665160", "", "008-70060,007-00007", "StartEngine Primary LLC|StartEngine Capital LLC", "startengine.com"),
        entry("DealMaker", "1872856", "315324", "008-70756", "DealMaker Securities LLC", "dealmaker.tech"),
        entry("Fundify", "1788777", "", "", "Fundify Portal LLC", "fundify.com"),
        entry("Honeycomb Credit", "1705726", "289015", "007-00119", "Honeycomb Portal LLC", "honeycombcredit.com"),
        entry("Netcapital", "1669191", "283596", "007-00035", "NetCapital Funding Portal Inc", "netcapital.com"),
        entry("PicMii", "1817013", "310171", "007-00246", "PicMii Crowdfunding LLC", "picmiicrowdfunding.com"),
        entry("Silicon Prairie", "1640943", "226591", "008-69625", "Silicon Prairie Capital Partners LLC", ""),
        entry("Cultivate", "1768367", "300634", "008-70293", "Cultivate Capital Group LLC", ""),
        entry("ChainRaise", "1870874", "", "007-00314", "ChainRaise Portal LLC", "chainraise.io"),
        entry("Climatize", "1923174", "", "", "Climatize Earth Securities LLC", "climatize.earth"),
        entry("WeVidIt", "1883789", "", "", "WeVidIt Inc", "wevidit.com"),
        entry("Issuance Express", "1664804", "282912", "007-00008", "Jumpstart Micro Inc d/b/a Issuance Express|Jumpstart Micro Inc", ""),
        entry("GigaStar", "1935609", "", "", "GigaStar Portal LLC", "gigastar.io"),
        entry("Vicinity", "1798542", "307772", "007-00223", "Vicinity LLC", "vicinitycapital.com")
    );

    private IntermediaryRegistry() { }

    public static Identity resolve(String name, String cik, String crd, String file, String domainUrl) {
        Set<String> families = new LinkedHashSet<>();
        String reason = null;
        for (int priority = 0; priority < 4; priority++) {
            for (Entry entry : ENTRIES) {
                boolean match = switch (priority) {
                    case 0 -> entry.ciks().contains(identifier(cik));
                    case 1 -> entry.crds().contains(identifier(crd)) || entry.files().contains(fileNumber(file));
                    case 2 -> entry.legalNames().contains(legalName(name));
                    default -> entry.domains().contains(domain(domainUrl));
                };
                if (match) {
                    families.add(entry.family());
                    if (reason == null) reason = switch (priority) {
                        case 0 -> "Exact SEC intermediary CIK";
                        case 1 -> "Exact SEC intermediary CRD/commission file number";
                        case 2 -> "Exact normalized SEC legal intermediary name";
                        default -> "Explicit intermediary domain";
                    };
                }
            }
        }
        if (families.size() > 1) return new Identity(null, State.AMBIGUOUS, "Conflicting intermediary identifiers: " + families);
        if (families.isEmpty()) return new Identity(null, State.UNKNOWN, "No exact registered intermediary identifier");
        boolean domainOnly = "Explicit intermediary domain".equals(reason);
        return new Identity(families.iterator().next(), domainOnly ? State.EXPLICIT_DOMAIN_CONFIRMED : State.SEC_CONFIRMED, reason);
    }

    public static Identity fromFacts(Map<String, String> facts) {
        return resolve(value(facts, "intermediaryName", "COMPANYNAME"), value(facts, "intermediaryCik", "COMMISSIONCIK"),
                value(facts, "COMISSIONCRD", "COMMISSIONCRD"), value(facts, "COMMISSIONFILENUMBER"),
                value(facts, "intermediaryWebsite", "COISSUERWEBSITE"));
    }

    public static Identity stamp(Map<String, String> facts, String accession) {
        Identity identity = fromFacts(facts);
        facts.put("_secPlatform.state", identity.state().name());
        facts.put("_secPlatform.reason", identity.reason());
        if (identity.family() != null) facts.put("_secPlatform.family", identity.family());
        if (accession != null) facts.put("_secPlatform.accession", accession);
        facts.put("_secPlatform.observedAt", LocalDateTime.now().toString());
        return identity;
    }

    public static String familyForUrl(String url) {
        return resolve(null, null, null, null, url).family();
    }
    public static String value(Map<String, String> facts, String... names) {
        for (String name : names) for (var entry : facts.entrySet())
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null && !entry.getValue().isBlank()) return entry.getValue();
        return null;
    }
    private static Entry entry(String family, String ciks, String crds, String files, String names, String domains) {
        return new Entry(family, split(ciks), split(crds), split(files),
                Set.of(names.split("\\|")).stream().map(IntermediaryRegistry::legalName).collect(java.util.stream.Collectors.toSet()), split(domains));
    }
    private static Set<String> split(String text) { return text.isBlank() ? Set.of() : Set.of(text.split(",")); }
    private static String identifier(String value) { return value != null && value.trim().matches("[0-9]+") ? value.trim().replaceFirst("^0+(?!$)", "") : ""; }
    private static String fileNumber(String value) { return value == null ? "" : value.trim(); }
    private static String legalName(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[.,]", "").replaceAll("\\s+", " ").trim(); }
    private static String domain(String value) {
        try { URI uri = URI.create(value); return uri.getHost() == null || uri.getUserInfo() != null || !"https".equals(uri.getScheme()) ? "" : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""); }
        catch (RuntimeException ignored) { return ""; }
    }
}
