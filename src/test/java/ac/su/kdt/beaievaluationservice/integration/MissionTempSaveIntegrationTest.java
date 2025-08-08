package ac.su.kdt.beaievaluationservice.integration;

import ac.su.kdt.beaievaluationservice.analyzer.MetricAnalyzer;
import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricSummaryDto;
import ac.su.kdt.beaievaluationservice.client.PrometheusClient;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionTempSaveEvent;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.repository.MissionTempSaveRepository;
import ac.su.kdt.beaievaluationservice.service.EvaluationService;
import ac.su.kdt.beaievaluationservice.service.GeminiEvaluationService;
import ac.su.kdt.beaievaluationservice.service.MissionTempSaveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("임시 저장 + AI 평가 통합 테스트")
class MissionTempSaveIntegrationTest {

    @Mock
    private AIEvaluationRepository aiEvaluationRepository;
    @Mock
    private EvaluationSummaryRepository evaluationSummaryRepository;
    @Mock
    private EvaluationHistoryRepository evaluationHistoryRepository;
    @Mock
    private MissionTempSaveRepository missionTempSaveRepository;
    @Mock
    private PrometheusClient prometheusClient;
    @Mock
    private MetricAnalyzer metricAnalyzer;
    @Mock
    private GeminiEvaluationService geminiEvaluationService;
    @Mock
    private EvaluationEventPublisher evaluationEventPublisher;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MissionTempSaveService missionTempSaveService;
    
    private EvaluationService evaluationService;

    private MissionTempSaveEvent tempSaveEvent;
    private MissionCompletedEvent completedEvent;
    private EvaluationResultDTO evaluationResult;

    @BeforeEach
    void setUp() {
        tempSaveEvent = createTestTempSaveEvent();
        completedEvent = createTestCompletedEvent();
        evaluationResult = createTestEvaluationResult();
        
        // EvaluationService 수동 생성 (모든 의존성 주입)
        evaluationService = new EvaluationService(
            aiEvaluationRepository,
            evaluationSummaryRepository,
            evaluationHistoryRepository,
            geminiEvaluationService,
            prometheusClient,
            metricAnalyzer,
            evaluationEventPublisher,
            objectMapper,
            missionTempSaveService
        );
    }

    @Test
    @DisplayName("임시 저장 → 최종 완료 → AI 평가 전체 플로우")
    void tempSaveToFinalCompletion_FullFlow() throws Exception {
        // Phase 1: 임시 저장 처리
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123")).thenReturn(Optional.empty());
        when(missionTempSaveRepository.save(any(MissionTempSave.class))).thenReturn(createMockTempSave());

        missionTempSaveService.processTempSave(tempSaveEvent);

        // 임시 저장 확인
        verify(missionTempSaveRepository).save(argThat(tempSave -> {
            assertThat(tempSave.getMissionAttemptId()).isEqualTo("attempt-123");
            assertThat(tempSave.getTempCode()).isEqualTo("console.log('temp code');");
            assertThat(tempSave.getSaveCount()).isEqualTo(1);
            assertThat(tempSave.getSaveStatus()).isEqualTo(MissionTempSave.SaveStatus.TEMP_SAVED);
            assertThat(tempSave.getIsFinalCompleted()).isFalse();
            return true;
        }));

        // Phase 2: 최종 완료 시 임시 저장 데이터 활용
        setupEvaluationMocks();
        
        // 기존 임시 저장 데이터 조회
        MissionTempSave existingTempSave = createMockTempSave();
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(existingTempSave));

        // AI 평가 진행
        evaluationService.processEvaluationAsync(completedEvent);

        // Then - 임시 저장이 최종 완료 상태로 변경됨
        verify(missionTempSaveRepository, times(2)).save(argThat(tempSave -> {
            // 첫 번째 save: 초기 임시 저장
            // 두 번째 save: 최종 완료 상태로 업데이트
            return tempSave.getMissionAttemptId().equals("attempt-123");
        }));

