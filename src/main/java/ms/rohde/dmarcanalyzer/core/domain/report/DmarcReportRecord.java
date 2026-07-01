package ms.rohde.dmarcanalyzer.core.domain.report;

import java.util.List;
import ms.rohde.hexagonalarch.annotations.DomainValueObject;
import org.jspecify.annotations.Nullable;

/**
 * A single {@code record} element of a DMARC aggregate report, describing
 * one sending source and the disposition/authentication outcome applied to
 * the messages it sent.
 */
@DomainValueObject
public record DmarcReportRecord(
        String sourceIp,
        int messageCount,
        PolicyEvaluated policyEvaluated,
        @Nullable String headerFrom,
        List<DkimAuthResult> dkimAuthResults,
        List<SpfAuthResult> spfAuthResults) {

    /**
     * Returns {@code true} if both DKIM and SPF were evaluated as passing for
     * this record's messages, meaning the message was fully DMARC-aligned.
     */
    public boolean isFullyAligned() {
        return policyEvaluated.dkimResult() == DmarcAuthResultValue.PASS
                && policyEvaluated.spfResult() == DmarcAuthResultValue.PASS;
    }
}
