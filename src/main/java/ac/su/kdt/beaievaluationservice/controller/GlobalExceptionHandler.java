package ac.su.kdt.beaievaluationservice.controller;

import ac.su.kdt.beaievaluationservice.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

// 전역 예외 처리 핸들러
@Slf4j
@RestControllerAdvice(basePackages = "ac.su.kdt.beaievaluationservice.controller")
public class GlobalExceptionHandler {

    /**
     * Validation 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("Validation failed: {}", errorMessage);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("입력 값 검증 실패: " + errorMessage));
    }

    /**
     * 제약 조건 위반 예외 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        log.warn("Constraint violation: {}", errorMessage);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("제약 조건 위반: " + errorMessage));
    }

    /**
     * 타입 변환 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String errorMessage = String.format("파라미터 '%s'의 값 '%s'을 %s 타입으로 변환할 수 없습니다.", 
                e.getName(), e.getValue(), e.getRequiredType().getSimpleName());
        
        log.warn("Type conversion failed: {}", errorMessage);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(errorMessage));
    }

    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("잘못된 파라미터: " + e.getMessage()));
    }
    
    /**
     * 일반적인 런타임 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception occurred", e);
        
        // 민감한 정보 노출 방지
        String safeMessage = sanitizeErrorMessage(e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("서버 내부 오류가 발생했습니다: " + safeMessage));
    }

    /**
     * 모든 기타 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("Unexpected exception occurred", e);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("예기치 않은 오류가 발생했습니다. 관리자에게 문의하세요."));
    }
    
    /**
     * 에러 메시지에서 민감한 정보 제거
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "알 수 없는 오류";
        }
        
        // 민감한 정보가 포함될 수 있는 패턴 제거
        return message.replaceAll("(?i)(password|token|key|secret)=[^\\s]*", "[REDACTED]")
                     .replaceAll("(?i)jdbc:[^\\s]*", "[DATABASE_URL_REDACTED]");
    }
}