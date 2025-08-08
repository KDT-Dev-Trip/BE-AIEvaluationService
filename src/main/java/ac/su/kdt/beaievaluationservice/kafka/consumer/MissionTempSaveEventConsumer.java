package ac.su.kdt.beaievaluationservice.kafka.consumer;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionTempSaveEvent;
import ac.su.kdt.beaievaluationservice.service.MissionTempSaveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// 미션 임시 저장 이벤트 컨슈머
// 사용자가 미션 진행 중 임시 저장 버튼을 클릭했을 때 발생하는 이벤트를 처리
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionTempSaveEventConsumer {

    private final MissionTempSaveService missionTempSaveService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.topic.mission-temp-save:mission.temp.save}",
        groupId = "${kafka.consumer.group-id:ai-evaluation-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleMissionTempSaveEvent(
            @Payload String eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received mission temp save event from topic: {}, partition: {}, offset: {}", 
                topic, partition, offset);

        try {
            // JSON 문자열을 MissionTempSaveEvent 객체로 변환
            MissionTempSaveEvent event = objectMapper.readValue(eventData, MissionTempSaveEvent.class);
            
            log.info("Processing temp save event for missionAttemptId: {}, userId: {}, saveCount: {}", 
                    event.getMissionAttemptId(), event.getUserId(), event.getSaveCount());

            // 이벤트 유효성 검증
            validateEvent(event);

            // 임시 저장 처리
            missionTempSaveService.processTempSave(event);

            // 메시지 처리 완료 확인
            acknowledgment.acknowledge();
            
            log.info("Successfully processed temp save event for missionAttemptId: {}", 
                    event.getMissionAttemptId());

        } catch (Exception e) {
            log.error("Failed to process mission temp save event: {}", eventData, e);
            // 임시 저장 실패 시에도 acknowledge (재처리 방지)
            acknowledgment.acknowledge();
        }
    }

    /**
     * 임시 저장 이벤트 유효성 검증
     */
    private void validateEvent(MissionTempSaveEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("UserId는 필수값입니다.");
        }
        
        if (event.getMissionId() == null || event.getMissionId().trim().isEmpty()) {
            throw new IllegalArgumentException("MissionId는 필수값입니다.");
        }
        
        if (event.getMissionAttemptId() == null || event.getMissionAttemptId().trim().isEmpty()) {
            throw new IllegalArgumentException("MissionAttemptId는 필수값입니다.");
        }
        
        if (event.getTempCode() == null || event.getTempCode().trim().isEmpty()) {
            log.warn("Empty temp code received for missionAttemptId: {}", event.getMissionAttemptId());
        }
        
        if (event.getSaveCount() == null || event.getSaveCount() < 1) {
            throw new IllegalArgumentException("SaveCount는 1 이상이어야 합니다.");
        }

        log.debug("Event validation passed for missionAttemptId: {}", event.getMissionAttemptId());
    }
}