package ms.rohde.dmarcanalyzer.core.domain.report;

import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * A single SPF check evaluation from a record's {@code auth_results/spf}
 * element.
 */
@DomainValueObject
public record SpfAuthResult(String domain, DmarcAuthResultValue result) {
}
