package ac.su.kdt.beaievaluationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DevTrip 표준 Health Check 응답 DTO
 * 모든 서비스에서 동일한 형식의 헬스체크 응답을 제공
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResponse {
    
    /**
     * 서비스 전체 상태 (UP, DOWN, OUT_OF_SERVICE)
     */
    private String status;
    
    /**
     * 서비스 이름
     */
    private String service;
    
    /**
     * 서비스 버전
     */
    private String version;
    
    /**
     * 체크 시점
     */
    private LocalDateTime timestamp;
    
    /**
     * 각 컴포넌트별 상태 정보
     */
    private Map<String, ComponentHealth> components;
    
    /**
     * 추가 상세 정보
     */
    private Map<String, Object> details;
    
    /**
     * 컴포넌트 헬스 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        private String status;
        private Map<String, Object> details;
    }
}