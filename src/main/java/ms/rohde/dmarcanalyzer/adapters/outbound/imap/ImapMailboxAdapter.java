package ms.rohde.dmarcanalyzer.adapters.outbound.imap;

import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.HeaderTerm;
import ms.rohde.dmarcanalyzer.ports.outbound.DmarcReportAttachment;
import ms.rohde.dmarcanalyzer.ports.outbound.EmailMessageId;
import ms.rohde.dmarcanalyzer.ports.outbound.IncomingReportEmail;
import ms.rohde.dmarcanalyzer.ports.outbound.MailboxException;
import ms.rohde.dmarcanalyzer.ports.outbound.MailboxPort;
import ms.rohde.hexagonalarch.annotations.InfrastructureServiceAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * {@link MailboxPort} implementation backed by Jakarta Mail IMAP.
 *
 * <p>Each call opens and closes its own {@link Store}/{@link Folder} connection — there is no
 * long-lived session state, which keeps the adapter simple, stateless, and safe to call
 * concurrently. Given the low volume of DMARC aggregate reports for a typical domain (at most a
 * handful per day), the reconnect overhead is negligible.
 *
 * <p>A message is only considered a DMARC report candidate — and therefore only ever touched or
 * marked {@code \Seen} — if it carries at least one attachment whose filename matches the
 * conventional DMARC report naming (RFC 7489 Appendix C): a trailing {@code .xml}, {@code .xml.gz}
 * or {@code .xml.zip} (some senders send a bare {@code .gz}/{@code .zip} without the {@code .xml}
 * infix). Every other message in the mailbox is left completely untouched, so the same mailbox
 * remains safe for a human to also read normally.
 */
@InfrastructureServiceAdapter
public class ImapMailboxAdapter implements MailboxPort {

    private static final Logger LOG = LogManager.getLogger(ImapMailboxAdapter.class);

    /**
     * Upper bound on the decompressed size of a single attachment/zip-entry. DMARC aggregate
     * reports are plain XML in the low hundreds of KB even for a busy domain; this leaves generous
     * headroom while still preventing a malicious sender from using a small zip/gzip attachment as
     * a decompression bomb to exhaust the container's heap.
     */
    private static final long MAX_DECOMPRESSED_BYTES = 20L * 1024 * 1024;

    /** Upper bound on the number of entries read from a single zip attachment. */
    private static final int MAX_ZIP_ENTRIES = 20;

    /** Upper bound on nested {@code multipart/*} recursion depth. */
    private static final int MAX_MULTIPART_DEPTH = 10;

    private final ImapMailboxProperties properties;

