package ac.su.kdt.beaievaluationservice.kafka.consumer;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionTempSaveEvent;
import ac.su.kdt.beaievaluationservice.service.MissionTempSaveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionTempSaveEventConsumer 테스트")
class MissionTempSaveEventConsumerTest {

    @Mock
    private MissionTempSaveService missionTempSaveService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private MissionTempSaveEventConsumer consumer;

    private String testEventJson;
    private MissionTempSaveEvent testEvent;

    @BeforeEach
    void setUp() throws Exception {
        testEvent = createTestTempSaveEvent();
        testEventJson = """
            {
                "eventType": "MISSION_TEMP_SAVE",
                "userId": "user-123",
                "missionId": "mission-456",
                "missionAttemptId": "attempt-123",
                "missionType": "JavaScript",
                "missionTitle": "JavaScript 기초 실습",
                "tempCode": "console.log('temp save');",
                "savedAt": "2025-08-08T10:00:00",
                "saveCount": 1,
                "saveReason": "manual_save"
            }
            """;
    }

    @Test
    @DisplayName("임시 저장 이벤트 처리 - 성공")
    void handleMissionTempSaveEvent_Success() throws Exception {
        // Given
        when(objectMapper.readValue(testEventJson, MissionTempSaveEvent.class)).thenReturn(testEvent);
        doNothing().when(missionTempSaveService).processTempSave(any(MissionTempSaveEvent.class));

        // When
        consumer.handleMissionTempSaveEvent(testEventJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then
        ArgumentCaptor<MissionTempSaveEvent> eventCaptor = ArgumentCaptor.forClass(MissionTempSaveEvent.class);
        verify(missionTempSaveService).processTempSave(eventCaptor.capture());
        verify(acknowledgment).acknowledge();

        MissionTempSaveEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getUserId()).isEqualTo("user-123");
        assertThat(capturedEvent.getMissionAttemptId()).isEqualTo("attempt-123");
        assertThat(capturedEvent.getSaveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("JSON 파싱 실패 시 예외 처리")
    void handleMissionTempSaveEvent_JsonParsingFailed() throws Exception {
        // Given
        String invalidJson = "invalid json";
        when(objectMapper.readValue(invalidJson, MissionTempSaveEvent.class))
            .thenThrow(new RuntimeException("JSON parsing failed"));

        // When
        consumer.handleMissionTempSaveEvent(invalidJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then
        verify(missionTempSaveService, never()).processTempSave(any());
        verify(acknowledgment).acknowledge(); // 실패해도 acknowledge (재처리 방지)
    }

    @Test
    @DisplayName("서비스 처리 실패 시 예외 처리")
    void handleMissionTempSaveEvent_ServiceFailed() throws Exception {
        // Given
        when(objectMapper.readValue(testEventJson, MissionTempSaveEvent.class)).thenReturn(testEvent);
        doThrow(new RuntimeException("Service processing failed"))
            .when(missionTempSaveService).processTempSave(any(MissionTempSaveEvent.class));

        // When
        consumer.handleMissionTempSaveEvent(testEventJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then
        verify(missionTempSaveService).processTempSave(any(MissionTempSaveEvent.class));
        verify(acknowledgment).acknowledge(); // 실패해도 acknowledge
    }

    @Test
    @DisplayName("이벤트 유효성 검증 실패 - UserId 누락")
    void handleMissionTempSaveEvent_InvalidEvent_MissingUserId() throws Exception {
        // Given
        MissionTempSaveEvent invalidEvent = createTestTempSaveEvent();
        invalidEvent.setUserId(null);

        when(objectMapper.readValue(anyString(), eq(MissionTempSaveEvent.class))).thenReturn(invalidEvent);

        // When
        consumer.handleMissionTempSaveEvent(testEventJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then
        verify(missionTempSaveService, never()).processTempSave(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("이벤트 유효성 검증 실패 - MissionAttemptId 누락")
    void handleMissionTempSaveEvent_InvalidEvent_MissingMissionAttemptId() throws Exception {
        // Given
        MissionTempSaveEvent invalidEvent = createTestTempSaveEvent();
        invalidEvent.setMissionAttemptId("");

        when(objectMapper.readValue(anyString(), eq(MissionTempSaveEvent.class))).thenReturn(invalidEvent);

        // When
        consumer.handleMissionTempSaveEvent(testEventJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then
        verify(missionTempSaveService, never()).processTempSave(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("이벤트 유효성 검증 실패 - SaveCount 무효")
    void handleMissionTempSaveEvent_InvalidEvent_InvalidSaveCount() throws Exception {
        // Given
        MissionTempSaveEvent invalidEvent = createTestTempSaveEvent();
        invalidEvent.setSaveCount(0); // 1보다 작음

        when(objectMapper.readValue(anyString(), eq(MissionTempSaveEvent.class))).thenReturn(invalidEvent);

        // When
        consumer.handleMissionTempSaveEvent(testEventJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then
        verify(missionTempSaveService, never()).processTempSave(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("빈 코드로 임시 저장 - 경고 로그만 출력하고 처리 계속")
    void handleMissionTempSaveEvent_EmptyCode_ProcessWithWarning() throws Exception {
        // Given
        MissionTempSaveEvent eventWithEmptyCode = createTestTempSaveEvent();
        eventWithEmptyCode.setTempCode(""); // 빈 코드

        when(objectMapper.readValue(anyString(), eq(MissionTempSaveEvent.class))).thenReturn(eventWithEmptyCode);

        // When
        consumer.handleMissionTempSaveEvent(testEventJson, "mission.temp.save", 0, 123L, acknowledgment);

        // Then - 경고 로그가 출력되지만 처리는 계속됨
        verify(missionTempSaveService).processTempSave(eventWithEmptyCode);
        verify(acknowledgment).acknowledge();
    }

    // Helper method
    private MissionTempSaveEvent createTestTempSaveEvent() {
        MissionTempSaveEvent event = new MissionTempSaveEvent();
        event.setEventType("MISSION_TEMP_SAVE");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("JavaScript");
        event.setMissionTitle("JavaScript 기초 실습");
        event.setTempCode("console.log('temp save');");
        event.setSavedAt(LocalDateTime.now());
        event.setSaveCount(1);
        event.setSaveReason("manual_save");
        return event;
    }
}