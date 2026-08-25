package com.startupvalidationbot.offering;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class PlatformNormalizer {
    private static final Map<String, String> MARKERS = new LinkedHashMap<>();

    static {
        MARKERS.put("wefunder", "Wefunder");
        MARKERS.put("republic", "Republic");
        MARKERS.put("open deal portal", "Republic");
        MARKERS.put("opendeal portal", "Republic");
        MARKERS.put("startengine", "StartEngine");
        MARKERS.put("dealmaker securities", "DealMaker");
        MARKERS.put("honeycomb portal", "Honeycomb Credit");
        MARKERS.put("netcapital", "Netcapital");
        MARKERS.put("microventure", "MicroVentures");
        MARKERS.put("first democracy", "MicroVentures");
        MARKERS.put("equifund", "Equifund");
        MARKERS.put("picmii", "PicMii");
    }

    private PlatformNormalizer() {
    }

    public static String normalize(String intermediary) {
        if (intermediary == null || intermediary.isBlank()) return "UNKNOWN";
        String lower = intermediary.toLowerCase(Locale.ROOT);
        return MARKERS.entrySet().stream().filter(entry -> lower.contains(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(intermediary.trim());
    }
}
