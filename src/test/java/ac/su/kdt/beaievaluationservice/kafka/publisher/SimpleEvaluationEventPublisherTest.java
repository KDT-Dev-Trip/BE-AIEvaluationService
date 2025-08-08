package ac.su.kdt.beaievaluationservice.kafka.publisher;

import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("간단한 EvaluationEventPublisher 테스트")
@ExtendWith(MockitoExtension.class)
class SimpleEvaluationEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private EvaluationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new KafkaEvaluationEventPublisher(kafkaTemplate, objectMapper);
        // 토픽 이름 설정
        ReflectionTestUtils.setField(eventPublisher, "evaluationCompletedTopic", "evaluation.completed");
    }

    @Test
    @DisplayName("최소 필수 필드로 이벤트 발행")
    void publishEvaluationCompleted_MinimalFields() throws Exception {
        // Given
        EvaluationCompletedEvent event = EvaluationCompletedEvent.builder()
                .missionAttemptId("mission-123")
                .userId("user-456")
                .evaluationStatus("COMPLETED")
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        eventPublisher.publishEvaluationCompleted(event);

        // Then
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("평가 실패 이벤트 발행")
    void publishEvaluationFailed_Simple() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        eventPublisher.publishEvaluationFailed("mission-123", "user-456", "error occurred");

        // Then
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }
}