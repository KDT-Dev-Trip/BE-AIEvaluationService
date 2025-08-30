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
import org.springframework.scheduling.annotation.Async;

// Kafka에서 mission.completed 이벤트를 수신하고, 해당 이벤트를 처리하여
// AI 평가 프로세스를 시작하는 컴포넌트
// 비동기적으로 평가를 처리하며, 이벤트 유효성을 검증한 후 EvaluationService를 통해 평가 작업을 수행
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionCompletedEventConsumer {
    
    private final EvaluationService evaluationService;
    
    @KafkaListener(
        topics = "${kafka.topics.mission-completed}", 
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
                log.info("=== STARTING ASYNC AI EVALUATION ===");
                log.info("Processing evaluation for mission attempt: {}", event.getMissionAttemptId());
                
                // 비동기 처리로 즉시 ACK하여 Consumer 응답성 향상
                evaluationService.processEvaluationAsync(event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.error("=== ASYNC EVALUATION FAILED ===");
                            log.error("Async evaluation failed for missionAttemptId: {}", 
                                event.getMissionAttemptId(), throwable);
                        } else {
                            log.info("=== ASYNC EVALUATION COMPLETED ===");
                            log.info("Async evaluation completed for missionAttemptId: {}", 
                                event.getMissionAttemptId());
                        }
                    });
                
                // 즉시 ACK - Consumer가 다음 메시지를 처리할 수 있도록
                acknowledgment.acknowledge();
                log.info("=== MESSAGE ACKNOWLEDGED (ASYNC PROCESSING) ===");
                log.info("Message acknowledged, evaluation processing asynchronously for: {}", 
                    event.getMissionAttemptId());
                    
            } else {
                log.warn("=== INVALID EVENT RECEIVED ===");
                log.warn("Event validation failed: {}", event);
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            log.error("=== CONSUMER ERROR ===");
            log.error("Error in consumer handling for missionAttemptId: {}", 
                    event != null ? event.getMissionAttemptId() : "null", e);
            acknowledgment.acknowledge(); // 실패한 메시지도 ACK하여 무한 재처리 방지
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