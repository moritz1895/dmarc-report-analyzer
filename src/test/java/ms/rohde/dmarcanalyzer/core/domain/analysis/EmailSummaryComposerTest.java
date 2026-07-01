package ms.rohde.dmarcanalyzer.core.domain.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import ms.rohde.dmarcanalyzer.core.domain.report.Disposition;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAuthResultValue;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcReportRecord;
import ms.rohde.dmarcanalyzer.core.domain.report.PolicyEvaluated;
import ms.rohde.dmarcanalyzer.core.domain.report.PolicyPublished;
import ms.rohde.dmarcanalyzer.core.domain.report.ReportMetadata;
import org.junit.jupiter.api.Test;

class EmailSummaryComposerTest {

    private final EmailSummaryComposer composer = new EmailSummaryComposer();

    private final PolicyPublished policyPublished = new PolicyPublished(
            "example.com", false, false, Disposition.QUARANTINE, Disposition.QUARANTINE, 100);

    private DmarcAggregateReport reportFor(String orgName) {
        ReportMetadata metadata = new ReportMetadata(
                orgName, "noreply@example.com", "1", Instant.EPOCH, Instant.EPOCH.plusSeconds(86400));
        PolicyEvaluated policyEvaluated =
                new PolicyEvaluated(Disposition.NONE, DmarcAuthResultValue.PASS, DmarcAuthResultValue.PASS);
        DmarcReportRecord record = new DmarcReportRecord(
                "1.1.1.1", 5, policyEvaluated, "example.com", List.of(), List.of());
        return new DmarcAggregateReport(metadata, policyPublished, List.of(record));
    }

    private DmarcReportAnalysisSummary summaryWith(
            int totalMessageCount, int fullyAlignedMessageCount, int distinctSourceIpCount) {
        return new DmarcReportAnalysisSummary(
                totalMessageCount, fullyAlignedMessageCount, 0, 0, 0, distinctSourceIpCount, List.of());
    }

    @Test
    void compose_givenSingleReport_thenSubjectIsPrefixWithOrgName() {
        DmarcAggregateReport report = reportFor("google.com");

        ComposedEmailSummary result = composer.compose(
                List.of(report), List.of(summaryWith(5, 5, 1)), "All good", List.of("Keep monitoring"));

        assertThat(result.subject()).isEqualTo("DMARC-Auswertung: google.com");
    }

    @Test
    void compose_givenMultipleReportsWithDistinctOrgNames_thenSubjectJoinsOrgNamesWithComma() {
        DmarcAggregateReport googleReport = reportFor("google.com");
        DmarcAggregateReport yahooReport = reportFor("yahoo.com");

        ComposedEmailSummary result = composer.compose(
                List.of(googleReport, yahooReport),
                List.of(summaryWith(5, 5, 1), summaryWith(3, 3, 1)),
                "All good",
                List.of("Keep monitoring"));

        assertThat(result.subject()).isEqualTo("DMARC-Auswertung: google.com, yahoo.com");
    }

    @Test
    void compose_givenDuplicateOrgNames_thenSubjectListsOrgNameOnce() {
        DmarcAggregateReport firstReport = reportFor("google.com");
        DmarcAggregateReport secondReport = reportFor("google.com");

        ComposedEmailSummary result = composer.compose(
                List.of(firstReport, secondReport),
                List.of(summaryWith(5, 5, 1), summaryWith(3, 3, 1)),
                "All good",
                List.of("Keep monitoring"));

        assertThat(result.subject()).isEqualTo("DMARC-Auswertung: google.com");
    }

    @Test
    void compose_givenAiSummary_thenBodyStartsWithAiSummaryText() {
        DmarcAggregateReport report = reportFor("google.com");

        ComposedEmailSummary result = composer.compose(
                List.of(report), List.of(summaryWith(5, 5, 1)), "All good", List.of("Keep monitoring"));

        assertThat(result.bodyText()).startsWith("All good\n\n");
    }

    @Test
    void compose_givenRecommendations_thenBodyContainsRecommendationsHeadingAndBulletedList() {
        DmarcAggregateReport report = reportFor("google.com");

        ComposedEmailSummary result = composer.compose(
                List.of(report),
                List.of(summaryWith(5, 5, 1)),
                "All good",
                List.of("Keep monitoring", "Enable DKIM"));

        assertThat(result.bodyText()).contains("Empfehlungen:\n- Keep monitoring\n- Enable DKIM\n");
    }

    @Test
    void compose_givenEmptyRecommendations_thenBodyContainsHeadingWithoutBullets() {
        DmarcAggregateReport report = reportFor("google.com");

        ComposedEmailSummary result = composer.compose(
                List.of(report), List.of(summaryWith(5, 5, 1)), "All good", List.of());

        assertThat(result.bodyText()).contains("Empfehlungen:\n\n");
        assertThat(result.bodyText()).doesNotContain("- ");
    }

    @Test
    void compose_givenReportAndSummary_thenBodyContainsOrgNameAndStatisticsLine() {
        DmarcAggregateReport report = reportFor("google.com");

        ComposedEmailSummary result = composer.compose(
                List.of(report), List.of(summaryWith(10, 7, 3)), "All good", List.of("Keep monitoring"));

        assertThat(result.bodyText()).contains(
                "google.com: Nachrichten gesamt=10, vollständig ausgerichtet=7, eindeutige Quell-IPs=3\n");
    }

    @Test
    void compose_givenMultipleReports_thenBodyContainsOneLinePerReportInOrder() {
        DmarcAggregateReport googleReport = reportFor("google.com");
        DmarcAggregateReport yahooReport = reportFor("yahoo.com");

        ComposedEmailSummary result = composer.compose(
                List.of(googleReport, yahooReport),
                List.of(summaryWith(10, 7, 3), summaryWith(4, 4, 2)),
                "All good",
                List.of("Keep monitoring"));

        String expectedTail = "google.com: Nachrichten gesamt=10, vollständig ausgerichtet=7, eindeutige Quell-IPs=3\n"
                + "yahoo.com: Nachrichten gesamt=4, vollständig ausgerichtet=4, eindeutige Quell-IPs=2\n";
        assertThat(result.bodyText()).endsWith(expectedTail);
    }

    @Test
    void compose_givenOrgNameWithCrlfInjectionAttempt_thenSubjectDoesNotContainRawControlCharacters() {
        DmarcAggregateReport maliciousReport = reportFor("evil.com\r\nBcc: attacker@evil.com");

        ComposedEmailSummary result = composer.compose(
                List.of(maliciousReport), List.of(summaryWith(5, 5, 1)), "All good", List.of("Keep monitoring"));

        assertThat(result.subject()).doesNotContain("\r");
        assertThat(result.subject()).doesNotContain("\n");
        assertThat(result.subject()).isEqualTo("DMARC-Auswertung: evil.comBcc: attacker@evil.com");
    }

    @Test
    void compose_givenOrgNameWithCrlfInjectionAttempt_thenBodyStillContainsRawUnsanitizedOrgName() {
        String rawOrgName = "evil.com\r\nBcc: attacker@evil.com";
        DmarcAggregateReport maliciousReport = reportFor(rawOrgName);

        ComposedEmailSummary result = composer.compose(
                List.of(maliciousReport), List.of(summaryWith(5, 5, 1)), "All good", List.of("Keep monitoring"));

        assertThat(result.bodyText()).contains(rawOrgName + ": ");
    }
}
