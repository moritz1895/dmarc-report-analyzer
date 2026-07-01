package ms.rohde.dmarcanalyzer.core.domain.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAuthResultValue;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcReportRecord;
import ms.rohde.hexagonalarch.annotations.DomainService;

/**
 * Computes message-count-weighted authentication statistics from a parsed
 * {@link DmarcAggregateReport}.
 */
@DomainService
public class DmarcReportStatisticsCalculator {

    private static final int TOP_FAILING_SOURCES_LIMIT = 10;

    public DmarcReportAnalysisSummary calculate(DmarcAggregateReport report) {
        List<DmarcReportRecord> records = report.records();

        int totalMessageCount = 0;
        int fullyAlignedMessageCount = 0;
        int spfOnlyPassMessageCount = 0;
        int dkimOnlyPassMessageCount = 0;
        int fullyFailedMessageCount = 0;

        for (DmarcReportRecord record : records) {
            totalMessageCount += record.messageCount();
            DmarcAuthResultValue dkim = record.policyEvaluated().dkimResult();
            DmarcAuthResultValue spf = record.policyEvaluated().spfResult();

            if (dkim == DmarcAuthResultValue.PASS && spf == DmarcAuthResultValue.PASS) {
                fullyAlignedMessageCount += record.messageCount();
            } else if (spf == DmarcAuthResultValue.PASS) {
                spfOnlyPassMessageCount += record.messageCount();
            } else if (dkim == DmarcAuthResultValue.PASS) {
                dkimOnlyPassMessageCount += record.messageCount();
            } else {
                fullyFailedMessageCount += record.messageCount();
            }
        }

        Set<String> distinctSourceIps = records.stream()
                .map(DmarcReportRecord::sourceIp)
                .collect(Collectors.toSet());

        List<FailingSource> topFailingSources = records.stream()
                .filter(record -> !record.isFullyAligned())
                .map(record -> new FailingSource(
                        record.sourceIp(),
                        record.messageCount(),
                        record.policyEvaluated().dkimResult(),
                        record.policyEvaluated().spfResult()))
                .sorted(Comparator.comparingInt(FailingSource::messageCount).reversed())
                .limit(TOP_FAILING_SOURCES_LIMIT)
                .toList();

        return new DmarcReportAnalysisSummary(
                totalMessageCount,
                fullyAlignedMessageCount,
                spfOnlyPassMessageCount,
                dkimOnlyPassMessageCount,
                fullyFailedMessageCount,
                distinctSourceIps.size(),
                topFailingSources);
    }
}
