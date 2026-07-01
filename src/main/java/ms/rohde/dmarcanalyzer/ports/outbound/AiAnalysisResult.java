package ms.rohde.dmarcanalyzer.ports.outbound;

import java.util.List;

/**
 * The AI-generated analysis of one or more DMARC aggregate reports: a
 * narrative summary plus concrete, actionable recommendations.
 */
public record AiAnalysisResult(String humanReadableSummary, List<String> recommendations) {
}
