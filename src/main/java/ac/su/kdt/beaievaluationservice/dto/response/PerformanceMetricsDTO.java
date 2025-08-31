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
public class PerformanceMetricsDTO {
    
    // 미션 타입별 성과
    private List<MissionTypePerformanceDTO> missionTypePerformances;
    
    // 개별 미션별 성과
    private List<IndividualMissionPerformanceDTO> individualMissionPerformances;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MissionTypePerformanceDTO {
        private String missionType;
        private Integer totalAttempts;        // 총 시도
        private Double averageSuccessRate;    // 평균 성공률
        private Double averageScore;          // 평균 점수
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IndividualMissionPerformanceDTO {
        private String missionId;
        private String missionTitle;
        private Double successRate;           // 성공률
        private Integer attemptCount;         // 시도 횟수
        private Double averageCompletionTime; // 평균 소요 시간 (분)
        private String difficulty;            // 난이도 (초급, 중급, 고급)
    }
}