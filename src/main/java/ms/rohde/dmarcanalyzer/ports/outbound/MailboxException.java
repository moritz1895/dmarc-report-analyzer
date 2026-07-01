package ms.rohde.dmarcanalyzer.ports.outbound;

/**
 * Thrown by a {@link MailboxPort} implementation when an IMAP operation
 * fails.
 */
public class MailboxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MailboxException(String message) {
        super(message);
    }

    public MailboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
