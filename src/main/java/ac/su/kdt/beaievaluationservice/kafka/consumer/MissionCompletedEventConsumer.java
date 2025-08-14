package ac.su.kdt.beaievaluationservice.kafka.consumer;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// Kafka에서 mission.completed 이벤트를 수신하고, 해당 이벤트를 처리하여
// AI 평가 프로세스를 시작하는 컴포넌트
// 비동기적으로 평가를 처리하며, 이벤트 유효성을 검증한 후 EvaluationService를 통해 평가 작업을 수행
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCompletedEventConsumer {
    
    private final EvaluationService evaluationService;
    
    @KafkaListener(
        topics = "mission.completed", 
        groupId = "ai-evaluation-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleMissionCompletedEvent(
            @Payload MissionCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("=== KAFKA EVENT RECEIVED ===");
        log.info("Topic: {}, Partition: {}, Offset: {}", topic, partition, offset);
        log.info("MissionAttemptId: {}", event != null ? event.getMissionAttemptId() : "null");
        log.info("UserId: {}, MissionId: {}", 
                event != null ? event.getUserId() : "null", 
                event != null ? event.getMissionId() : "null");

        try {
            if (isValidEvent(event)) {
                log.info("=== STARTING AI EVALUATION ===");
                log.info("Processing evaluation for mission attempt: {}", event.getMissionAttemptId());
                
                evaluationService.processEvaluation(event);
                acknowledgment.acknowledge();
                
                log.info("=== EVALUATION COMPLETED SUCCESSFULLY ===");
                log.info("Mission attempt {} evaluation finished and acknowledged", event.getMissionAttemptId());
            } else {
                log.warn("=== INVALID EVENT RECEIVED ===");
                log.warn("Event validation failed: {}", event);
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("=== EVALUATION FAILED ===");
            log.error("Error processing mission completed event for missionAttemptId: {}", 
                    event != null ? event.getMissionAttemptId() : "null", e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isValidEvent(MissionCompletedEvent event) {
        return event != null 
            && event.getMissionAttemptId() != null 
            && !event.getMissionAttemptId().trim().isEmpty()
            && event.getCode() != null 
            && !event.getCode().trim().isEmpty()
            && event.getUserId() != null
            && event.getMissionId() != null;
    }
}