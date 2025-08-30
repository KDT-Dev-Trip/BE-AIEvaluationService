package ac.su.kdt.beaievaluationservice.dto.response;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

// AI 평가 응답 DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationResponse {
    
    private Long evaluationId;
    private String missionAttemptId;
    private String userId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private String aiModelVersion;
    
    // 평가 결과 (COMPLETED 상태에서만)
    private Integer overallScore;
    private Integer codeQualityScore;
    private Integer securityScore;
    private Integer styleScore;
    private String feedback;
    
    // 성능 분석 결과 (있는 경우에만)
    private String performanceGrade;
    private Boolean hasCpuIssues;
    private Boolean hasMemoryIssues;
    private Boolean hasResponseTimeIssues;
    private String performanceSummary;
    
    // 임시 저장 관련 정보
    private Boolean hadTempSave;
    private Integer tempSaveCount;
    
    // 처리 시간
    private Long processingTimeMs;
    
    // 새로운 상세 평가 지표들
    private String securityRiskLevel; // Low, Medium, High
    private String efficiencyGrade; // A, B, C, D, F
    private Integer bestPracticeScore; // 0-100
    private Integer reliabilityScore; // 0-100
    
    // 명령어 통계
    private Integer totalCommandCount;
    private Integer significantCommandCount;
    private Integer errorCommandCount;
    
    // 획득 스탬프
    private Integer stampsEarned;
    
    // 미션 정보
    private String missionTitle;
    private String missionType;
    private String missionDifficulty;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // 에러 정보 (FAILED 상태에서만)
    private String errorMessage;
}