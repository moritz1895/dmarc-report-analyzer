package ms.rohde.dmarcanalyzer.core.domain.analysis;

import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAuthResultValue;
import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * A sending source whose messages were not fully DMARC-aligned, ranked by
 * how many messages it sent.
 */
@DomainValueObject
public record FailingSource(
        String sourceIp,
        int messageCount,
        DmarcAuthResultValue dkimResult,
        DmarcAuthResultValue spfResult) {
}