        // AI 평가 프로세스 실행 확인
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class));
        verify(geminiEvaluationService).evaluateCode(anyString(), anyString(), anyString());
        verify(evaluationEventPublisher).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("여러 번 임시 저장 후 최종 완료")
    void multipleTempSaves_ThenFinalCompletion() throws Exception {
        // Phase 1: 첫 번째 임시 저장
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123")).thenReturn(Optional.empty());
        MissionTempSave tempSave1 = createMockTempSave();
        when(missionTempSaveRepository.save(any(MissionTempSave.class))).thenReturn(tempSave1);

        missionTempSaveService.processTempSave(tempSaveEvent);

        // Phase 2: 두 번째 임시 저장 (업데이트)
        MissionTempSaveEvent updateEvent = createTestTempSaveEvent();
        updateEvent.setSaveCount(2);
        updateEvent.setTempCode("console.log('updated code');");

        MissionTempSave existingTempSave = createMockTempSave();
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(existingTempSave));

        missionTempSaveService.processTempSave(updateEvent);

        // Phase 3: 최종 완료
        setupEvaluationMocks();
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(existingTempSave));

        evaluationService.processEvaluationAsync(completedEvent);

        // Then - 총 3번의 save 호출 (초기 저장 + 업데이트 + 최종 완료)
        verify(missionTempSaveRepository, times(3)).save(any(MissionTempSave.class));
    }

    @Test
    @DisplayName("임시 저장 없이 바로 최종 완료")
    void directFinalCompletion_WithoutTempSave() throws Exception {
        // Given - 임시 저장 데이터 없음
        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123")).thenReturn(Optional.empty());
        setupEvaluationMocks();

        // When
        evaluationService.processEvaluationAsync(completedEvent);

        // Then - 임시 저장 관련 처리는 수행되지 않음
        verify(missionTempSaveRepository, never()).save(any());
        
        // 하지만 AI 평가는 정상 진행
        verify(geminiEvaluationService).evaluateCode(anyString(), anyString(), anyString());
        verify(evaluationEventPublisher).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("이미 완료된 임시 저장에 추가 저장 시도")
    void additionalSave_OnCompletedTempSave() {
        // Given - 이미 완료된 임시 저장
        MissionTempSave completedTempSave = createMockTempSave();
        completedTempSave.setIsFinalCompleted(true);
        completedTempSave.setSaveStatus(MissionTempSave.SaveStatus.FINAL_COMPLETED);

        when(missionTempSaveRepository.findByMissionAttemptId("attempt-123"))
            .thenReturn(Optional.of(completedTempSave));

        // When - 추가 임시 저장 시도
        missionTempSaveService.processTempSave(tempSaveEvent);

        // Then - 업데이트되지 않음
        verify(missionTempSaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("임시 저장 통계 조회")
    void getTempSaveStats_VariousStates() {
        // Given
        List<MissionTempSave> userTempSaves = Arrays.asList(
            createCompletedTempSave(),
            createIncompleteTempSave(),
            createCompletedTempSave(),
            createIncompleteTempSave()
        );

        when(missionTempSaveRepository.findByUserIdOrderByUpdatedAtDesc("user-123"))
            .thenReturn(userTempSaves);

        // When
        MissionTempSaveService.TempSaveStats stats = missionTempSaveService.getTempSaveStats("user-123");

        // Then
        assertThat(stats.totalSaves).isEqualTo(4);
        assertThat(stats.completedSaves).isEqualTo(2);
        assertThat(stats.incompleteSaves).isEqualTo(2);
    }

    // Helper methods
    private void setupEvaluationMocks() throws Exception {
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(createMockAIEvaluation());
        
        // Prometheus mocks
        when(prometheusClient.queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(createMockMetricData());
        
        // MetricAnalyzer mock
        when(metricAnalyzer.analyzeMissionPerformance(anyString(), anyString(), any(Map.class)))
            .thenReturn(createMockMetricSummary());
        
        // Gemini API mock
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenReturn(evaluationResult);
        
        // ObjectMapper mock
        when(objectMapper.writeValueAsString(evaluationResult)).thenReturn("{\"overallScore\":85}");
    }

    private MissionTempSaveEvent createTestTempSaveEvent() {
        MissionTempSaveEvent event = new MissionTempSaveEvent();
        event.setEventType("MISSION_TEMP_SAVE");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("JavaScript");
        event.setMissionTitle("JavaScript 기초 실습");
        event.setTempCode("console.log('temp code');");
        event.setSavedAt(LocalDateTime.now());
        event.setSaveCount(1);
        event.setSaveReason("manual_save");
        return event;
    }

    private MissionCompletedEvent createTestCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("JavaScript");
        event.setCode("console.log('final code');");
        event.setMissionTitle("JavaScript 기초 실습");
        event.setCompletedAt(LocalDateTime.now());
        event.setStartAt(LocalDateTime.now().minusHours(1));
        event.setEndAt(LocalDateTime.now());
        return event;
    }

    private EvaluationResultDTO createTestEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(85);
        result.setFeedback("Good code quality");
        return result;
    }

    private MissionTempSave createMockTempSave() {
        MissionTempSave tempSave = new MissionTempSave();
        tempSave.setId(1L);
        tempSave.setUserId("user-123");
        tempSave.setMissionId("mission-456");
        tempSave.setMissionAttemptId("attempt-123");
        tempSave.setMissionType("JavaScript");
        tempSave.setMissionTitle("JavaScript 기초 실습");
        tempSave.setTempCode("console.log('temp code');");
        tempSave.setSaveCount(1);
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.TEMP_SAVED);
        tempSave.setIsFinalCompleted(false);
        tempSave.setCreatedAt(LocalDateTime.now());
        tempSave.setUpdatedAt(LocalDateTime.now());
        return tempSave;
    }

    private MissionTempSave createCompletedTempSave() {
        MissionTempSave tempSave = createMockTempSave();
        tempSave.setId(2L);
        tempSave.setMissionAttemptId("attempt-completed");
        tempSave.setIsFinalCompleted(true);
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.FINAL_COMPLETED);
        return tempSave;
    }

    private MissionTempSave createIncompleteTempSave() {
        MissionTempSave tempSave = createMockTempSave();
        tempSave.setId(3L);
        tempSave.setMissionAttemptId("attempt-incomplete");
        return tempSave;
    }

    private AIEvaluation createMockAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("attempt-123");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        return evaluation;
    }

    private List<MetricDataPoint> createMockMetricData() {
        return Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 50.0),
            new MetricDataPoint(Instant.now(), 55.0)
        );
    }

    private MetricSummaryDto createMockMetricSummary() {
        return MetricSummaryDto.builder()
            .missionAttemptId("attempt-123")
            .userId("user-123")
            .overallGrade(MetricSummaryDto.PerformanceGrade.GOOD)
            .performanceSummary("Good performance")
            .hasCpuIssues(false)
            .hasMemoryIssues(false)
            .hasResponseTimeIssues(false)
            .metricResults(new HashMap<>())
            .build();
    }
}