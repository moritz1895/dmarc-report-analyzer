package ms.rohde.dmarcanalyzer.ports.outbound;

import java.time.Instant;
import java.util.List;

/**
 * An email fetched from the monitored mailbox that is expected to contain
 * one or more DMARC aggregate report attachments.
 */
public record IncomingReportEmail(
        EmailMessageId id,
        String subject,
        Instant receivedAt,
        List<DmarcReportAttachment> attachments) {
}
