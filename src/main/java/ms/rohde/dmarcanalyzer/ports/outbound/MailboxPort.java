package ms.rohde.dmarcanalyzer.ports.outbound;

import java.util.List;
import ms.rohde.hexagonalarch.annotations.InfrastructureServicePort;

/**
 * Outbound port for reading DMARC report emails from the monitored mailbox
 * and marking them as processed once handled.
 */
@InfrastructureServicePort
public interface MailboxPort {

    /**
     * Returns all emails in the monitored mailbox that have not yet been
     * marked as processed. Implementations must not return the same email
     * twice unless {@link #markAsProcessed(EmailMessageId)} was never called
     * for it. Ordering of the returned list is not guaranteed.
     *
     * @throws MailboxException if the mailbox cannot be reached or read
     */
    List<IncomingReportEmail> fetchUnprocessedDmarcReportEmails();

    /**
     * Marks the email identified by {@code id} as processed so it is not
     * returned by future calls to {@link #fetchUnprocessedDmarcReportEmails()}.
     * Implementations should make this idempotent — marking an already
     * processed email again must not fail.
     *
     * @throws MailboxException if the mailbox cannot be reached or updated
     */
    void markAsProcessed(EmailMessageId id);
}
