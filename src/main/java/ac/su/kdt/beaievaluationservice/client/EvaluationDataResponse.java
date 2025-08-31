package ac.su.kdt.beaievaluationservice.client;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationDataResponse {
    
    private String attemptId;
    private String userId;
    private String missionId;
    private String missionTitle;
    private String missionType;
    
    // 명령어 실행 데이터
    private List<CommandExecutionData> commandHistory;
    private List<CommandExecutionData> significantCommands;
    private List<CommandExecutionData> failedCommands;
    
    // 리소스 사용량 통계
    private ResourceUsageStatistics resourceUsage;
    
    // 세션 정보
    private SessionInfo sessionInfo;
    
    // 워크스페이스 파일들 (중요 파일만)
    private Map<String, String> workspaceFiles;
    
    // 전체 통계
    private EvaluationStatistics statistics;
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CommandExecutionData {
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
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourceUsageStatistics {
        private Double averageCpuUsage;
        private Double maxCpuUsage;
        private Double averageMemoryUsage;
        private Double maxMemoryUsage;
        private Long totalExecutionTime;
        private Integer commandSuccessCount;
        private Integer commandFailureCount;
        private List<String> topErrorMessages;
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SessionInfo {
        private String currentWorkingDirectory;
        private Map<String, String> environmentVariables;
        private List<String> completedSteps;
        private String currentStep;
        private Integer totalProgressPercent;
        private LocalDateTime sessionStarted;
        private LocalDateTime lastActivity;
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvaluationStatistics {
        private Integer totalCommands;
        private Integer successfulCommands;
        private Integer failedCommands;
        private Double successRate;
        private Integer totalFiles;
        private Long totalExecutionTimeMs;
        private LocalDateTime evaluationRequestedAt;
    }
}