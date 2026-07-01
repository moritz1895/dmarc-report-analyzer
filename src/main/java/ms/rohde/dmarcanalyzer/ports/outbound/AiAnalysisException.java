package ms.rohde.dmarcanalyzer.ports.outbound;

/**
 * Thrown by a {@link DmarcAnalysisAiPort} implementation when the AI
 * analysis call fails.
 */
public class AiAnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiAnalysisException(String message) {
        super(message);
    }

    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
