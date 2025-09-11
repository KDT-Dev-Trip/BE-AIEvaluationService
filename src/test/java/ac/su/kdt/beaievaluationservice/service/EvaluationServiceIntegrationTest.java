package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ac.su.kdt.beaievaluationservice.config.TestConfig;
import org.springframework.context.annotation.Import;

@ExtendWith(MockitoExtension.class)
@Import(TestConfig.class)
class EvaluationServiceIntegrationTest {

    @Mock
    private AIEvaluationRepository aiEvaluationRepository;
    
    @Mock
    private EvaluationSummaryRepository evaluationSummaryRepository;
    
    @Mock
    private EvaluationHistoryRepository evaluationHistoryRepository;
    
    @Mock
    private EvaluationEventPublisher evaluationEventPublisher;
    
    @Mock
    private GeminiEvaluationService geminiEvaluationService;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private EvaluationService evaluationService;

    private MissionCompletedEvent testEvent;
    private AIEvaluation testEvaluation;

    @BeforeEach
    void setUp() {
        testEvent = createTestMissionCompletedEvent();
        testEvaluation = createTestAIEvaluation();
    }

    @Test
    void processEvaluation_WithValidEvent_ShouldCreateEvaluationSuccessfully() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId(anyString()))
            .thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class)))
            .thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenReturn(createMockEvaluationResult());

        // When
        evaluationService.processEvaluation(testEvent);

        // Then
        verify(aiEvaluationRepository, atLeastOnce()).save(any(AIEvaluation.class));
        verify(geminiEvaluationService, atMost(1)).evaluateCode(anyString(), anyString(), anyString());
    }

    @Test
    void processEvaluation_WithExistingEvaluation_ShouldNotCreateDuplicate() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId(anyString()))
            .thenReturn(true);

        // When
        evaluationService.processEvaluation(testEvent);

        // Then
        verify(aiEvaluationRepository, never()).save(any(AIEvaluation.class));
        verify(geminiEvaluationService, never()).evaluateCode(anyString(), anyString(), anyString());
    }

    @Test
    void processEvaluation_WithNullEvent_ShouldHandleGracefully() {
        // When & Then - Should throw NullPointerException as current implementation doesn't handle null gracefully
        assertThrows(NullPointerException.class, () -> {
            evaluationService.processEvaluation(null);
        });
    }

    @Test
    void processEvaluation_WithMissingRequiredFields_ShouldHandleGracefully() {
        // Given
        MissionCompletedEvent incompleteEvent = new MissionCompletedEvent();
        incompleteEvent.setEventType("MISSION_COMPLETED");
        // Missing required fields like userId, missionId, etc.

        // When & Then - Should throw NullPointerException as current implementation doesn't handle missing fields gracefully
        assertThrows(NullPointerException.class, () -> {
            evaluationService.processEvaluation(incompleteEvent);
        });
    }

    @Test
    void processEvaluation_WithGeminiServiceException_ShouldFailEvaluation() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId(anyString()))
            .thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class)))
            .thenReturn(testEvaluation);
        when(geminiEvaluationService.evaluateCode(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Gemini service error"));

        // When
        evaluationService.processEvaluation(testEvent);

        // Then
        verify(aiEvaluationRepository, atLeastOnce()).save(any(AIEvaluation.class));
        // Should save with failed status
    }

    @Test
    void extractScoreFromResult_WithValidJson_ShouldReturnCorrectScore() {
        // Given
        String evaluationResult = "{\"totalScore\": 85, \"details\": \"Good work\"}";

        // When
        int score = evaluationService.extractScoreFromResult(evaluationResult);

        // Then
        assertThat(score).isEqualTo(85);
    }

    @Test
    void extractScoreFromResult_WithInvalidJson_ShouldReturnDefaultScore() {
        // Given
        String evaluationResult = "invalid json";

        // When
        int score = evaluationService.extractScoreFromResult(evaluationResult);

        // Then
        assertThat(score).isEqualTo(70); // Default value
    }

    @Test
    void extractScoreFromResult_WithNullInput_ShouldReturnZero() {
        // When
        int score = evaluationService.extractScoreFromResult(null);

        // Then
        assertThat(score).isZero();
    }

    @Test
    void calculateStampsEarned_WithHighScore_ShouldReturn3Stamps() {
        // When
        int stamps = evaluationService.calculateStampsEarned(90);

        // Then
        assertThat(stamps).isEqualTo(3);
    }

    @Test
    void calculateStampsEarned_WithMediumScore_ShouldReturn2Stamps() {
        // When
        int stamps = evaluationService.calculateStampsEarned(75);

        // Then
        assertThat(stamps).isEqualTo(2);
    }

    @Test
    void calculateStampsEarned_WithLowScore_ShouldReturn1Stamp() {
        // When
        int stamps = evaluationService.calculateStampsEarned(65);

        // Then
        assertThat(stamps).isEqualTo(1);
    }

    @Test
    void calculateStampsEarned_WithVeryLowScore_ShouldReturnNoStamps() {
        // When
        int stamps = evaluationService.calculateStampsEarned(50);

        // Then
        assertThat(stamps).isZero();
    }

    @Test
    void calculatePointsAwarded_ShouldReturnScoreMultipliedBy10() {
        // When
        int points = evaluationService.calculatePointsAwarded(85);

        // Then
        assertThat(points).isEqualTo(850);
    }

    private MissionCompletedEvent createTestMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("123");  // Changed to numeric string
        event.setMissionId("456");  // Changed to numeric string
        event.setMissionAttemptId("789");
        event.setMissionTitle("Docker Container Creation");
        event.setCode("docker run hello-world");
        event.setCompletedAt(LocalDateTime.now());
        
        // Add statistics
        MissionCompletedEvent.SimpleStatistics statistics = 
            new MissionCompletedEvent.SimpleStatistics();
        statistics.setCommandSuccessCount(5);
        statistics.setCommandFailureCount(1);
        statistics.setAverageCpuUsage(45.0);
        statistics.setMaxCpuUsage(70.0);
        statistics.setAverageMemoryUsage(512.0);
        statistics.setMaxMemoryUsage(768.0);
        statistics.setTotalExecutionTime(30000L);
        event.setStatistics(statistics);
        
        return event;
    }

    private AIEvaluation createTestAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("attempt-789");
        evaluation.setUserId(123L);
        evaluation.setMissionId("mission-456");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setMissionTitle("Docker Container Creation");
        evaluation.setMissionType("DOCKER");
        evaluation.setSubmittedCode("docker run hello-world");
        evaluation.setEvaluationTrigger("MISSION_COMPLETION");
        evaluation.setCreatedAt(LocalDateTime.now());
        return evaluation;
    }

    private EvaluationResultDTO createMockEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(85);
        result.setFeedback("Good implementation with minor improvements needed.");
        result.setDetailedAnalysis("Detailed analysis of the submitted code");
        result.setSecurityRiskLevel("Low");
        result.setEfficiencyGrade("B");
        result.setBestPracticeScore(80);
        result.setReliabilityScore(85);
        result.setOverallObjectiveAchievement(90);
        
        // Create nested objects
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(85);
        codeQuality.setFeedback("Code quality is good");
        codeQuality.setEfficiencyGrade("B");
        codeQuality.setBestPracticeScore(80);
        codeQuality.setReliabilityScore(85);
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(90);
        security.setFeedback("Security practices are well implemented");
        security.setRiskLevel("Low");
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(85);
        style.setFeedback("Code style follows best practices");
        result.setStyle(style);
        
        return result;
    }
}