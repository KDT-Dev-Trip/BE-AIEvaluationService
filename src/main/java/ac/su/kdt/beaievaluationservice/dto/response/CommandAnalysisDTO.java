package ac.su.kdt.beaievaluationservice.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommandAnalysisDTO {
    
    // 명령어
    private String command;
    
    // 정답 여부
    private Boolean isCorrect;
    
    // 보안 위험도 (Low, Medium, High)
    private String securityRisk;
    
    // 카테고리
    private String category;
    
    // 효율성 등급 (A, B, C, D, F)
    private String efficiencyGrade;
    
    // 모범사례 점수 (0-100)
    private Integer bestPracticeScore;
    
    // 신뢰도 (0-100)
    private Integer reliabilityScore;
    
    // AI 피드백
    private String aiFeedback;
    
    // 개선 제안
    private List<String> improvementSuggestions;
    
    // 대체 명령어
    private List<String> alternativeCommands;
}