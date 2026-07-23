package ms.rohde.dmarcanalyzer.adapters.outbound.imap;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ms.rohde.dmarcanalyzer.ports.outbound.DmarcReportAttachment;
import org.junit.jupiter.api.Test;

class ImapMailboxAdapterTest {

    private static final Session SESSION = Session.getInstance(new Properties());

    private final ImapMailboxAdapter adapter = new ImapMailboxAdapter(
            new ImapMailboxProperties("imap.example.com", 993, "postmaster@example.com", "secret",
                    true, "INBOX", 10_000, 10_000));

    @Test
    void extractDmarcAttachments_givenSinglePartZipMessageWithoutContentDisposition_thenReadsItAsAttachment()
            throws Exception {
        byte[] zipBytes = zipOf("google.com!example.com!1782691200!1782777599.xml", "<feedback/>");
        MimeMessage message = rawMessage(
                "Content-Type: application/zip; name=google.com!example.com!1782691200!1782777599.zip\r\n"
                        + "Content-Transfer-Encoding: base64\r\n",
                zipBytes);

        var attachments = adapter.extractDmarcAttachments(message);

        assertThat(attachments)
                .containsExactly(new DmarcReportAttachment(
                        "google.com!example.com!1782691200!1782777599.xml", "<feedback/>"));
    }

    @Test
    void extractDmarcAttachments_givenSinglePartMessageThatIsNotAReport_thenReturnsNoAttachments()
            throws Exception {
        MimeMessage message = rawMessage(
                "Content-Type: text/plain; charset=UTF-8\r\n", "just a regular email".getBytes(StandardCharsets.UTF_8));

        var attachments = adapter.extractDmarcAttachments(message);

        assertThat(attachments).isEmpty();
    }

    private static MimeMessage rawMessage(String extraHeaders, byte[] body) throws Exception {
        String raw = "Message-ID: <test@example.com>\r\n"
                + "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + extraHeaders
                + "\r\n"
                + Base64.getMimeEncoder().encodeToString(body);
        return new MimeMessage(SESSION, new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] zipOf(String entryName, String content) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }
}
