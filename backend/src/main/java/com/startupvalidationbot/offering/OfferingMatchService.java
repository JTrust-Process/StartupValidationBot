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
        return match(candidate.issuerName(), candidate.issuerWebsite(), companies);
    }

    public Match match(String candidateIssuerName, String candidateIssuerWebsite, List<Company> companies) {
        String issuerName = CompanyIdentity.normalizeName(candidateIssuerName);
        String issuerDomain = CompanyIdentity.normalizeDomain(candidateIssuerWebsite);
        List<Company> exactNames = companies.stream().filter(company -> names(company).contains(issuerName)).toList();
        List<Company> domains = issuerDomain == null ? List.of()
                : companies.stream().filter(company -> issuerDomain.equals(company.domain())).toList();

        if (exactNames.size() == 1) {
            Company company = exactNames.get(0);
            if (issuerDomain != null && company.domain() != null && !issuerDomain.equals(company.domain())) {
                return new Match(company.id(), MatchStatus.REJECTED, 15,
                        "Exact normalized name conflicts with the issuer website domain.");
            }
            if (issuerDomain != null && issuerDomain.equals(company.domain())) {
                return new Match(company.id(), MatchStatus.CONFIRMED, 100,
                        "Exact legal/name identity and issuer website domain match.");
            }
            return new Match(company.id(), MatchStatus.LIKELY, 85,
                    "Unique exact normalized legal/name match, but issuer domain or equivalent corroborating "
                            + "identity evidence is unavailable. Manual verification required.");
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
