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
    
    // 학습 목표 관련 필드들 (Mission 엔티티에서 가져옴)
    private String evaluationCriteria;      // Mission의 평가 기준 (JSON 형태)
    private String missionGuide;            // Mission의 가이드 (마크다운)
    
    // S3 관련 필드들
    private String s3StorageUrl;            // S3 저장소 주소
    private String s3PreSignedUrl;          // S3 Pre-Signed URL
    
    // 간단한 통계 정보 (선택사항)
    private SimpleStatistics statistics;
    
    // 실제 실행 데이터 (새로 추가)
    private RealExecutionData realExecutionData;
    
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
    
    /**
     * 실제 명령어 실행 데이터
     * 미션 관리 서비스에서 수집된 실제 사용자 실행 데이터
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealExecutionData {
        // 명령어 실행 히스토리
        private List<CommandExecutionRecord> commandHistory;
        private List<CommandExecutionRecord> significantCommands;
        private List<CommandExecutionRecord> failedCommands;
        
        // 리소스 사용량
        private ResourceUsageMetrics resourceUsage;
        
        // 세션 정보
        private SessionMetadata sessionInfo;
        
        // 워크스페이스 파일들
        private java.util.Map<String, String> workspaceFiles;
        
        // 전체 통계
        private ExecutionStatistics statistics;
    }
    
    /**
     * 명령어 실행 기록
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandExecutionRecord {
        private String command;
        private String output;
        private Integer exitCode;
        private Long durationMs;
        private String workingDirectory;
        private LocalDateTime executedAt;
        private String commandType;
        private Boolean isSignificant;
        private Integer stepNumber;
    }
    
    /**
     * 리소스 사용량 메트릭
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUsageMetrics {
        private Double averageCpuUsage;
        private Double maxCpuUsage;
        private Double averageMemoryUsage;
        private Double maxMemoryUsage;
        private Long totalExecutionTime;
        private Integer commandSuccessCount;
        private Integer commandFailureCount;
        private List<String> topErrorMessages;
    }
    
    /**
     * 세션 메타데이터
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionMetadata {
        private String currentWorkingDirectory;
        private java.util.Map<String, String> environmentVariables;
        private List<String> completedSteps;
        private String currentStep;
        private Integer totalProgressPercent;
        private LocalDateTime sessionStarted;
        private LocalDateTime lastActivity;
    }
    
    /**
     * 실행 통계
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStatistics {
        private Integer totalCommands;
        private Integer successfulCommands;
        private Integer failedCommands;
        private Double successRate;
        private Integer totalFiles;
        private Long totalExecutionTimeMs;
        private LocalDateTime evaluationRequestedAt;
    }
}