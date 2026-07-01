package ms.rohde.dmarcanalyzer.core.app;

import java.util.ArrayList;
import java.util.List;
import ms.rohde.dmarcanalyzer.core.domain.analysis.ComposedEmailSummary;
import ms.rohde.dmarcanalyzer.core.domain.analysis.DmarcReportAnalysisSummary;
import ms.rohde.dmarcanalyzer.core.domain.analysis.DmarcReportStatisticsCalculator;
import ms.rohde.dmarcanalyzer.core.domain.analysis.EmailSummaryComposer;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcReportParseException;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcXmlReportParser;
import ms.rohde.dmarcanalyzer.ports.inbound.DmarcReportProcessingUseCase;
import ms.rohde.dmarcanalyzer.ports.outbound.AiAnalysisException;
import ms.rohde.dmarcanalyzer.ports.outbound.AiAnalysisResult;
import ms.rohde.dmarcanalyzer.ports.outbound.DmarcAnalysisAiPort;
import ms.rohde.dmarcanalyzer.ports.outbound.DmarcReportAttachment;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailSenderPort;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailSendingException;
import ms.rohde.dmarcanalyzer.ports.outbound.IncomingReportEmail;
import ms.rohde.dmarcanalyzer.ports.outbound.MailboxPort;
import ms.rohde.dmarcanalyzer.ports.outbound.SummaryEmailMessage;
import ms.rohde.hexagonalarch.annotations.ApplicationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Orchestrates the end-to-end DMARC report processing workflow: fetching
 * unprocessed report emails from the mailbox, parsing their DMARC XML
 * attachments, requesting an AI analysis per email, sending a summary email
 * back to the postmaster, and marking each source email as processed once
 * handled.
 */
@ApplicationService
public class DmarcReportProcessingService implements DmarcReportProcessingUseCase {

    private static final Logger LOG = LogManager.getLogger(DmarcReportProcessingService.class);

    private final MailboxPort mailboxPort;
    private final EmailSenderPort emailSenderPort;
    private final DmarcAnalysisAiPort dmarcAnalysisAiPort;
    private final DmarcXmlReportParser dmarcXmlReportParser;
    private final DmarcReportStatisticsCalculator dmarcReportStatisticsCalculator;
    private final EmailSummaryComposer emailSummaryComposer;

    public DmarcReportProcessingService(
            MailboxPort mailboxPort,
            EmailSenderPort emailSenderPort,
            DmarcAnalysisAiPort dmarcAnalysisAiPort,
            DmarcXmlReportParser dmarcXmlReportParser,
            DmarcReportStatisticsCalculator dmarcReportStatisticsCalculator,
            EmailSummaryComposer emailSummaryComposer) {
        this.mailboxPort = mailboxPort;
        this.emailSenderPort = emailSenderPort;
        this.dmarcAnalysisAiPort = dmarcAnalysisAiPort;
        this.dmarcXmlReportParser = dmarcXmlReportParser;
        this.dmarcReportStatisticsCalculator = dmarcReportStatisticsCalculator;
        this.emailSummaryComposer = emailSummaryComposer;
    }

    @Override
    public void processIncomingReports() {
        List<IncomingReportEmail> unprocessedEmails = mailboxPort.fetchUnprocessedDmarcReportEmails();
        for (IncomingReportEmail email : unprocessedEmails) {
            processEmail(email);
        }
    }

    private void processEmail(IncomingReportEmail email) {
        List<DmarcAggregateReport> reports = new ArrayList<>();
        for (DmarcReportAttachment attachment : email.attachments()) {
            try {
                reports.add(dmarcXmlReportParser.parse(attachment.xmlContent()));
            } catch (DmarcReportParseException e) {
                LOG.warn("Failed to parse DMARC report attachment '{}' of email '{}'",
                        attachment.filename(), email.id().value(), e);
            }
        }

        if (reports.isEmpty()) {
            mailboxPort.markAsProcessed(email.id());
            return;
        }

        List<DmarcReportAnalysisSummary> summaries = reports.stream()
                .map(dmarcReportStatisticsCalculator::calculate)
                .toList();

        AiAnalysisResult analysisResult;
        try {
            analysisResult = dmarcAnalysisAiPort.analyze(reports, summaries);
        } catch (AiAnalysisException e) {
            LOG.error("AI analysis failed for email '{}'; leaving it unprocessed for retry", email.id().value(), e);
            return;
        }

        ComposedEmailSummary composedEmailSummary = emailSummaryComposer.compose(
                reports, summaries, analysisResult.humanReadableSummary(), analysisResult.recommendations());
        SummaryEmailMessage summaryEmailMessage =
                new SummaryEmailMessage(composedEmailSummary.subject(), composedEmailSummary.bodyText());
        try {
            emailSenderPort.sendSummaryEmail(summaryEmailMessage);
        } catch (EmailSendingException e) {
            LOG.error("Sending summary email failed for email '{}'; leaving it unprocessed for retry",
                    email.id().value(), e);
            return;
        }

        mailboxPort.markAsProcessed(email.id());
    }
}
