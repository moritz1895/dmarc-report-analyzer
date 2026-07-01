package ms.rohde.dmarcanalyzer.ports.outbound;

/**
 * Wraps a mail server's unique Message-ID (or an equivalent stable
 * identifier) used to detect and avoid reprocessing the same email twice.
 */
public record EmailMessageId(String value) {
}
