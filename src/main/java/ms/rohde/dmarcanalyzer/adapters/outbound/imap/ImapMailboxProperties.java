package ms.rohde.dmarcanalyzer.adapters.outbound.imap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for the IMAP mailbox that receives incoming DMARC aggregate report emails.
 *
 * @param host                the IMAP server host name
 * @param port                the IMAP server port (993 for implicit TLS, 143 for STARTTLS)
 * @param username            the mailbox login (e.g. {@code postmaster@example.com})
 * @param password            the mailbox password
 * @param useSsl              whether to connect with implicit TLS (IMAPS); {@code false} uses STARTTLS on a plain
 *                            connection
 * @param folder              the mailbox folder to poll for new reports
 * @param connectionTimeoutMs socket connection timeout in milliseconds
 * @param readTimeoutMs       socket read timeout in milliseconds
 */
@ConfigurationProperties(prefix = "dmarc-analyzer.mail.imap")
public record ImapMailboxProperties(
        String host,
        @DefaultValue("993") int port,
        String username,
        String password,
        @DefaultValue("true") boolean useSsl,
        @DefaultValue("INBOX") String folder,
        @DefaultValue("10000") int connectionTimeoutMs,
        @DefaultValue("10000") int readTimeoutMs) {

    @Override
    public String toString() {
        return "ImapMailboxProperties[host=" + host + ", port=" + port + ", username=" + username
                + ", password=***, useSsl=" + useSsl + ", folder=" + folder
                + ", connectionTimeoutMs=" + connectionTimeoutMs + ", readTimeoutMs=" + readTimeoutMs + "]";
    }
}
