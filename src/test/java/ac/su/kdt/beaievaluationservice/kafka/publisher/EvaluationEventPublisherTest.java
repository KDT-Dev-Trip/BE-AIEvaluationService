package ac.su.kdt.beaievaluationservice.kafka.publisher;

import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EvaluationEventPublisher 테스트")
@ExtendWith(MockitoExtension.class)
class EvaluationEventPublisherTest {

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
    @DisplayName("평가 완료 이벤트 발행 성공")
    void publishEvaluationCompleted_Success() throws Exception {
        // Given
        EvaluationCompletedEvent event = EvaluationCompletedEvent.builder()
                .missionAttemptId("mission-123")
                .userId("user-456")
                .missionId("mission-789")
                .missionTitle("Java Spring Boot API 구현")
                .missionType("backend")
                .evaluationId(100L)
                .overallScore(85)
                .codeQualityScore(90)
                .securityScore(80)
                .styleScore(85)
                .performanceGrade("GOOD")
                .feedbackSummary("전체적으로 양호한 코드품질을 보입니다.")
                .aiModelVersion("gemini-1.5-pro")
                .evaluationStatus("COMPLETED")
                .completedAt(LocalDateTime.now())
                .processingTimeMs(5000L)
                .hasCpuIssues(false)
                .hasMemoryIssues(false)
                .hasResponseTimeIssues(true)
                .performanceSummary("응답 시간이 지연되고 있습니다.")
                .build();

        String eventJson = "{\"mission_attempt_id\":\"mission-123\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);

        // When
        eventPublisher.publishEvaluationCompleted(event);

        // Then
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> sentRecord = recordCaptor.getValue();
        assertThat(sentRecord.topic()).isEqualTo("evaluation.completed");
        assertThat(sentRecord.key()).isEqualTo("mission-123"); // missionAttemptId를 키로 사용
        assertThat(sentRecord.value()).isEqualTo(eventJson);
    }

    @Test
    @DisplayName("평가 완료 이벤트 발행 시 JSON 직렬화 실패 처리")
    void publishEvaluationCompleted_JsonSerializationFailure() throws Exception {
        // Given
        EvaluationCompletedEvent event = EvaluationCompletedEvent.builder()
                .missionAttemptId("mission-123")
                .userId("user-456")
                .evaluationStatus("COMPLETED")
                .build();

        when(objectMapper.writeValueAsString(event))
            .thenThrow(new RuntimeException("JSON serialization failed"));

        // When & Then
        assertThatThrownBy(() -> eventPublisher.publishEvaluationCompleted(event))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to publish evaluation completed event");
    }

    @Test
    @DisplayName("평가 실패 이벤트 발행 성공")
    void publishEvaluationFailed_Success() throws Exception {
        // Given
        String missionAttemptId = "mission-123";
        String userId = "user-456";
        String errorMessage = "Gemini API rate limit exceeded";

        ArgumentCaptor<EvaluationCompletedEvent> eventCaptor = 
            ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        String eventJson = "{\"mission_attempt_id\":\"mission-123\",\"evaluation_status\":\"FAILED\"}";
        when(objectMapper.writeValueAsString(eventCaptor.capture())).thenReturn(eventJson);

        // When
        eventPublisher.publishEvaluationFailed(missionAttemptId, userId, errorMessage);

        // Then
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        // 이벤트 검증
        EvaluationCompletedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getMissionAttemptId()).isEqualTo(missionAttemptId);
        assertThat(capturedEvent.getUserId()).isEqualTo(userId);
        assertThat(capturedEvent.getEvaluationStatus()).isEqualTo("FAILED");
        assertThat(capturedEvent.getFeedbackSummary()).contains(errorMessage);
        assertThat(capturedEvent.getCompletedAt()).isNotNull();

        // Kafka 메시지 검증
        ProducerRecord<String, String> sentRecord = recordCaptor.getValue();
        assertThat(sentRecord.topic()).isEqualTo("evaluation.completed");
        assertThat(sentRecord.key()).isEqualTo(missionAttemptId);
        assertThat(sentRecord.value()).isEqualTo(eventJson);
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 예외 처리")
    void publishEvaluationCompleted_KafkaFailure() throws Exception {
        // Given
        EvaluationCompletedEvent event = EvaluationCompletedEvent.builder()
                .missionAttemptId("mission-123")
                .userId("user-456")
                .evaluationStatus("COMPLETED")
                .build();

        String eventJson = "{\"mission_attempt_id\":\"mission-123\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);
        doThrow(new RuntimeException("Kafka broker unavailable"))
            .when(kafkaTemplate).send(any(ProducerRecord.class));

        // When & Then
        assertThatThrownBy(() -> eventPublisher.publishEvaluationCompleted(event))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to publish evaluation completed event");
    }

    @Test
    @DisplayName("이벤트 메타데이터 검증")
    void publishEvaluationCompleted_EventMetadata() throws Exception {
        // Given
        LocalDateTime now = LocalDateTime.now();
        EvaluationCompletedEvent event = EvaluationCompletedEvent.builder()
                .missionAttemptId("mission-123")
                .userId("user-456")
                .evaluationStatus("COMPLETED")
                .completedAt(now)
                .processingTimeMs(3500L)
                .aiModelVersion("gemini-1.5-pro")
                .build();

        String eventJson = "{\"completed_at\":\"2023-01-01T10:00:00\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);

        // When
        eventPublisher.publishEvaluationCompleted(event);

        // Then
        verify(kafkaTemplate).send(any(ProducerRecord.class));
        verify(objectMapper).writeValueAsString(event);
    }

    @Test
    @DisplayName("성능 분석 결과 포함 이벤트 발행")
    void publishEvaluationCompleted_WithPerformanceAnalysis() throws Exception {
        // Given
        EvaluationCompletedEvent event = EvaluationCompletedEvent.builder()
                .missionAttemptId("mission-123")
                .userId("user-456")
                .evaluationStatus("COMPLETED")
                .performanceGrade("FAIR")
                .hasCpuIssues(true)
                .hasMemoryIssues(false)
                .hasResponseTimeIssues(true)
                .performanceSummary("CPU 사용률이 높습니다. 응답 시간이 지연되고 있습니다.")
                .build();

        String eventJson = "{\"performance_grade\":\"FAIR\"}";
        when(objectMapper.writeValueAsString(event)).thenReturn(eventJson);

        // When
        eventPublisher.publishEvaluationCompleted(event);

        // Then
        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor = 
            ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> sentRecord = recordCaptor.getValue();
        assertThat(sentRecord.topic()).isEqualTo("evaluation.completed");
        assertThat(sentRecord.value()).isEqualTo(eventJson);
    }

    @Test
    @DisplayName("빈 문자열 파라미터 처리")
    void publishEvaluationFailed_EmptyParameters() throws Exception {
        // Given
        String eventJson = "{\"evaluation_status\":\"FAILED\"}";
        when(objectMapper.writeValueAsString(any(EvaluationCompletedEvent.class)))
            .thenReturn(eventJson);

        // When
        eventPublisher.publishEvaluationFailed("", "", "");

        // Then
        ArgumentCaptor<EvaluationCompletedEvent> eventCaptor = 
            ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        EvaluationCompletedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getMissionAttemptId()).isEmpty();
        assertThat(capturedEvent.getUserId()).isEmpty();
        assertThat(capturedEvent.getEvaluationStatus()).isEqualTo("FAILED");
    }
}