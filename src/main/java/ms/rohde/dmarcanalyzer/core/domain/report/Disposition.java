package ms.rohde.dmarcanalyzer.core.domain.report;

import java.util.Locale;

/**
 * DMARC policy disposition applied by the receiving mail server, as reported
 * in {@code policy_published} and {@code policy_evaluated} elements.
 */
public enum Disposition {
    NONE,
    QUARANTINE,
    REJECT;

    public static Disposition fromXmlValue(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NONE" -> NONE;
            case "QUARANTINE" -> QUARANTINE;
            case "REJECT" -> REJECT;
            default -> throw new DmarcReportParseException("Unknown disposition value: " + value);
        };
    }
}
