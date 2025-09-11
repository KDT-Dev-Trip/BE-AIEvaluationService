package ac.su.kdt.beaievaluationservice.kafka.publisher;

import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

// Kafka를 통한 평가 이벤트 발행 구현체
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEvaluationEventPublisher implements EvaluationEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topic.evaluation.completed:evaluation.completed}")
    private String evaluationCompletedTopic;

    @Override
    public void publishEvaluationCompleted(EvaluationCompletedEvent event) {
        try {
            log.info("Publishing evaluation completed event for missionAttemptId: {}", 
                    event.getMissionAttemptId());
            
            // User Service의 EvaluationEventListener가 기대하는 형식으로 이벤트 변환
            Map<String, Object> eventData = Map.of(
                "eventType", "evaluation.completed",
                "missionAttemptId", event.getMissionAttemptId(),
                "userId", event.getUserId() != null ? event.getUserId() : "",
                "evaluationStatus", event.getEvaluationStatus() != null ? event.getEvaluationStatus() : "COMPLETED",
                "overallScore", event.getOverallScore() != null ? event.getOverallScore() : 0,
                "feedbackSummary", event.getFeedbackSummary() != null ? event.getFeedbackSummary() : "",
                "missionTitle", event.getMissionTitle() != null ? event.getMissionTitle() : "미션",
                "missionCategory", event.getMissionCategory() != null ? event.getMissionCategory() : "UNKNOWN",
                "missionDifficulty", event.getMissionDifficulty() != null ? event.getMissionDifficulty() : "BEGINNER",
                "completedAt", event.getCompletedAt() != null ? event.getCompletedAt().toString() : LocalDateTime.now().toString()
            );
            
            // 이벤트를 JSON으로 직렬화
            String eventJson = objectMapper.writeValueAsString(eventData);
            
            // evaluation-events 토픽으로 발행 (User Service가 수신)
            ProducerRecord<String, String> record = new ProducerRecord<>(
                "evaluation-events",
                event.getMissionAttemptId(), // 키: 같은 미션은 같은 파티션으로
                eventJson
            );
            
            // 메시지 헤더에 메타데이터 추가
            record.headers().add("event_type", "evaluation.completed".getBytes());
            if (event.getUserId() != null) {
                record.headers().add("user_id", event.getUserId().getBytes());
            }
            if (event.getEvaluationStatus() != null) {
                record.headers().add("evaluation_status", event.getEvaluationStatus().getBytes());
            }
            
            // Kafka로 전송
            kafkaTemplate.send(record);
            
            log.info("Successfully published evaluation completed event to evaluation-events topic: missionAttemptId={}, status={}", 
                    event.getMissionAttemptId(), event.getEvaluationStatus());
                    
        } catch (Exception e) {
            log.error("Failed to publish evaluation completed event for missionAttemptId: {}", 
                     event.getMissionAttemptId(), e);
            throw new RuntimeException("Failed to publish evaluation completed event", e);
        }
    }

    @Override
    public void publishEvaluationFailed(String missionAttemptId, String userId, String errorMessage) {
        log.info("Publishing evaluation failed event for missionAttemptId: {}", missionAttemptId);
        
        // 실패 이벤트 생성
        EvaluationCompletedEvent failedEvent = EvaluationCompletedEvent.builder()
                .missionAttemptId(missionAttemptId)
                .userId(userId)
                .evaluationStatus("FAILED")
                .feedbackSummary("평가 처리 중 오류가 발생했습니다: " + errorMessage)
                .completedAt(LocalDateTime.now())
                .overallScore(0) // 실패 시 점수는 0
                .build();
        
        // 평가 완료 이벤트로 발행 (상태만 FAILED)
        publishEvaluationCompleted(failedEvent);
    }
}