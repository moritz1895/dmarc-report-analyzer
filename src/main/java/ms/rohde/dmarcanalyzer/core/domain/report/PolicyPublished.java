package ms.rohde.dmarcanalyzer.core.domain.report;

import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * The DMARC policy the domain owner published in DNS, as echoed back in the
 * report's {@code policy_published} element.
 */
@DomainValueObject
public record PolicyPublished(
        String domain,
        boolean adkimStrict,
        boolean aspfStrict,
        Disposition domainPolicy,
        Disposition subdomainPolicy,
        int percentageCoverage) {
}
