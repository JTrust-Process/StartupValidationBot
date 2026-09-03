package com.startupvalidationbot.diligence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.diligence.DiligenceDomain.FinancialPeriod;
import com.startupvalidationbot.offering.OfferingDomain.Offering;

@Component
public class SecFinancialExtractor {
    public List<FinancialPeriod> extract(Offering offering, Map<String, String> facts) {
        List<FinancialPeriod> periods = new ArrayList<>();
        FinancialPeriod recent = period(periodLabel(facts, "MOSTRECENT", "MOST_RECENT_FISCAL_YEAR"),
                offering, facts, "MOSTRECENT");
        FinancialPeriod prior = period(periodLabel(facts, "PRIOR", "PRIOR_FISCAL_YEAR"),
                offering, facts, "PRIOR");
        if (hasValues(recent)) periods.add(recent);
        if (hasValues(prior)) periods.add(prior);
        return List.copyOf(periods);
    }

    private static FinancialPeriod period(String period, Offering offering, Map<String, String> facts,
            String marker) {
        BigDecimal shortDebt = number(any(facts,
                "SHORTTERMDEBT" + marker + "FISCALYEAR", "CURRENTDEBT" + marker + "FISCALYEAR"));
        BigDecimal longDebt = number(any(facts, "LONGTERMDEBT" + marker + "FISCALYEAR"));
        return new FinancialPeriod(period,
                number(any(facts, "REVENUE" + marker + "FISCALYEAR", "REVENUES" + marker + "FISCALYEAR")),
                number(any(facts, "COSTGOODSSOLD" + marker + "FISCALYEAR", "COSTOFGOODSSOLD" + marker + "FISCALYEAR")),
                number(any(facts, "NETINCOME" + marker + "FISCALYEAR", "NETINCOMELOSS" + marker + "FISCALYEAR")),
                number(any(facts, "CASHEQUI" + marker + "FISCALYEAR", "CASHANDCASHEQUIVALENTS" + marker + "FISCALYEAR")),
                number(any(facts, "TOTALASSET" + marker + "FISCALYEAR", "TOTALASSETS" + marker + "FISCALYEAR")),
                number(any(facts, "TOTALLIABILITIES" + marker + "FISCALYEAR", "TOTALLIABILITY" + marker + "FISCALYEAR")),
                shortDebt, longDebt, number(any(facts, "TAXPAID" + marker + "FISCALYEAR", "TAXESPAID" + marker + "FISCALYEAR")),
                offering.accessionNumber(), offering.secFilingUrl());
    }

    private static String periodLabel(Map<String, String> facts, String marker, String fallback) {
        String value = any(facts, "FISCALYEAREND" + marker, marker + "FISCALYEAREND",
                "FISCALYEAR" + marker, marker + "FISCALYEAR");
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean hasValues(FinancialPeriod value) {
        return value.revenue() != null || value.costOfGoods() != null || value.netIncome() != null
                || value.cash() != null || value.assets() != null || value.liabilities() != null || value.shortTermDebt() != null
                || value.longTermDebt() != null || value.taxesPaid() != null;
    }

    private static String any(Map<String, String> facts, String... keys) {
        for (String key : keys) {
            String value = facts.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
    private static BigDecimal number(String value) {
        try { return value == null ? null : new BigDecimal(value.replace("$", "").replace(",", "").trim()); }
        catch (NumberFormatException error) { return null; }
    }
}
