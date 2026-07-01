package ms.rohde.dmarcanalyzer.adapters.outbound.smtp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for the SMTP relay used to send the DMARC analysis summary back to the
 * postmaster mailbox.
 *
 * @param host           the SMTP server host name
 * @param port           the SMTP server port (587 for STARTTLS, 465 for implicit TLS)
 * @param username       the SMTP login
 * @param password       the SMTP password
 * @param useStartTls    whether to upgrade the connection with STARTTLS
 * @param fromAddress    the sender address used in the summary email
 * @param recipientAddress the single fixed recipient of every DMARC analysis summary (typically
 *                       the same postmaster mailbox that this service also polls via IMAP)
 * @param connectionTimeoutMs socket connection timeout in milliseconds
 * @param readTimeoutMs  socket read timeout in milliseconds
 */
@ConfigurationProperties(prefix = "dmarc-analyzer.mail.smtp")
public record SmtpProperties(
        String host,
        @DefaultValue("587") int port,
        String username,
        String password,
        @DefaultValue("true") boolean useStartTls,
        String fromAddress,
        String recipientAddress,
        @DefaultValue("10000") int connectionTimeoutMs,
        @DefaultValue("10000") int readTimeoutMs) {

    @Override
    public String toString() {
        return "SmtpProperties[host=" + host + ", port=" + port + ", username=" + username
                + ", password=***, useStartTls=" + useStartTls + ", fromAddress=" + fromAddress
                + ", recipientAddress=" + recipientAddress + ", connectionTimeoutMs=" + connectionTimeoutMs
                + ", readTimeoutMs=" + readTimeoutMs + "]";
    }
}
