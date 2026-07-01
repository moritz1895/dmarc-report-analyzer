package ms.rohde.dmarcanalyzer.ports.inbound;

import ms.rohde.hexagonalarch.annotations.DrivingPort;

/**
 * Inbound port that drives the DMARC report processing workflow: fetching
 * unprocessed report emails, parsing their attachments, requesting an AI
 * analysis per email, sending one summary email per source email back to
 * the configured recipient, and marking each source email as processed.
 */
@DrivingPort
public interface DmarcReportProcessingUseCase {

    /**
     * Processes all currently unprocessed DMARC report emails in one run.
     *
     * <p>An email whose attachments are all unparseable is marked as
     * processed anyway, since a permanently malformed report would
     * otherwise be retried forever. An email is left unprocessed for retry
     * on the next run only when a genuinely transient failure occurs, i.e.
     * the AI analysis call or the outbound summary email send fails.
     */
    void processIncomingReports();
}
