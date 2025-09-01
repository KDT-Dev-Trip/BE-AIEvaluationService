package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluationService 단위 테스트")
class EvaluationServiceTest {

    @Mock
    private AIEvaluationRepository aiEvaluationRepository;

    @Mock
    private EvaluationSummaryRepository evaluationSummaryRepository;

    @Mock
    private EvaluationHistoryRepository evaluationHistoryRepository;

    @Mock
    private GeminiEvaluationService geminiEvaluationService;
    
    @Mock
    private EvaluationEventPublisher evaluationEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EvaluationService evaluationService;

    private MissionCompletedEvent testEvent;
    private AIEvaluation testEvaluation;
    private EvaluationResultDTO testResult;

    @BeforeEach
    void setUp() {
        testEvent = createTestMissionCompletedEvent();
        testEvaluation = createTestAIEvaluation();
        testResult = createTestEvaluationResult();
    }

    @Test
    @DisplayName("정상적인 평가 처리 - 성공 케이스")
    void processEvaluation_Success() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluation(testEvent);

        // Then
        verify(geminiEvaluationService).evaluateCode(eq(testEvent.getCode()), eq(testEvent.getMissionType()), eq(testEvent.getMissionId()));
        verify(evaluationEventPublisher).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("중복 평가 요청 방지")
    void processEvaluation_DuplicateEvaluation_PreventDuplicateProcessing() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(true);

        // When
        evaluationService.processEvaluation(testEvent);

        // Then
        verify(geminiEvaluationService, never()).evaluateCode(anyString(), anyString(), anyString());
        verify(evaluationEventPublisher, never()).publishEvaluationCompleted(any());
    }

    private MissionCompletedEvent createTestMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("123");
        event.setMissionId("456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update");
        event.setMissionTitle("Docker 컨테이너 생성");
        event.setCompletedAt(LocalDateTime.now());
        return event;
    }

    private AIEvaluation createTestAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("attempt-123");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());
        return evaluation;
    }

    private EvaluationResultDTO createTestEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(85);
        result.setFeedback("전체적으로 우수한 성능입니다.");
        result.setDetailedAnalysis("상세 분석 결과");

        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(80);
        codeQuality.setFeedback("코드 품질이 우수합니다.");
        result.setCodeQuality(codeQuality);

        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(90);
        security.setFeedback("보안 측면에서 양호합니다.");
        result.setSecurity(security);

        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(85);
        style.setFeedback("스타일이 일관성 있습니다.");
        result.setStyle(style);

        return result;
    }
}