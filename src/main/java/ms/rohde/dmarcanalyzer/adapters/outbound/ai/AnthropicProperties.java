package ms.rohde.dmarcanalyzer.adapters.outbound.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Claude model used to analyze DMARC aggregate reports.
 *
 * <p>The Anthropic API key itself is intentionally NOT a property here — the Anthropic Java SDK
 * resolves it automatically from the standard {@code ANTHROPIC_API_KEY} environment variable, so
 * it never needs to be duplicated into a custom configuration key.
 *
 * @param model the Claude model id. Defaults to {@code claude-haiku-4-5} — DMARC report
 *              summarization is a well-structured, low-reasoning classification/summarization
 *              task on small inputs, so the fastest and cheapest Claude model is the right choice
 *              here; there is no quality benefit from a larger model for this use case.
 */
@ConfigurationProperties(prefix = "dmarc-analyzer.ai.anthropic")
public record AnthropicProperties(@DefaultValue("claude-haiku-4-5") String model) {
}
