package ac.su.kdt.beaievaluationservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

// AI 평가 요청 DTO (미션 완료)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {
    
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;
    
    @NotBlank(message = "미션 ID는 필수입니다")
    private String missionId;
    
    @NotBlank(message = "미션 시도 ID는 필수입니다")
    private String missionAttemptId;
    
    @NotBlank(message = "미션 타입은 필수입니다")
    private String missionType;
    
    private String missionTitle;
    
    @NotBlank(message = "평가할 코드는 필수입니다")
    private String code;
    
    // Prometheus 메트릭 수집을 위한 시간 범위 (선택적)
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}