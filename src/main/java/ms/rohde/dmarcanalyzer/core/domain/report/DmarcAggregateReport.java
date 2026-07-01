package ms.rohde.dmarcanalyzer.core.domain.report;

import java.util.List;
import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * A fully parsed DMARC aggregate report ({@code feedback} root element),
 * comprising its metadata, the published policy, and the individual
 * per-source records it contains.
 */
@DomainValueObject
public record DmarcAggregateReport(
        ReportMetadata metadata,
        PolicyPublished policyPublished,
        List<DmarcReportRecord> records) {

    /**
     * Sums {@link DmarcReportRecord#messageCount()} across all records.
     */
    public int totalMessageCount() {
        return records.stream().mapToInt(DmarcReportRecord::messageCount).sum();
    }
}
