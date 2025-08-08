package ac.su.kdt.beaievaluationservice.integration;

import ac.su.kdt.beaievaluationservice.analyzer.MetricAnalyzer;
import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricSummaryDto;
import ac.su.kdt.beaievaluationservice.client.PrometheusClient;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.service.EvaluationService;
import ac.su.kdt.beaievaluationservice.service.GeminiEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// AI 평가 시스템 통합 테스트
// 전체 플로우: Kafka 이벤트 수신 → Prometheus 메트릭 조회 → AI 평가 → 결과 저장 → 이벤트 발행
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 평가 시스템 통합 테스트")
class AIEvaluationIntegrationTest {

    @Mock
    private AIEvaluationRepository aiEvaluationRepository;

    @Mock
    private EvaluationSummaryRepository evaluationSummaryRepository;

    @Mock
    private EvaluationHistoryRepository evaluationHistoryRepository;

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
    private EvaluationService evaluationService;

    private MissionCompletedEvent testEvent;
    private EvaluationResultDTO testResult;

    @BeforeEach
    void setUp() {
        testEvent = createTestMissionCompletedEvent();
        testResult = createTestEvaluationResult();
    }

    @Test
    @DisplayName("전체 플로우 통합 테스트 - 성공 케이스")
    void completeEvaluationFlow_Success() throws Exception {
        // Given
        setupSuccessfulMocks();
        AIEvaluation mockEvaluation = createMockAIEvaluation();
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(mockEvaluation);

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 컴포넌트 호출 순서 검증
        // 1. 중복 평가 체크
        verify(aiEvaluationRepository).existsByMissionAttemptId("attempt-123");
        
        // 2. 초기 평가 저장 (3번: PENDING → PROCESSING → COMPLETED)
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class));
        
        // 3. Prometheus 메트릭 조회
        verify(prometheusClient).queryMissionMetrics(
            eq("cpu_usage"), eq("attempt-123"), 
            any(LocalDateTime.class), any(LocalDateTime.class), eq(15)
        );
        verify(prometheusClient).queryMissionMetrics(
            eq("memory_usage"), eq("attempt-123"), 
            any(LocalDateTime.class), any(LocalDateTime.class), eq(15)
        );
        verify(prometheusClient).queryMissionMetrics(
            eq("response_time"), eq("attempt-123"), 
            any(LocalDateTime.class), any(LocalDateTime.class), eq(15)
        );
        
        // 4. 메트릭 분석 호출
        verify(metricAnalyzer).analyzeMissionPerformance(
            eq("attempt-123"), eq("user-123"), any(Map.class)
        );
        
        // 5. AI 평가 호출
        verify(geminiEvaluationService).evaluateCode(
            testEvent.getCode(), testEvent.getMissionType(), testEvent.getMissionId()
        );
        
        // 6. JSON 직렬화
        verify(objectMapper).writeValueAsString(testResult);
        
        // 7. EvaluationSummary 저장
        verify(evaluationSummaryRepository).save(any(EvaluationSummary.class));
        
        // 8. EvaluationHistory 저장 (3번의 상태 변경)
        verify(evaluationHistoryRepository, times(3)).save(any());
        
        // 9. 평가 완료 이벤트 발행
        verify(evaluationEventPublisher).publishEvaluationCompleted(any(EvaluationCompletedEvent.class));
    }

    @Test
    @DisplayName("Prometheus 장애 상황에서의 복원력 테스트")
    void evaluationFlow_PrometheusFailure_Resilience() throws Exception {
        // Given - Prometheus 장애 상황 설정
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        AIEvaluation mockEvaluation = createMockAIEvaluation();
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(mockEvaluation);
        
        when(prometheusClient.queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenThrow(new RuntimeException("Prometheus connection failed"));

        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - Prometheus 장애에도 AI 평가는 정상 진행
        verify(geminiEvaluationService).evaluateCode(testEvent.getCode(), testEvent.getMissionType(), testEvent.getMissionId());
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class));
        verify(evaluationSummaryRepository).save(any(EvaluationSummary.class));
        
        // 평가 완료 이벤트는 여전히 발행되어야 함 (성능 분석 없이)
        verify(evaluationEventPublisher).publishEvaluationCompleted(any(EvaluationCompletedEvent.class));
    }

    @Test
    @DisplayName("AI 평가 실패 시 실패 처리 플로우 테스트")
    void evaluationFlow_AIEvaluationFailure_FailureHandling() throws Exception {
        // Given - AI 평가 실패 설정
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        AIEvaluation mockEvaluation = createMockAIEvaluation();
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(mockEvaluation);
        
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Gemini API rate limit exceeded"));

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 실패 처리 검증
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class)); // PENDING → PROCESSING → FAILED
        
        // 실패 이벤트 발행 검증
        verify(evaluationEventPublisher).publishEvaluationFailed(
            eq("attempt-123"), eq("user-123"), contains("Gemini API rate limit exceeded")
        );

        // EvaluationSummary는 생성되지 않아야 함
        verify(evaluationSummaryRepository, never()).save(any(EvaluationSummary.class));
    }

    @Test
    @DisplayName("중복 평가 요청 처리 테스트")
    void evaluationFlow_DuplicateRequest_Prevention() throws Exception {
        // Given - 중복 평가 존재 설정
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(true);

        // When - 동일한 요청 재시도
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 추가 평가 수행되지 않음
        verify(aiEvaluationRepository, never()).save(any(AIEvaluation.class));
        verify(prometheusClient, never()).queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
        verify(geminiEvaluationService, never()).evaluateCode(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("성능 분석 결과를 포함한 이벤트 발행 테스트")
    void evaluationFlow_WithPerformanceAnalysis_EventPublishing() throws Exception {
        // Given
        setupSuccessfulMocks();
        AIEvaluation mockEvaluation = createMockAIEvaluation();
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(mockEvaluation);

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 성능 분석 결과가 포함된 이벤트 발행 검증
        ArgumentCaptor<EvaluationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(EvaluationCompletedEvent.class);
        verify(evaluationEventPublisher).publishEvaluationCompleted(eventCaptor.capture());
        
        EvaluationCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getMissionAttemptId()).isEqualTo("attempt-123");
        assertThat(publishedEvent.getUserId()).isEqualTo("user-123");
        assertThat(publishedEvent.getOverallScore()).isEqualTo(85);
        assertThat(publishedEvent.getPerformanceGrade()).isEqualTo("GOOD");
        assertThat(publishedEvent.getHasCpuIssues()).isFalse();
        assertThat(publishedEvent.getHasMemoryIssues()).isFalse();
        assertThat(publishedEvent.getProcessingTimeMs()).isNotNull();
        assertThat(publishedEvent.getEvaluationStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("시간 구간이 없는 이벤트 처리 - 메트릭 수집 건너뛰기")
    void evaluationFlow_NoTimeRange_SkipMetrics() throws Exception {
        // Given - 시간 구간이 없는 이벤트
        MissionCompletedEvent eventWithoutTime = createTestMissionCompletedEventWithoutTime();
        
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        AIEvaluation mockEvaluation = createMockAIEvaluation();
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(mockEvaluation);
        
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluationAsync(eventWithoutTime);

        // Then - 평가 완료
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class));
        verify(evaluationSummaryRepository).save(any(EvaluationSummary.class));

        // Prometheus 호출 안함
        verify(prometheusClient, never()).queryMissionMetrics(anyString(), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt());
        
        // 이벤트는 여전히 발행 (성능 분석 없이)
        verify(evaluationEventPublisher).publishEvaluationCompleted(any(EvaluationCompletedEvent.class));
    }

    // Helper methods for test setup
    private void setupSuccessfulMocks() throws Exception {
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        setupPrometheusSuccess();
        setupMetricAnalyzerSuccess();
        setupGeminiSuccess();
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");
    }

    private AIEvaluation createMockAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("attempt-123");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());
        return evaluation;
    }

    private void setupPrometheusSuccess() {
        // CPU 메트릭 데이터
        List<MetricDataPoint> cpuData = Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 45.0),
            new MetricDataPoint(Instant.now().minusSeconds(30), 50.0),
            new MetricDataPoint(Instant.now(), 55.0)
        );

        // 메모리 메트릭 데이터
        List<MetricDataPoint> memoryData = Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 70.0),
            new MetricDataPoint(Instant.now().minusSeconds(30), 75.0),
            new MetricDataPoint(Instant.now(), 80.0)
        );

        // 응답 시간 메트릭 데이터
        List<MetricDataPoint> responseTimeData = Arrays.asList(
            new MetricDataPoint(Instant.now().minusSeconds(60), 200.0),
            new MetricDataPoint(Instant.now().minusSeconds(30), 250.0),
            new MetricDataPoint(Instant.now(), 300.0)
        );

        when(prometheusClient.queryMissionMetrics(eq("cpu_usage"), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(cpuData);
        when(prometheusClient.queryMissionMetrics(eq("memory_usage"), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(memoryData);
        when(prometheusClient.queryMissionMetrics(eq("response_time"), anyString(), 
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
            .thenReturn(responseTimeData);
    }

    private void setupMetricAnalyzerSuccess() {
        MetricSummaryDto performanceSummary = MetricSummaryDto.builder()
            .missionAttemptId("attempt-123")
            .userId("user-123")
            .overallGrade(MetricSummaryDto.PerformanceGrade.GOOD)
            .performanceSummary("전체적으로 안정적인 성능을 보입니다.")
            .hasCpuIssues(false)
            .hasMemoryIssues(false)
            .hasResponseTimeIssues(false)
            .hasHighVariability(false)
            .metricResults(new HashMap<>())
            .build();

        when(metricAnalyzer.analyzeMissionPerformance(anyString(), anyString(), any(Map.class)))
            .thenReturn(performanceSummary);
    }

    private void setupGeminiSuccess() throws Exception {
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenReturn(testResult);
    }

    private MissionCompletedEvent createTestMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update");
        event.setMissionTitle("Docker 컨테이너 생성 실습");
        event.setCompletedAt(LocalDateTime.now());
        event.setStartAt(LocalDateTime.now().minusHours(1)); // 1시간 전 시작
        event.setEndAt(LocalDateTime.now()); // 현재 시간 종료
        return event;
    }

    private MissionCompletedEvent createTestMissionCompletedEventWithoutTime() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("user-123");
        event.setMissionId("mission-456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update");
        event.setMissionTitle("Docker 컨테이너 생성 실습");
        event.setCompletedAt(LocalDateTime.now());
        // startAt, endAt은 null
        return event;
    }

    private EvaluationResultDTO createTestEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(85);
        result.setFeedback("Overall good code quality with minor improvements needed");
        result.setDetailedAnalysis("Detailed analysis of the code...");

        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(80);
        codeQuality.setFeedback("Code structure is well organized");
        codeQuality.setSuggestions("Add more comments for clarity");
        result.setCodeQuality(codeQuality);

        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(90);
        security.setFeedback("No major security vulnerabilities found");
        security.setVulnerabilities("None detected");
        security.setRecommendations("Continue following security best practices");
        result.setSecurity(security);

        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(85);
        style.setFeedback("Consistent coding style");
        style.setStyleIssues("Minor indentation issues");
        style.setImprovements("Use consistent indentation throughout");
        result.setStyle(style);

        return result;
    }
}