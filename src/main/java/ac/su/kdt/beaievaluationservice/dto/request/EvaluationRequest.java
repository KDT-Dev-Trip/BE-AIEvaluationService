package ac.su.kdt.beaievaluationservice.dto.request;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

// AI 평가 요청 DTO (미션 완료) - S3 통합 버전
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
    
    // === S3 통합 관련 필드들 ===
    
    // 미션 목표 (평가 기준)
    private String missionObjective;
    
    // 미션 체크리스트 (완료해야 할 항목들)
    private List<String> checklist;
    
    // S3 저장소 주소 (실행 로그와 메트릭이 저장된 위치)
    private String s3StorageUrl;
    
    // Pre-signed URL (5분 만료, AI가 직접 S3 데이터를 읽기 위함)
    private String s3PreSignedUrl;
    
    // 실행 통계 (명령어 성공/실패 횟수, 리소스 사용량 등)
    private MissionCompletedEvent.SimpleStatistics statistics;
    
    // === 기존 필드들 (하위 호환성) ===
    
    // Prometheus 메트릭 수집을 위한 시간 범위 (선택적, 더 이상 사용되지 않음)
    @Deprecated
    private LocalDateTime startAt;
    
    @Deprecated
    private LocalDateTime endAt;
}