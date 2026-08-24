package com.startupvalidationbot.offering;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.radar.CompanyIdentity;
import com.startupvalidationbot.radar.RadarDomain.Company;

@Service
public class OfferingMatchService {
    public Match match(Candidate candidate, List<Company> companies) {
        String issuerName = CompanyIdentity.normalizeName(candidate.issuerName());
        String issuerDomain = CompanyIdentity.normalizeDomain(candidate.issuerWebsite());
        List<Company> exactNames = companies.stream().filter(company -> names(company).contains(issuerName)).toList();
        List<Company> domains = issuerDomain == null ? List.of()
                : companies.stream().filter(company -> issuerDomain.equals(company.domain())).toList();

        if (exactNames.size() == 1) {
            Company company = exactNames.get(0);
            if (issuerDomain != null && company.domain() != null && !issuerDomain.equals(company.domain())) {
                return new Match(company.id(), MatchStatus.REJECTED, 15,
                        "Exact normalized name conflicts with the issuer website domain.");
            }
            int confidence = issuerDomain != null && issuerDomain.equals(company.domain()) ? 100 : 90;
            return new Match(company.id(), MatchStatus.CONFIRMED, confidence,
                    confidence == 100 ? "Exact legal/name alias and website domain match."
                            : "Unique exact normalized legal/name alias match.");
        }
        if (exactNames.size() > 1) {
            return new Match(null, MatchStatus.AMBIGUOUS, 45,
                    "Issuer name matches more than one tracked company.");
        }
        if (domains.size() == 1) {
            return new Match(domains.get(0).id(), MatchStatus.LIKELY, 75,
                    "Exact issuer website domain match without an exact legal-name match.");
        }

        List<Company> close = companies.stream().filter(company -> {
            String companyName = CompanyIdentity.normalizeName(company.name());
            return issuerName.length() >= 5 && (companyName.startsWith(issuerName) || issuerName.startsWith(companyName));
        }).toList();
        if (close.size() == 1) {
            return new Match(close.get(0).id(), MatchStatus.LIKELY, 60,
                    "Single close normalized-name candidate; manual review required.");
        }
        if (close.size() > 1) {
            return new Match(null, MatchStatus.AMBIGUOUS, 35,
                    "Close issuer name is ambiguous across tracked companies.");
        }
        return new Match(null, MatchStatus.UNMATCHED, 0, "No deterministic Radar identity match.");
    }

    private static List<String> names(Company company) {
        List<String> names = new ArrayList<>();
        names.add(CompanyIdentity.normalizeName(company.name()));
        company.aliases().stream().map(CompanyIdentity::normalizeName).forEach(names::add);
        return names;
    }
}
