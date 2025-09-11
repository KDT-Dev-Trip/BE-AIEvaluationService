package ac.su.kdt.beaievaluationservice.exception;

public class GeminiApiException extends EvaluationException {
    public GeminiApiException(String message) {
        super(message);
    }
    
    public GeminiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}