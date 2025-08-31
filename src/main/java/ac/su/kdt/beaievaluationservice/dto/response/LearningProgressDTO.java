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
public class LearningProgressDTO {
    
    // 완료 미션 수
    private Integer completedMissions;
    
    // 평균 점수
    private Double averageScore;
    
    // 획득 스탬프 수
    private Integer totalStamps;
    
    // 최근 성과 트렌드 (최근 10개 평가의 점수 리스트)
    private List<Integer> recentScoreTrend;
    
    // 난이도별 성과
    private List<DifficultyPerformanceDTO> difficultyPerformances;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DifficultyPerformanceDTO {
        private String difficulty; // 초급, 중급, 고급
        private Integer completedCount;  // 완료 수
        private Double averageScore;     // 평균 점수
        private Double successRate;      // 성공률
    }
}