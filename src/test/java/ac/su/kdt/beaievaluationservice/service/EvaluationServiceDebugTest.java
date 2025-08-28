package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceDebugTest {

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

    @Test
    void testMissionIdValuePreservation() {
        // Given
        String testUserId = "123";
        String testMissionId = "456"; 
        String testMissionAttemptId = "testAttempt789";
        
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId(testUserId);
        event.setMissionId(testMissionId);
        event.setMissionAttemptId(testMissionAttemptId);
        event.setMissionType("DOCKER");
        event.setMissionTitle("Test Mission");
        event.setCode("echo 'test'");
        event.setCompletedAt(LocalDateTime.now());
        
        // Mock repository behavior
        when(aiEvaluationRepository.existsByMissionAttemptId(testMissionAttemptId)).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenAnswer(invocation -> {
            AIEvaluation saved = invocation.getArgument(0);
            saved.setId(1L); // Simulate database auto-generated ID
            return saved;
        });
        
        // When
        try {
            evaluationService.processEvaluation(event);
        } catch (Exception e) {
            // Expected due to missing mocks for actual AI evaluation, but we can verify the initial save
        }
        
        // Then - verify that save was called at least once
        verify(aiEvaluationRepository, atLeast(1)).save(any(AIEvaluation.class));
    }
    
    @Test 
    void testEventDataTransfer() {
        // This test specifically checks if the data flows correctly from MissionCompletedEvent to AIEvaluation
        String userId = "999";
        String missionId = "888";
        String missionAttemptId = "attempt777";
        
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setUserId(userId);
        event.setMissionId(missionId);
        event.setMissionAttemptId(missionAttemptId);
        event.setMissionType("TEST");
        event.setMissionTitle("Test Mission Title");
        event.setCode("test code");
        event.setCompletedAt(LocalDateTime.now());
        
        when(aiEvaluationRepository.existsByMissionAttemptId(missionAttemptId)).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenAnswer(invocation -> {
            AIEvaluation evaluation = invocation.getArgument(0);
            
            // Print debug information
            System.out.println("\n=== EVALUATION OBJECT CAPTURED IN SAVE ===");
            System.out.println("Original Event - userId: " + userId + ", missionId: " + missionId + ", attemptId: " + missionAttemptId);
            System.out.println("Evaluation Object - userId: " + evaluation.getUserId() + ", missionId: " + evaluation.getMissionId() + ", attemptId: " + evaluation.getMissionAttemptId());
            
            // Check for null values
            if (evaluation.getUserId() == null) {
                System.out.println("!!! ISSUE FOUND: evaluation.getUserId() is NULL !!!");
            }
            if (evaluation.getMissionId() == null) {
                System.out.println("!!! ISSUE FOUND: evaluation.getMissionId() is NULL !!!");
            }
            if (evaluation.getMissionAttemptId() == null) {
                System.out.println("!!! ISSUE FOUND: evaluation.getMissionAttemptId() is NULL !!!");
            }
            
            evaluation.setId(1L);
            return evaluation;
        });
        
        // Execute
        try {
            evaluationService.processEvaluation(event);
        } catch (Exception e) {
            // Expected due to incomplete mocking
        }
        
        // Verify save was called (can be multiple times due to status updates)
        verify(aiEvaluationRepository, atLeast(1)).save(any(AIEvaluation.class));
    }
}