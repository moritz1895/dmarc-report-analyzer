package ms.rohde.dmarcanalyzer.ports.outbound;

/**
 * A single DMARC XML attachment extracted from an incoming email, already
 * decompressed/unzipped by the adapter. This layer never deals with archive
 * formats.
 */
public record DmarcReportAttachment(String filename, String xmlContent) {
}
