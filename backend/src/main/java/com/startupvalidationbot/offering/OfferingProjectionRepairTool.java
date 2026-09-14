package com.startupvalidationbot.offering;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Offline dry-run only. Accepts a bounded read-only JSON snapshot on stdin; never connects to a database. */
public final class OfferingProjectionRepairTool {
    private OfferingProjectionRepairTool() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("This tool is dry-run only; supply a JSON snapshot on stdin");
        byte[] bytes = System.in.readNBytes(5_000_001);
        if (bytes.length > 5_000_000) throw new IllegalArgumentException("Repair snapshot exceeds 5 MB");
        ObjectMapper json = new ObjectMapper();
        var snapshot = json.readValue(bytes, OfferingProjectionRepair.Snapshot.class);
        System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(OfferingProjectionRepair.plan(snapshot)));
    }
}
