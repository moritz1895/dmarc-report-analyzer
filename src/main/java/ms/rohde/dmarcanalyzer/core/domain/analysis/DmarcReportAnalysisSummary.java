package ms.rohde.dmarcanalyzer.core.domain.analysis;

import java.util.List;
import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * Aggregated, message-count-weighted statistics computed from a single
 * {@link ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport}.
 */
@DomainValueObject
public record DmarcReportAnalysisSummary(
        int totalMessageCount,
        int fullyAlignedMessageCount,
        int spfOnlyPassMessageCount,
        int dkimOnlyPassMessageCount,
        int fullyFailedMessageCount,
        int distinctSourceIpCount,
        List<FailingSource> topFailingSources) {
}
