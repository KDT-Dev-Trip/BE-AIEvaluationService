package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
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
import static org.junit.jupiter.api.Assertions.*;

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
    void processEvaluationAsync_Success() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then
        verify(aiEvaluationRepository, times(3)).save(any(AIEvaluation.class)); // Initial, Processing, Completed
        verify(evaluationSummaryRepository).save(any(EvaluationSummary.class));
        verify(evaluationHistoryRepository, times(3)).save(any(EvaluationHistory.class)); // 3 status changes
        verify(geminiEvaluationService).evaluateCode(testEvent.getCode(), testEvent.getMissionType(), testEvent.getMissionId());
    }

    @Test
    @DisplayName("중복 평가 요청 - 이미 존재하는 경우")
    void processEvaluationAsync_DuplicateRequest() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(true);

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then
        verify(aiEvaluationRepository, never()).save(any(AIEvaluation.class));
        verify(geminiEvaluationService, never()).evaluateCode(anyString(), anyString(), anyString());
        verify(evaluationSummaryRepository, never()).save(any(EvaluationSummary.class));
    }

    @Test
    @DisplayName("Gemini API 호출 실패 - 평가 실패 처리")
    void processEvaluationAsync_GeminiApiFailed() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Gemini API failed"));

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then
        verify(aiEvaluationRepository, times(3)).save(argThat(evaluation -> {
            if (evaluation.getStatus() == AIEvaluation.EvaluationStatus.FAILED) {
                assertNotNull(evaluation.getErrorMessage());
                assertTrue(evaluation.getErrorMessage().contains("Gemini API failed"));
                return true;
            }
            return true;
        }));
        verify(evaluationSummaryRepository, never()).save(any(EvaluationSummary.class));
    }

    @Test
    @DisplayName("JSON 변환 실패 - 평가 실패 처리")
    void processEvaluationAsync_JsonSerializationFailed() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenThrow(new RuntimeException("JSON serialization failed"));

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then
        verify(aiEvaluationRepository, times(3)).save(argThat(evaluation -> {
            if (evaluation.getStatus() == AIEvaluation.EvaluationStatus.FAILED) {
                assertNotNull(evaluation.getErrorMessage());
                assertTrue(evaluation.getErrorMessage().contains("Failed to save evaluation result"));
                return true;
            }
            return true;
        }));
        verify(evaluationSummaryRepository, never()).save(any(EvaluationSummary.class));
    }

    @Test
    @DisplayName("평가 상태 변경 추적 검증")
    void processEvaluationAsync_VerifyStatusChangeTracking() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 상태 변경 이력이 올바르게 기록되는지 검증
        verify(evaluationHistoryRepository, times(3)).save(argThat(history -> {
            assertNotNull(history.getAiEvaluation());
            assertNotNull(history.getNewStatus());
            assertNotNull(history.getChangeReason());
            return true;
        }));
    }

    @Test
    @DisplayName("EvaluationSummary 생성 검증")
    void processEvaluationAsync_VerifyEvaluationSummaryCreation() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString())).thenReturn(testResult);
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluationAsync(testEvent);

        // Then - EvaluationSummary가 올바르게 생성되는지 검증
        verify(evaluationSummaryRepository).save(argThat(summary -> {
            assertEquals(testEvent.getUserId(), summary.getUserId());
            assertEquals(testEvent.getMissionId(), summary.getMissionId());
            assertEquals(testEvent.getMissionAttemptId(), summary.getMissionAttemptId());
            assertEquals(testEvent.getMissionTitle(), summary.getMissionTitle());
            assertEquals(testEvent.getMissionType(), summary.getMissionType());
            assertEquals(testResult.getOverallScore(), summary.getOverallScore());
            assertEquals(AIEvaluation.EvaluationStatus.COMPLETED, summary.getStatus());
            assertEquals(testResult.getFeedback(), summary.getFeedbackSummary());
            assertEquals(testEvaluation, summary.getAiEvaluation());
            return true;
        }));
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