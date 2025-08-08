package ac.su.kdt.beaievaluationservice.kafka.event;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

// 미션 임시 저장 이벤트
// 사용자가 미션 진행 중 저장 버튼을 클릭했을 때 발생하는 이벤트
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MissionTempSaveEvent {
    
    private String eventType; // "MISSION_TEMP_SAVE"
    private String userId;
    private String missionId;
    private String missionAttemptId;
    private String missionType;
    private String missionTitle;
    
    // 현재 작성 중인 코드 (임시 저장)
    private String tempCode;
    
    // 저장 시점
    private LocalDateTime savedAt;
    
    // 저장 횟수 (동일 미션에서 몇 번째 저장인지)
    private Integer saveCount;
    
    // 메타데이터
    private String saveReason; // "auto_save", "manual_save", "before_final"
}