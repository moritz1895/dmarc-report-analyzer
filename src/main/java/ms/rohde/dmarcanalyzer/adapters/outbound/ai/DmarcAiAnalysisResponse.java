package ms.rohde.dmarcanalyzer.adapters.outbound.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * JSON shape requested from Claude via structured outputs. Adapter-local DTO — mapped to the
 * port-facing {@code AiAnalysisResult} by {@link AnthropicDmarcAnalysisAdapter}.
 *
 * @param summary         a short, German-language, easy-to-understand narrative summary of the
 *                         DMARC report(s) for a postmaster who is not a DMARC expert
 * @param recommendations concrete, actionable recommendations in German (e.g. "Prüfen Sie, ob
 *                         Absender X eine berechtigte Quelle ist" or "Passen Sie den SPF-Eintrag
 *                         für Y an")
 */
record DmarcAiAnalysisResponse(
        @JsonPropertyDescription(
                "Eine kurze, leicht verständliche, deutschsprachige Zusammenfassung der DMARC-Auswertung "
                        + "für einen Postmaster ohne tiefes DMARC-Fachwissen.")
        String summary,
        @JsonPropertyDescription(
                "Konkrete, umsetzbare Handlungsempfehlungen auf Deutsch, basierend auf den DMARC-Kennzahlen.")
        List<String> recommendations) {
}
