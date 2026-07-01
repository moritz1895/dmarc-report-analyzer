package ms.rohde.dmarcanalyzer.core.domain.report;

import java.time.Instant;
import ms.rohde.hexagonalarch.annotations.DomainValueObject;

/**
 * Identifying and time-range metadata of a DMARC aggregate report as found
 * in its {@code report_metadata} element.
 */
@DomainValueObject
public record ReportMetadata(
        String orgName,
        String email,
        String reportId,
        Instant dateRangeBegin,
        Instant dateRangeEnd) {
}
