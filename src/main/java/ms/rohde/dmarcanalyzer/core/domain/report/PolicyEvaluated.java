package ms.rohde.dmarcanalyzer.core.domain.report;

import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * The disposition and authentication outcome the receiving mail server
 * actually applied to a group of messages, as found in a record's
 * {@code row/policy_evaluated} element.
 */
@DomainValueObject
public record PolicyEvaluated(
        Disposition disposition,
        DmarcAuthResultValue dkimResult,
        DmarcAuthResultValue spfResult) {
}
