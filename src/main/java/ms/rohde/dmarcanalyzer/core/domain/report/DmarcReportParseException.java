package ms.rohde.dmarcanalyzer.core.domain.report;

/**
 * Thrown when a DMARC aggregate report XML document is malformed or cannot
 * be parsed into a {@link DmarcAggregateReport}.
 */
public class DmarcReportParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DmarcReportParseException(String message) {
        super(message);
    }

    public DmarcReportParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
