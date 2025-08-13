package ac.su.kdt.beaievaluationservice.kafka.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

// 미션 완료 이벤트
// 이 이벤트는 미션이 완료되었을 때 발생하며, AI 평가 서비스에서
// 해당 미션의 평가를 시작하는 트리거 역할을 함
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MissionCompletedEvent {
    private String eventType;
    private String userId;
    private String missionId;
    private String missionAttemptId;
    private String missionType;
    private String code;
    private String missionTitle;
    private LocalDateTime completedAt;
    
    // 새로 추가된 필드들
    private String missionObjective;        // 미션의 목표 및 요구사항
    private List<String> checklist;         // 미션의 체크리스트 항목들
    private String s3StorageUrl;            // S3 저장소 주소
    private String s3PreSignedUrl;          // S3 Pre-Signed URL
    
    // 간단한 통계 정보 (선택사항)
    private SimpleStatistics statistics;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleStatistics {
        private Integer commandSuccessCount;    // 명령 성공 횟수
        private Integer commandFailureCount;    // 명령 실패 횟수
        private List<String> topErrorMessages;  // 상위 에러 메시지들 (Top N)
        private Double averageCpuUsage;         // 평균 CPU 사용량 (%)
        private Double maxCpuUsage;             // 최대 CPU 사용량 (%)
        private Double averageMemoryUsage;      // 평균 메모리 사용량 (%)
        private Double maxMemoryUsage;          // 최대 메모리 사용량 (%)
        private Long totalExecutionTime;       // 전체 실행 시간 (ms)
    }
}