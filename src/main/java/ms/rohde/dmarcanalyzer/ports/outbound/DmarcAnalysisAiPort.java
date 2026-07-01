package ms.rohde.dmarcanalyzer.ports.outbound;

import java.util.List;
import ms.rohde.dmarcanalyzer.core.domain.analysis.DmarcReportAnalysisSummary;
import ms.rohde.dmarcanalyzer.core.domain.report.DmarcAggregateReport;
import ms.rohde.hexagonalarch.annotations.InfrastructureServicePort;

/**
 * Outbound port for requesting an AI-generated, human-readable analysis of
 * one or more DMARC aggregate reports that arrived in the same email.
 *
 * <p>{@code reports} and {@code summaries} are parallel lists of the same
 * size; {@code summaries.get(i)} is the pre-computed statistical summary
 * corresponding to {@code reports.get(i)}.
 *
 * <p>Implementations are expected to call an external AI model and may be
 * comparatively slow and costly. Callers should therefore batch all reports
 * from one email into a single call rather than calling this method once
 * per report.
 */
@InfrastructureServicePort
public interface DmarcAnalysisAiPort {

    /**
     * @throws AiAnalysisException if the underlying AI call fails
     */
    AiAnalysisResult analyze(List<DmarcAggregateReport> reports, List<DmarcReportAnalysisSummary> summaries);
}
