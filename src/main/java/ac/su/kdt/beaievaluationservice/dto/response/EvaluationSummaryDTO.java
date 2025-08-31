package ac.su.kdt.beaievaluationservice.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationSummaryDTO {
    
    // 총 평가 건수
    private Long totalEvaluations;
    
    // 완료된 평가 건수
    private Long completedEvaluations;
    
    // 획득한 스탬프 수
    private Integer totalStampsEarned;
}