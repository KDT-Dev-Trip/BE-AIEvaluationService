package ac.su.kdt.beaievaluationservice.entity;

import lombok.Data;
import java.time.LocalDateTime;

// AI 평가 결과 저장 엔티티
// DB 추가 필요
@Data
public class EvaluationResult {
    private String evaluationId;
    private String userId;
    private String missionId;
    private int overallScore;
    private String feedback;
    private String detailedAnalysis;
    private LocalDateTime evaluatedAt;
}