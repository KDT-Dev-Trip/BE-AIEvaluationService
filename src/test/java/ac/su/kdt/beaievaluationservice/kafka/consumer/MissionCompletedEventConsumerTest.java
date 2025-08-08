package ac.su.kdt.beaievaluationservice.kafka.consumer;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.service.EvaluationService;
import ac.su.kdt.beaievaluationservice.testutil.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// MissionCompletedEventConsumer의 단위 테스트를 작성합니다.
@ExtendWith(MockitoExtension.class)
@DisplayName("MissionCompletedEventConsumer 단위 테스트")
class MissionCompletedEventConsumerTest {

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private MissionCompletedEventConsumer missionCompletedEventConsumer;

    private MissionCompletedEvent validEvent;

    @BeforeEach
    void setUp() {
        validEvent = TestDataBuilder.missionCompletedEvent().build();
    }

    @Test
    @DisplayName("유효한 이벤트 처리 - 정상 케이스")
    void handleMissionCompletedEvent_ValidEvent_Success() {
        // Given - 유효한 이벤트

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            validEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService).processEvaluationAsync(validEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("잘못된 이벤트 처리 - null 이벤트")
    void handleMissionCompletedEvent_NullEvent() {
        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            null, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge(); // 잘못된 이벤트는 acknowledge하여 재처리 방지
    }

    @Test
    @DisplayName("잘못된 이벤트 처리 - missionAttemptId 누락")
    void handleMissionCompletedEvent_MissingMissionAttemptId() {
        // Given
        MissionCompletedEvent invalidEvent = TestDataBuilder.missionCompletedEvent()
            .missionAttemptId(null)
            .build();

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            invalidEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("잘못된 이벤트 처리 - 빈 코드")
    void handleMissionCompletedEvent_EmptyCode() {
        // Given
        MissionCompletedEvent invalidEvent = TestDataBuilder.missionCompletedEvent()
            .code("")
            .build();

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            invalidEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("잘못된 이벤트 처리 - userId 누락")
    void handleMissionCompletedEvent_MissingUserId() {
        // Given
        MissionCompletedEvent invalidEvent = TestDataBuilder.missionCompletedEvent()
            .userId(null)
            .build();

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            invalidEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("잘못된 이벤트 처리 - missionId 누락")
    void handleMissionCompletedEvent_MissingMissionId() {
        // Given
        MissionCompletedEvent invalidEvent = TestDataBuilder.missionCompletedEvent()
            .missionId(null)
            .build();

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            invalidEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("평가 서비스 예외 발생 - acknowledge 호출되지 않음")
    void handleMissionCompletedEvent_EvaluationServiceException() {
        // Given
        doThrow(new RuntimeException("Evaluation service error"))
            .when(evaluationService).processEvaluationAsync(any());

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            validEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService).processEvaluationAsync(validEvent);
        verify(acknowledgment, never()).acknowledge(); // 예외 발생 시 acknowledge하지 않음
    }

    @Test
    @DisplayName("공백 문자열 처리 - missionAttemptId")
    void handleMissionCompletedEvent_WhitespaceOnlyMissionAttemptId() {
        // Given
        MissionCompletedEvent invalidEvent = TestDataBuilder.missionCompletedEvent()
            .missionAttemptId("   ")
            .build();

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            invalidEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("공백 문자열 처리 - code")
    void handleMissionCompletedEvent_WhitespaceOnlyCode() {
        // Given
        MissionCompletedEvent invalidEvent = TestDataBuilder.missionCompletedEvent()
            .code("   \n\t   ")
            .build();

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            invalidEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService, never()).processEvaluationAsync(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("최소한의 유효한 이벤트 처리")
    void handleMissionCompletedEvent_MinimalValidEvent() {
        // Given
        MissionCompletedEvent minimalEvent = new MissionCompletedEvent();
        minimalEvent.setMissionAttemptId("minimal-attempt-001");
        minimalEvent.setCode("FROM ubuntu");
        minimalEvent.setUserId("user");
        minimalEvent.setMissionId("mission");

        // When
        missionCompletedEventConsumer.handleMissionCompletedEvent(
            minimalEvent, "mission.completed", 0, 12345L, acknowledgment);

        // Then
        verify(evaluationService).processEvaluationAsync(minimalEvent);
        verify(acknowledgment).acknowledge();
    }
}
