package ms.rohde.dmarcanalyzer.core.domain.report;

import java.util.Locale;

/**
 * Outcome of a DKIM or SPF authentication check as reported in a DMARC
 * aggregate report. Values in the wild are sometimes inconsistently cased,
 * so parsing is lenient.
 */
public enum DmarcAuthResultValue {
    PASS,
    FAIL;

    public static DmarcAuthResultValue fromXmlValue(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PASS" -> PASS;
            case "FAIL" -> FAIL;
            default -> throw new DmarcReportParseException("Unknown auth result value: " + value);
        };
    }
}
