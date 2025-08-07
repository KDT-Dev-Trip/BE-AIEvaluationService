package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// AI 평가 서비스
// 미션 완료 이벤트를 처리하여 AI 평가를 수행하고, 평가 결과를 데이터베이스에 저장
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {
    
    private final AIEvaluationRepository aiEvaluationRepository;
    private final EvaluationSummaryRepository evaluationSummaryRepository;
    private final EvaluationHistoryRepository evaluationHistoryRepository;
    private final GeminiEvaluationService geminiEvaluationService;
    private final ObjectMapper objectMapper;
    
    @Async
    @Transactional
    public void processEvaluationAsync(MissionCompletedEvent event) {
        String missionAttemptId = event.getMissionAttemptId();
        
        if (aiEvaluationRepository.existsByMissionAttemptId(missionAttemptId)) {
            log.warn("Evaluation already exists for missionAttemptId: {}", missionAttemptId);
            return;
        }

        AIEvaluation evaluation = createInitialEvaluation(event);
        evaluation = aiEvaluationRepository.save(evaluation);
        
        recordStatusChange(evaluation, null, AIEvaluation.EvaluationStatus.PENDING, "Initial evaluation request");
        
        try {
            updateEvaluationStatus(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, "Starting AI evaluation");
            
            EvaluationResultDTO result = geminiEvaluationService.evaluateCode(
                event.getCode(), 
                event.getMissionType(),
                event.getMissionId()
            );
            
            completeEvaluation(evaluation, result, event);
            
        } catch (Exception e) {
            failEvaluation(evaluation, e.getMessage());
            log.error("Failed to process evaluation for missionAttemptId: {}", missionAttemptId, e);
        }
    }
    
    private AIEvaluation createInitialEvaluation(MissionCompletedEvent event) {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setMissionAttemptId(event.getMissionAttemptId());
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        return evaluation;
    }
    
    private void updateEvaluationStatus(AIEvaluation evaluation, AIEvaluation.EvaluationStatus newStatus, String reason) {
        AIEvaluation.EvaluationStatus previousStatus = evaluation.getStatus();
        evaluation.setStatus(newStatus);
        aiEvaluationRepository.save(evaluation);
        
        recordStatusChange(evaluation, previousStatus, newStatus, reason);
        log.info("Updated evaluation status for missionAttemptId: {} from {} to {}", 
                evaluation.getMissionAttemptId(), previousStatus, newStatus);
    }
    
    private void completeEvaluation(AIEvaluation evaluation, EvaluationResultDTO result, MissionCompletedEvent event) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            evaluation.setEvaluationResult(resultJson);
            evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            aiEvaluationRepository.save(evaluation);
            
            createEvaluationSummary(evaluation, result, event);
            recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                             AIEvaluation.EvaluationStatus.COMPLETED, "Evaluation completed successfully");
            
            log.info("Successfully completed evaluation for missionAttemptId: {}", evaluation.getMissionAttemptId());
            
        } catch (Exception e) {
            failEvaluation(evaluation, "Failed to save evaluation result: " + e.getMessage());
        }
    }
    
    private void failEvaluation(AIEvaluation evaluation, String errorMessage) {
        evaluation.setStatus(AIEvaluation.EvaluationStatus.FAILED);
        evaluation.setErrorMessage(errorMessage);
        aiEvaluationRepository.save(evaluation);
        
        recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                         AIEvaluation.EvaluationStatus.FAILED, errorMessage);
    }
    
    private void createEvaluationSummary(AIEvaluation evaluation, EvaluationResultDTO result, MissionCompletedEvent event) {
        EvaluationSummary summary = new EvaluationSummary();
        summary.setUserId(event.getUserId());
        summary.setMissionId(event.getMissionId());
        summary.setMissionAttemptId(event.getMissionAttemptId());
        summary.setMissionTitle(event.getMissionTitle());
        summary.setMissionType(event.getMissionType());
        summary.setOverallScore(result.getOverallScore());
        summary.setCodeQualityScore(result.getCodeQuality() != null ? result.getCodeQuality().getScore() : null);
        summary.setSecurityScore(result.getSecurity() != null ? result.getSecurity().getScore() : null);
        summary.setStyleScore(result.getStyle() != null ? result.getStyle().getScore() : null);
        summary.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
        summary.setFeedbackSummary(result.getFeedback());
        summary.setAiEvaluation(evaluation);
        
        evaluationSummaryRepository.save(summary);
    }
    
    private void recordStatusChange(AIEvaluation evaluation, AIEvaluation.EvaluationStatus previousStatus, 
                                  AIEvaluation.EvaluationStatus newStatus, String reason) {
        EvaluationHistory history = new EvaluationHistory();
        history.setAiEvaluation(evaluation);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangeReason(reason);
        
        evaluationHistoryRepository.save(history);
    }
}