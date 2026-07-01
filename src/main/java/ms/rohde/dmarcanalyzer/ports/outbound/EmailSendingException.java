package ms.rohde.dmarcanalyzer.ports.outbound;

/**
 * Thrown by an {@link EmailSenderPort} implementation when sending a
 * summary email fails.
 */
public class EmailSendingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailSendingException(String message) {
        super(message);
    }

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
