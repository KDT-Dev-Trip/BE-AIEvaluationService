package ac.su.kdt.beaievaluationservice.kafka.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

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
    
    // Prometheus 메트릭 수집을 위한 시간 구간
    private LocalDateTime startAt; // 미션 시작 시간
    private LocalDateTime endAt;   // 미션 종료 시간
}