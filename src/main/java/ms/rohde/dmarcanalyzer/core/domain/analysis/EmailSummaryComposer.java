package ms.rohde.dmarcanalyzer.core.domain.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.hexagonalarch.annotations.DomainService;

/**
 * Composes the subject and body text of the DMARC analysis summary email
 * from parsed reports, their calculated statistics, and the AI-generated
 * analysis text and recommendations.
 */
@DomainService
public class EmailSummaryComposer {

    private static final String SUBJECT_PREFIX = "DMARC-Auswertung: ";

    public ComposedEmailSummary compose(
            List<DmarcAggregateReport> reports,
            List<DmarcReportAnalysisSummary> summaries,
            String aiSummary,
            List<String> recommendations) {
        Set<String> orgNames = reports.stream()
                .map(report -> sanitizeForHeader(report.metadata().orgName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String subject = SUBJECT_PREFIX + String.join(", ", orgNames);

        StringBuilder body = new StringBuilder();
        body.append(aiSummary);
        body.append("\n\n");
        body.append("Empfehlungen:\n");
        for (String recommendation : recommendations) {
            body.append("- ").append(recommendation).append('\n');
        }
        body.append('\n');
        for (int i = 0; i < reports.size(); i++) {
            DmarcAggregateReport report = reports.get(i);
            DmarcReportAnalysisSummary summary = summaries.get(i);
            body.append(report.metadata().orgName()).append(": ")
                    .append("Nachrichten gesamt=").append(summary.totalMessageCount()).append(", ")
                    .append("vollständig ausgerichtet=").append(summary.fullyAlignedMessageCount()).append(", ")
                    .append("eindeutige Quell-IPs=").append(summary.distinctSourceIpCount())
                    .append('\n');
        }

        return new ComposedEmailSummary(subject, body.toString());
    }

    /**
     * Strips CR/LF and other control characters from a value that originates from an untrusted,
     * externally supplied DMARC XML attachment before it is used in an email header value — a
     * report sender otherwise fully controls this text and could attempt header/content injection
     * (e.g. embedding a fake {@code Bcc:} line) via a crafted {@code org_name}.
     */
    private String sanitizeForHeader(String value) {
        return value.codePoints()
                .filter(codePoint -> codePoint >= 0x20 && codePoint != 0x7F)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim();
    }
}