    public ImapMailboxAdapter(ImapMailboxProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<IncomingReportEmail> fetchUnprocessedDmarcReportEmails() {
        List<IncomingReportEmail> result = new ArrayList<>();
        try {
            Store store = connect();
            try {
                Folder folder = store.getFolder(properties.folder());
                folder.open(Folder.READ_ONLY);
                try {
                    Message[] unseen = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                    for (Message message : unseen) {
                        List<DmarcReportAttachment> attachments = extractDmarcAttachments(message);
                        if (attachments.isEmpty()) {
                            continue;
                        }
                        result.add(new IncomingReportEmail(
                                messageIdOf(message),
                                subjectOf(message),
                                receivedAtOf(message),
                                attachments));
                    }
                } finally {
                    folder.close(false);
                }
            } finally {
                store.close();
            }
        } catch (MessagingException | IOException e) {
            throw new MailboxException("failed to fetch DMARC report emails via IMAP", e);
        }
        return result;
    }

    @Override
    public void markAsProcessed(EmailMessageId id) {
        try {
            Store store = connect();
            try {
                Folder folder = store.getFolder(properties.folder());
                folder.open(Folder.READ_WRITE);
                try {
                    Message[] matches = folder.search(new HeaderTerm("Message-ID", id.value()));
                    for (Message message : matches) {
                        message.setFlag(Flags.Flag.SEEN, true);
                    }
                    if (matches.length == 0) {
                        LOG.warn("could not find message with Message-ID {} to mark as processed", id.value());
                    }
                } finally {
                    folder.close(true);
                }
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            throw new MailboxException("failed to mark message " + id.value() + " as processed", e);
        }
    }

    private Store connect() throws MessagingException {
        String protocol = properties.useSsl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", properties.host());
        props.put("mail." + protocol + ".port", String.valueOf(properties.port()));
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(properties.connectionTimeoutMs()));
        props.put("mail." + protocol + ".timeout", String.valueOf(properties.readTimeoutMs()));
        if (!properties.useSsl()) {
            props.put("mail.imap.starttls.enable", "true");
        }

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(properties.host(), properties.username(), properties.password());
        return store;
    }

    private List<DmarcReportAttachment> extractDmarcAttachments(Message message)
            throws MessagingException, IOException {
        List<DmarcReportAttachment> attachments = new ArrayList<>();
        Object content = message.getContent();
        if (content instanceof Multipart multipart) {
            collectAttachments(multipart, attachments, 0);
        }
        return attachments;
    }

    private void collectAttachments(Multipart multipart, List<DmarcReportAttachment> attachments, int depth)
            throws MessagingException, IOException {
        if (depth >= MAX_MULTIPART_DEPTH) {
            LOG.warn("multipart nesting exceeded the maximum depth of {}, aborting further descent", MAX_MULTIPART_DEPTH);
            return;
        }
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getContent() instanceof Multipart nested) {
                collectAttachments(nested, attachments, depth + 1);
                continue;
            }
            String filename = part.getFileName();
            if (!looksLikeDmarcReportFilename(filename)) {
                continue;
            }
            try {
                attachments.addAll(readAttachment(filename, part.getInputStream()));
            } catch (IOException e) {
                LOG.warn("failed to read/decompress attachment {}, skipping it", filename, e);
            }
        }
    }

    private boolean looksLikeDmarcReportFilename(@Nullable String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xml") || lower.endsWith(".xml.gz") || lower.endsWith(".xml.zip")
                || lower.endsWith(".gz") || lower.endsWith(".zip");
    }

    private List<DmarcReportAttachment> readAttachment(String filename, InputStream rawStream) throws IOException {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return readZipEntries(filename, rawStream);
        }
        if (lower.endsWith(".gz")) {
            try (GZIPInputStream gzip = new GZIPInputStream(rawStream)) {
                return List.of(new DmarcReportAttachment(filename, readFullyBounded(gzip)));
            }
        }
        return List.of(new DmarcReportAttachment(filename, readFullyBounded(rawStream)));
    }

    private List<DmarcReportAttachment> readZipEntries(String zipFilename, InputStream rawStream) throws IOException {
        List<DmarcReportAttachment> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(rawStream)) {
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    LOG.warn("zip attachment {} exceeded the maximum of {} entries, ignoring the rest",
                            zipFilename, MAX_ZIP_ENTRIES);
                    break;
                }
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    entries.add(new DmarcReportAttachment(entry.getName(), readFullyBounded(zip)));
                }
                zip.closeEntry();
            }
        }
        if (entries.isEmpty()) {
            LOG.warn("zip attachment {} contained no .xml entry", zipFilename);
        }
        return entries;
    }

    /**
     * Reads {@code inputStream} fully as UTF-8 text, throwing an {@link IOException} once more than
     * {@link #MAX_DECOMPRESSED_BYTES} have been read — a defense against decompression-bomb
     * attachments from untrusted external senders, since this method is always used downstream of a
     * {@link GZIPInputStream} or {@link ZipInputStream}.
     */
    private String readFullyBounded(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            total += read;
            if (total > MAX_DECOMPRESSED_BYTES) {
                throw new IOException("attachment exceeded the maximum decompressed size of "
                        + MAX_DECOMPRESSED_BYTES + " bytes, aborting read (possible decompression bomb)");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private EmailMessageId messageIdOf(Message message) throws MessagingException {
        String[] headers = message.getHeader("Message-ID");
        if (headers != null && headers.length > 0 && !headers[0].isBlank()) {
            return new EmailMessageId(headers[0]);
        }
        return new EmailMessageId(subjectOf(message) + "|" + receivedAtOf(message));
    }

    private String subjectOf(Message message) throws MessagingException {
        String subject = message.getSubject();
        return subject == null ? "" : subject;
    }

    private Instant receivedAtOf(Message message) throws MessagingException {
        var receivedDate = message.getReceivedDate();
        return receivedDate == null ? Instant.EPOCH : receivedDate.toInstant();
    }
}
