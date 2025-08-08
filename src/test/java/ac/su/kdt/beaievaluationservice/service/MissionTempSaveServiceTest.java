package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionTempSaveEvent;
import ac.su.kdt.beaievaluationservice.repository.MissionTempSaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MissionTempSaveService 테스트")
class MissionTempSaveServiceTest {

    @Mock
    private MissionTempSaveRepository missionTempSaveRepository;

    @InjectMocks
    private MissionTempSaveService missionTempSaveService;

    private MissionTempSaveEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = createTestTempSaveEvent();
    }

    @Test
    @DisplayName("새로운 임시 저장 처리 - 성공")
    void processTempSave_NewSave_Success() {
        // Given
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123")).thenReturn(Optional.empty());
        when(missionTempSaveRepository.save(any(MissionTempSave.class))).thenReturn(createMockTempSave());

        // When
        missionTempSaveService.processTempSave(testEvent);

        // Then
        ArgumentCaptor<MissionTempSave> captor = ArgumentCaptor.forClass(MissionTempSave.class);
        verify(missionTempSaveRepository).save(captor.capture());

        MissionTempSave savedTempSave = captor.getValue();
        assertThat(savedTempSave.getUserId()).isEqualTo("user-123");
        assertThat(savedTempSave.getMissionAttemptId()).isEqualTo("attempt-123");
        assertThat(savedTempSave.getTempCode()).isEqualTo("console.log('temp save');");
        assertThat(savedTempSave.getSaveCount()).isEqualTo(1);
        assertThat(savedTempSave.getSaveStatus()).isEqualTo(MissionTempSave.SaveStatus.TEMP_SAVED);
        assertThat(savedTempSave.getIsFinalCompleted()).isFalse();
    }

    @Test
    @DisplayName("기존 임시 저장 업데이트 - 성공")
    void processTempSave_UpdateExisting_Success() {
        // Given
        MissionTempSave existingTempSave = createMockTempSave();
        existingTempSave.setSaveCount(2);
        existingTempSave.setTempCode("updated code");

        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(existingTempSave));
        when(missionTempSaveRepository.save(any(MissionTempSave.class))).thenReturn(existingTempSave);

        // 업데이트용 이벤트
        testEvent.setSaveCount(3);
        testEvent.setTempCode("console.log('updated temp save');");

        // When
        missionTempSaveService.processTempSave(testEvent);

        // Then
        ArgumentCaptor<MissionTempSave> captor = ArgumentCaptor.forClass(MissionTempSave.class);
        verify(missionTempSaveRepository).save(captor.capture());

        MissionTempSave updatedTempSave = captor.getValue();
        assertThat(updatedTempSave.getTempCode()).isEqualTo("console.log('updated temp save');");
        assertThat(updatedTempSave.getSaveCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 완료된 미션 업데이트 시도 - 무시")
    void processTempSave_AlreadyCompleted_Ignored() {
        // Given
        MissionTempSave completedTempSave = createMockTempSave();
        completedTempSave.setIsFinalCompleted(true);
        completedTempSave.setSaveStatus(MissionTempSave.SaveStatus.FINAL_COMPLETED);

        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(completedTempSave));

        // When
        missionTempSaveService.processTempSave(testEvent);

        // Then - save 호출되지 않음 (업데이트 안함)
        verify(missionTempSaveRepository, never()).save(any(MissionTempSave.class));
    }

    @Test
    @DisplayName("임시 저장 데이터 조회 - 성공")
    void getTempSave_Success() {
        // Given
        MissionTempSave mockTempSave = createMockTempSave();
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(mockTempSave));

        // When
        Optional<MissionTempSave> result = missionTempSaveService.getTempSave("attempt-123");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMissionAttemptId()).isEqualTo("attempt-123");
    }

    @Test
    @DisplayName("미완료 임시 저장 목록 조회")
    void getIncompleteTempSaves_Success() {
        // Given
        List<MissionTempSave> incompleteSaves = Arrays.asList(
            createMockTempSave(),
            createAnotherMockTempSave()
        );
        when(missionTempSaveRepository.findIncompleteByUserId("user-123"))
            .thenReturn(incompleteSaves);

        // When
        List<MissionTempSave> result = missionTempSaveService.getIncompleteTempSaves("user-123");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getIsFinalCompleted()).isFalse();
        assertThat(result.get(1).getIsFinalCompleted()).isFalse();
    }

    @Test
    @DisplayName("최종 완료 상태로 변경 - 성공")
    void markAsCompleted_Success() {
        // Given
        MissionTempSave tempSave = createMockTempSave();
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(tempSave));
        when(missionTempSaveRepository.save(any(MissionTempSave.class))).thenReturn(tempSave);

        // When
        missionTempSaveService.markAsCompleted("attempt-123");

        // Then
        ArgumentCaptor<MissionTempSave> captor = ArgumentCaptor.forClass(MissionTempSave.class);
        verify(missionTempSaveRepository).save(captor.capture());

        MissionTempSave completedTempSave = captor.getValue();
        assertThat(completedTempSave.getIsFinalCompleted()).isTrue();
        assertThat(completedTempSave.getSaveStatus()).isEqualTo(MissionTempSave.SaveStatus.FINAL_COMPLETED);
    }

    @Test
    @DisplayName("존재하지 않는 임시 저장 완료 시도 - 경고 로그")
    void markAsCompleted_NotFound_Warning() {
        // Given
        when(missionTempSaveRepository.findByMissionAttemptId("nonexistent"))
            .thenReturn(Optional.empty());

        // When & Then - 예외 발생하지 않고 정상 처리
        assertThatCode(() -> missionTempSaveService.markAsCompleted("nonexistent"))
            .doesNotThrowAnyException();

        verify(missionTempSaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("임시 저장 통계 조회")
    void getTempSaveStats_Success() {
        // Given
        List<MissionTempSave> userTempSaves = Arrays.asList(
            createCompletedMockTempSave(),
            createMockTempSave(), // incomplete
            createCompletedMockTempSave()
        );
        when(missionTempSaveRepository.findByUserIdOrderByUpdatedAtDesc("user-123"))
            .thenReturn(userTempSaves);

        // When
        MissionTempSaveService.TempSaveStats stats = missionTempSaveService.getTempSaveStats("user-123");

        // Then
        assertThat(stats.totalSaves).isEqualTo(3);
        assertThat(stats.completedSaves).isEqualTo(2);
        assertThat(stats.incompleteSaves).isEqualTo(1);
    }

    @Test
    @DisplayName("임시 저장 삭제")
    void deleteTempSave_Success() {
        // Given
        MissionTempSave tempSave = createMockTempSave();
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(tempSave));

        // When
        missionTempSaveService.deleteTempSave("attempt-123");

        // Then
        verify(missionTempSaveRepository).delete(tempSave);
    }

    // Helper methods
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

    private MissionTempSave createMockTempSave() {
        MissionTempSave tempSave = new MissionTempSave();
        tempSave.setId(1L);
        tempSave.setUserId("user-123");
        tempSave.setMissionId("mission-456");
        tempSave.setMissionAttemptId("attempt-123");
        tempSave.setMissionType("JavaScript");
        tempSave.setMissionTitle("JavaScript 기초 실습");
        tempSave.setTempCode("console.log('temp save');");
        tempSave.setSaveCount(1);
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.TEMP_SAVED);
        tempSave.setIsFinalCompleted(false);
        tempSave.setCreatedAt(LocalDateTime.now());
        tempSave.setUpdatedAt(LocalDateTime.now());
        return tempSave;
    }

    private MissionTempSave createAnotherMockTempSave() {
        MissionTempSave tempSave = new MissionTempSave();
        tempSave.setId(2L);
        tempSave.setUserId("user-123");
        tempSave.setMissionId("mission-789");
        tempSave.setMissionAttemptId("attempt-456");
        tempSave.setMissionType("Python");
        tempSave.setMissionTitle("Python 기초 실습");
        tempSave.setTempCode("print('temp save')");
        tempSave.setSaveCount(2);
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.TEMP_SAVED);
        tempSave.setIsFinalCompleted(false);
        tempSave.setCreatedAt(LocalDateTime.now());
        tempSave.setUpdatedAt(LocalDateTime.now());
        return tempSave;
    }

    private MissionTempSave createCompletedMockTempSave() {
        MissionTempSave tempSave = createMockTempSave();
        tempSave.setIsFinalCompleted(true);
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.FINAL_COMPLETED);
        return tempSave;
    }
}