package ac.su.kdt.beaievaluationservice.controller;

import ac.su.kdt.beaievaluationservice.dto.HealthCheckResponse;
import ac.su.kdt.beaievaluationservice.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DevTrip 표준 헬스체크 컨트롤러
 * 모든 서비스에서 동일한 형식의 헬스체크 엔드포인트 제공
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class StandardHealthController {
    
    private final HealthCheckService healthCheckService;
    
    /**
     * 표준 헬스체크 엔드포인트
     * Gateway 및 모니터링 시스템에서 사용
     */
    @GetMapping("/health")
    public ResponseEntity<HealthCheckResponse> health() {
        try {
            HealthCheckResponse response = healthCheckService.performHealthCheck();
            
            // AI Evaluation 서비스 특화 정보 추가
            Map<String, Object> details = response.getDetails();
            if (details != null) {
                details.put("features", Map.of(
                    "ai-evaluation", "enabled",
                    "gemini-integration", "enabled",
                    "mission-assessment", "enabled",
                    "kafka", "enabled",
                    "s3-integration", "enabled"
                ));
            }
            
            // 상태에 따른 적절한 HTTP 상태 코드 반환
            if ("DOWN".equals(response.getStatus())) {
                return ResponseEntity.status(503).body(response);
            } else if ("WARNING".equals(response.getStatus())) {
                return ResponseEntity.status(200).body(response); // Warning은 여전히 200
            } else {
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            log.error("Health check endpoint failed", e);
            return ResponseEntity.status(503).body(
                HealthCheckResponse.builder()
                    .status("DOWN")
                    .service("BE-AI-evaluation-service")
                    .version("1.0.0")
                    .timestamp(LocalDateTime.now())
                    .details(Map.of("error", e.getMessage()))
                    .build()
            );
        }
    }
}