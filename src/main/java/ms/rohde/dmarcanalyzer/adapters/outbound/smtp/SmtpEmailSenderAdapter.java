package ms.rohde.dmarcanalyzer.adapters.outbound.smtp;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailSenderPort;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailSendingException;
import ms.rohde.dmarcanalyzer.ports.outbound.SummaryEmailMessage;
import ms.rohde.hexagonalarch.annotations.InfrastructureServiceAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * {@link EmailSenderPort} implementation backed by Jakarta Mail SMTP.
 *
 * <p>The recipient is intentionally not part of {@link SummaryEmailMessage} — for this service
 * there is exactly one fixed destination (the monitored postmaster mailbox itself), configured via
 * {@link SmtpProperties#recipientAddress()}, so it is adapter/deployment configuration rather than
 * a per-call business decision.
 */
@InfrastructureServiceAdapter
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private static final Logger LOG = LogManager.getLogger(SmtpEmailSenderAdapter.class);

    private final SmtpProperties properties;

    public SmtpEmailSenderAdapter(SmtpProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendSummaryEmail(SummaryEmailMessage message) {
        LOG.debug("connecting to SMTP {}:{} to send summary '{}' to {}",
                properties.host(), properties.port(), message.subject(), properties.recipientAddress());
        try {
            Session session = createSession();
            MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(properties.fromAddress()));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(properties.recipientAddress()));
            mimeMessage.setSubject(message.subject(), StandardCharsets.UTF_8.name());
            mimeMessage.setText(message.bodyText(), StandardCharsets.UTF_8.name());

            Transport.send(mimeMessage, properties.username(), properties.password());
            LOG.debug("summary email '{}' sent successfully", message.subject());
        } catch (MessagingException e) {
            throw new EmailSendingException("failed to send DMARC analysis summary email", e);
        }
    }

    private Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", properties.host());
        props.put("mail.smtp.port", String.valueOf(properties.port()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", String.valueOf(properties.connectionTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(properties.readTimeoutMs()));
        if (properties.useStartTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        } else {
            props.put("mail.smtp.ssl.enable", "true");
        }
        return Session.getInstance(props);
    }
}
