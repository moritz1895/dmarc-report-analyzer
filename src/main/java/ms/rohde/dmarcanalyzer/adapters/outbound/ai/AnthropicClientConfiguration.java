package ms.rohde.dmarcanalyzer.adapters.outbound.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link AnthropicClient} bean. The API key is never read as an application
 * property — the SDK resolves {@code ANTHROPIC_API_KEY} (or an {@code ant auth login} profile)
 * from the process environment automatically.
 */
@Configuration
public class AnthropicClientConfiguration {

    @Bean
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }
}
