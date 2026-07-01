package ms.rohde.dmarcanalyzer.ports.outbound;

/**
 * A plain-text summary email to be sent to the mailbox's fixed, adapter-configured recipient.
 *
 * <p>There is intentionally no {@code recipient} field here: for this service there is exactly
 * one destination (the monitored postmaster mailbox itself), which is deployment configuration of
 * the {@code EmailSenderPort} adapter, not a business decision made by the application layer.
 */
public record SummaryEmailMessage(String subject, String bodyText) {
}
