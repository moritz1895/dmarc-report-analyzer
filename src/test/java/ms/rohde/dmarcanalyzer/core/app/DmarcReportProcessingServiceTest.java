package ms.rohde.dmarcanalyzer.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.List;
import ms.rohde.dmarcanalyzer.core.domain.analysis.ComposedEmailSummary;
import ms.rohde.dmarcanalyzer.core.domain.analysis.DmarcReportAnalysisSummary;
import ms.rohde.dmarcanalyzer.core.domain.analysis.DmarcReportStatisticsCalculator;
import ms.rohde.dmarcanalyzer.core.domain.analysis.EmailSummaryComposer;
import ms.rohde.dmarcanalyzer.core.domain.report.Disposition;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAuthResultValue;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcReportParseException;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcReportRecord;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcXmlReportParser;
import ms.rohde.dmarcanalyzer.core.domain.report.PolicyEvaluated;
import ms.rohde.dmarcanalyzer.core.domain.report.PolicyPublished;
import ms.rohde.dmarcanalyzer.core.domain.report.ReportMetadata;
import ms.rohde.dmarcanalyzer.ports.outbound.AiAnalysisException;
import ms.rohde.dmarcanalyzer.ports.outbound.AiAnalysisResult;
import ms.rohde.dmarcanalyzer.ports.outbound.DmarcAnalysisAiPort;
import ms.rohde.dmarcanalyzer.ports.outbound.DmarcReportAttachment;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailMessageId;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailSenderPort;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailSendingException;
import ms.rohde.dmarcanalyzer.ports.outbound.IncomingReportEmail;
import ms.rohde.dmarcanalyzer.ports.outbound.MailboxPort;
import ms.rohde.dmarcanalyzer.ports.outbound.SummaryEmailMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DmarcReportProcessingServiceTest {

    private static final String VALID_XML = "<feedback>valid</feedback>";

    @Mock
    private MailboxPort mailboxPort;

    @Mock
    private EmailSenderPort emailSenderPort;

    @Mock
    private DmarcAnalysisAiPort dmarcAnalysisAiPort;

    @Mock
    private DmarcXmlReportParser dmarcXmlReportParser;

    @Mock
    private DmarcReportStatisticsCalculator dmarcReportStatisticsCalculator;

    @Mock
    private EmailSummaryComposer emailSummaryComposer;

    private DmarcReportProcessingService service;

    private final ReportMetadata metadata = new ReportMetadata(
            "google.com", "noreply@google.com", "1", Instant.EPOCH, Instant.EPOCH.plusSeconds(86400));

    private final PolicyPublished policyPublished = new PolicyPublished(
            "example.com", false, false, Disposition.QUARANTINE, Disposition.QUARANTINE, 100);

    private DmarcAggregateReport sampleReport() {
        PolicyEvaluated policyEvaluated =
                new PolicyEvaluated(Disposition.NONE, DmarcAuthResultValue.PASS, DmarcAuthResultValue.PASS);
        DmarcReportRecord record = new DmarcReportRecord(
                "1.1.1.1", 5, policyEvaluated, "example.com", List.of(), List.of());
        return new DmarcAggregateReport(metadata, policyPublished, List.of(record));
    }

    private DmarcReportAnalysisSummary sampleSummary() {
        return new DmarcReportAnalysisSummary(5, 5, 0, 0, 0, 1, List.of());
    }

    private void initServiceWithoutStubbing() {
        service = new DmarcReportProcessingService(
                mailboxPort, emailSenderPort, dmarcAnalysisAiPort, dmarcXmlReportParser,
                dmarcReportStatisticsCalculator, emailSummaryComposer);
    }

    @Test
    void processIncomingReports_givenEmailWithValidAttachment_thenAnalyzesSendsAndMarksProcessed() {
        initServiceWithoutStubbing();
        EmailMessageId id = new EmailMessageId("msg-1");
        DmarcReportAttachment attachment = new DmarcReportAttachment("report.xml", VALID_XML);
        IncomingReportEmail email = new IncomingReportEmail(id, "DMARC report", Instant.now(), List.of(attachment));

        given(mailboxPort.fetchUnprocessedDmarcReportEmails()).willReturn(List.of(email));
        given(dmarcXmlReportParser.parse(VALID_XML)).willReturn(sampleReport());
        given(dmarcReportStatisticsCalculator.calculate(any())).willReturn(sampleSummary());
        given(dmarcAnalysisAiPort.analyze(anyList(), anyList()))
                .willReturn(new AiAnalysisResult("All good", List.of("Keep monitoring")));
        given(emailSummaryComposer.compose(anyList(), anyList(), any(), anyList()))
                .willReturn(new ComposedEmailSummary("DMARC-Auswertung: google.com", "some body"));

        service.processIncomingReports();

        then(dmarcAnalysisAiPort).should().analyze(List.of(sampleReport()), List.of(sampleSummary()));
        then(emailSenderPort).should().sendSummaryEmail(any(SummaryEmailMessage.class));
        then(mailboxPort).should().markAsProcessed(id);
    }

    @Test
    void processIncomingReports_givenSuccessfulEmail_thenComposerIsCalledAndItsResultIsSent() {
        initServiceWithoutStubbing();
        EmailMessageId id = new EmailMessageId("msg-1");
        DmarcReportAttachment attachment = new DmarcReportAttachment("report.xml", VALID_XML);
        IncomingReportEmail email = new IncomingReportEmail(id, "DMARC report", Instant.now(), List.of(attachment));

        given(mailboxPort.fetchUnprocessedDmarcReportEmails()).willReturn(List.of(email));
        given(dmarcXmlReportParser.parse(VALID_XML)).willReturn(sampleReport());
        given(dmarcReportStatisticsCalculator.calculate(any())).willReturn(sampleSummary());
        given(dmarcAnalysisAiPort.analyze(anyList(), anyList()))
                .willReturn(new AiAnalysisResult("All good", List.of("Keep monitoring")));
        given(emailSummaryComposer.compose(anyList(), anyList(), any(), anyList()))
                .willReturn(new ComposedEmailSummary("DMARC-Auswertung: google.com", "some body"));

        service.processIncomingReports();

        then(emailSummaryComposer).should().compose(
                List.of(sampleReport()), List.of(sampleSummary()), "All good", List.of("Keep monitoring"));

        var captor = ArgumentCaptor.forClass(SummaryEmailMessage.class);
        then(emailSenderPort).should().sendSummaryEmail(captor.capture());
        SummaryEmailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.subject()).isEqualTo("DMARC-Auswertung: google.com");
        assertThat(sentMessage.bodyText()).isEqualTo("some body");
    }

    @Test
    void processIncomingReports_givenAttachmentFailsToParse_thenEmailIsMarkedProcessedWithoutAiCall() {
        initServiceWithoutStubbing();
        EmailMessageId id = new EmailMessageId("msg-2");
        DmarcReportAttachment attachment = new DmarcReportAttachment("broken.xml", "<broken>");
        IncomingReportEmail email = new IncomingReportEmail(id, "Broken report", Instant.now(), List.of(attachment));

        given(mailboxPort.fetchUnprocessedDmarcReportEmails()).willReturn(List.of(email));
        given(dmarcXmlReportParser.parse("<broken>"))
                .willThrow(new DmarcReportParseException("malformed"));

        service.processIncomingReports();

        then(dmarcAnalysisAiPort).should(never()).analyze(anyList(), anyList());
        then(emailSenderPort).should(never()).sendSummaryEmail(any());
        then(mailboxPort).should().markAsProcessed(id);
    }

    @Test
    void processIncomingReports_givenAiAnalysisFails_thenEmailIsNotMarkedProcessedAndNoEmailSent() {
        initServiceWithoutStubbing();
        EmailMessageId id = new EmailMessageId("msg-3");
        DmarcReportAttachment attachment1 = new DmarcReportAttachment("report1.xml", VALID_XML);
        DmarcReportAttachment attachment2 = new DmarcReportAttachment("report2.xml", VALID_XML);
        IncomingReportEmail email =
                new IncomingReportEmail(id, "Two reports", Instant.now(), List.of(attachment1, attachment2));

        given(mailboxPort.fetchUnprocessedDmarcReportEmails()).willReturn(List.of(email));
        given(dmarcXmlReportParser.parse(VALID_XML)).willReturn(sampleReport());
        given(dmarcReportStatisticsCalculator.calculate(any())).willReturn(sampleSummary());
        given(dmarcAnalysisAiPort.analyze(anyList(), anyList()))
                .willThrow(new AiAnalysisException("AI unavailable"));

        service.processIncomingReports();

        then(emailSenderPort).should(never()).sendSummaryEmail(any());
        then(mailboxPort).should(never()).markAsProcessed(id);
    }

    @Test
    void processIncomingReports_givenSendSummaryEmailFails_thenEmailIsNotMarkedProcessed() {
        initServiceWithoutStubbing();
        EmailMessageId id = new EmailMessageId("msg-4");
        DmarcReportAttachment attachment = new DmarcReportAttachment("report.xml", VALID_XML);
        IncomingReportEmail email = new IncomingReportEmail(id, "DMARC report", Instant.now(), List.of(attachment));

        given(mailboxPort.fetchUnprocessedDmarcReportEmails()).willReturn(List.of(email));
        given(dmarcXmlReportParser.parse(VALID_XML)).willReturn(sampleReport());
        given(dmarcReportStatisticsCalculator.calculate(any())).willReturn(sampleSummary());
        given(dmarcAnalysisAiPort.analyze(anyList(), anyList()))
                .willReturn(new AiAnalysisResult("All good", List.of("Keep monitoring")));
        given(emailSummaryComposer.compose(anyList(), anyList(), any(), anyList()))
                .willReturn(new ComposedEmailSummary("DMARC-Auswertung: google.com", "some body"));
        doThrow(new EmailSendingException("SMTP down"))
                .when(emailSenderPort).sendSummaryEmail(any());

        service.processIncomingReports();

        then(mailboxPort).should(never()).markAsProcessed(id);
    }

    @Test
    void processIncomingReports_givenTwoEmailsWhereFirstFails_thenSecondIsStillProcessedAndMarked() {
        initServiceWithoutStubbing();
        EmailMessageId failingId = new EmailMessageId("msg-fail");
        EmailMessageId succeedingId = new EmailMessageId("msg-succeed");
        DmarcReportAttachment failingAttachment = new DmarcReportAttachment("broken.xml", "<broken>");
        DmarcReportAttachment validAttachment = new DmarcReportAttachment("report.xml", VALID_XML);
        IncomingReportEmail failingEmail =
                new IncomingReportEmail(failingId, "Broken", Instant.now(), List.of(failingAttachment));
        IncomingReportEmail succeedingEmail =
                new IncomingReportEmail(succeedingId, "Valid", Instant.now(), List.of(validAttachment));

        given(mailboxPort.fetchUnprocessedDmarcReportEmails())
                .willReturn(List.of(failingEmail, succeedingEmail));
        given(dmarcXmlReportParser.parse("<broken>"))
                .willThrow(new DmarcReportParseException("malformed"));
        given(dmarcXmlReportParser.parse(VALID_XML)).willReturn(sampleReport());
        given(dmarcReportStatisticsCalculator.calculate(any())).willReturn(sampleSummary());
        given(dmarcAnalysisAiPort.analyze(anyList(), anyList()))
                .willReturn(new AiAnalysisResult("All good", List.of("Keep monitoring")));
        given(emailSummaryComposer.compose(anyList(), anyList(), any(), anyList()))
                .willReturn(new ComposedEmailSummary("DMARC-Auswertung: google.com", "some body"));

        service.processIncomingReports();

        then(mailboxPort).should().markAsProcessed(failingId);
        then(mailboxPort).should().markAsProcessed(succeedingId);
        then(emailSenderPort).should().sendSummaryEmail(any());
    }
}
