package ms.rohde.dmarcanalyzer.ports.outbound;

import ms.rohde.hexagonalarch.annotations.InfrastructureServicePort;

/**
 * Outbound port for sending the generated DMARC analysis summary back to
 * the postmaster via email.
 */
@InfrastructureServicePort
public interface EmailSenderPort {

    /**
     * Sends {@code message} via the configured outbound mail transport.
     *
     * @throws EmailSendingException if the message could not be sent
     */
    void sendSummaryEmail(SummaryEmailMessage message);
}
