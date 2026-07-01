package ms.rohde.dmarcanalyzer.core.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ms.rohde.dmarcanalyzer.core.domain.report.DkimAuthResult;
import ms.rohde.dmarcanalyzer.core.domain.report.Disposition;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAuthResultValue;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcReportRecord;
import ms.rohde.dmarcanalyzer.core.domain.report.PolicyEvaluated;
import ms.rohde.dmarcanalyzer.core.domain.report.PolicyPublished;
import ms.rohde.dmarcanalyzer.core.domain.report.ReportMetadata;
import ms.rohde.dmarcanalyzer.core.domain.report.SpfAuthResult;
import org.junit.jupiter.api.Test;

class DmarcReportStatisticsCalculatorTest {

    private final DmarcReportStatisticsCalculator calculator = new DmarcReportStatisticsCalculator();

    private static final ReportMetadata METADATA = new ReportMetadata(
            "google.com", "noreply@google.com", "1", Instant.EPOCH, Instant.EPOCH.plusSeconds(86400));

    private static final PolicyPublished POLICY_PUBLISHED = new PolicyPublished(
            "rohde.ms", false, false, Disposition.QUARANTINE, Disposition.QUARANTINE, 100);

    private DmarcReportRecord record(String sourceIp, int messageCount, DmarcAuthResultValue dkim, DmarcAuthResultValue spf) {
        PolicyEvaluated policyEvaluated = new PolicyEvaluated(Disposition.NONE, dkim, spf);
        return new DmarcReportRecord(
                sourceIp,
                messageCount,
                policyEvaluated,
                "rohde.ms",
                List.of(new DkimAuthResult("rohde.ms", dkim)),
                List.of(new SpfAuthResult("rohde.ms", spf)));
    }

    @Test
    void calculate_givenMixOfRecords_thenCountsAreWeightedByMessageCount() {
        DmarcAggregateReport report = new DmarcAggregateReport(METADATA, POLICY_PUBLISHED, List.of(
                record("1.1.1.1", 10, DmarcAuthResultValue.PASS, DmarcAuthResultValue.PASS),
                record("2.2.2.2", 5, DmarcAuthResultValue.FAIL, DmarcAuthResultValue.PASS),
                record("3.3.3.3", 3, DmarcAuthResultValue.PASS, DmarcAuthResultValue.FAIL),
                record("4.4.4.4", 2, DmarcAuthResultValue.FAIL, DmarcAuthResultValue.FAIL)));

        DmarcReportAnalysisSummary summary = calculator.calculate(report);

        assertThat(summary.totalMessageCount()).isEqualTo(20);
        assertThat(summary.fullyAlignedMessageCount()).isEqualTo(10);
        assertThat(summary.spfOnlyPassMessageCount()).isEqualTo(5);
        assertThat(summary.dkimOnlyPassMessageCount()).isEqualTo(3);
        assertThat(summary.fullyFailedMessageCount()).isEqualTo(2);
        assertThat(summary.distinctSourceIpCount()).isEqualTo(4);
    }

    @Test
    void calculate_givenNonAlignedRecords_thenTopFailingSourcesSortedDescendingByMessageCount() {
        DmarcAggregateReport report = new DmarcAggregateReport(METADATA, POLICY_PUBLISHED, List.of(
                record("1.1.1.1", 10, DmarcAuthResultValue.PASS, DmarcAuthResultValue.PASS),
                record("2.2.2.2", 5, DmarcAuthResultValue.FAIL, DmarcAuthResultValue.PASS),
                record("3.3.3.3", 30, DmarcAuthResultValue.PASS, DmarcAuthResultValue.FAIL),
                record("4.4.4.4", 2, DmarcAuthResultValue.FAIL, DmarcAuthResultValue.FAIL)));

        DmarcReportAnalysisSummary summary = calculator.calculate(report);

        assertThat(summary.topFailingSources()).extracting(FailingSource::sourceIp)
                .containsExactly("3.3.3.3", "2.2.2.2", "4.4.4.4");
        assertThat(summary.topFailingSources()).extracting(FailingSource::messageCount)
                .containsExactly(30, 5, 2);
    }

    @Test
    void calculate_givenMoreThanTenFailingSources_thenTopFailingSourcesCappedAtTen() {
        List<DmarcReportRecord> records = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            records.add(record("10.0.0." + i, 100 - i, DmarcAuthResultValue.FAIL, DmarcAuthResultValue.FAIL));
        }
        DmarcAggregateReport report = new DmarcAggregateReport(METADATA, POLICY_PUBLISHED, records);

        DmarcReportAnalysisSummary summary = calculator.calculate(report);

        assertThat(summary.topFailingSources()).hasSize(10);
        assertThat(summary.topFailingSources().get(0).messageCount()).isEqualTo(100);
        assertThat(summary.topFailingSources().get(9).messageCount()).isEqualTo(91);
    }

    @Test
    void calculate_givenAllRecordsFullyAligned_thenTopFailingSourcesIsEmpty() {
        DmarcAggregateReport report = new DmarcAggregateReport(METADATA, POLICY_PUBLISHED, List.of(
                record("1.1.1.1", 10, DmarcAuthResultValue.PASS, DmarcAuthResultValue.PASS)));

        DmarcReportAnalysisSummary summary = calculator.calculate(report);

        assertThat(summary.topFailingSources()).isEmpty();
        assertThat(summary.fullyAlignedMessageCount()).isEqualTo(10);
    }

    @Test
    void calculate_givenEmptyReport_thenAllCountsAreZero() {
        DmarcAggregateReport report = new DmarcAggregateReport(METADATA, POLICY_PUBLISHED, List.of());

        DmarcReportAnalysisSummary summary = calculator.calculate(report);

        assertThat(summary.totalMessageCount()).isZero();
        assertThat(summary.distinctSourceIpCount()).isZero();
        assertThat(summary.topFailingSources()).isEmpty();
    }
}
