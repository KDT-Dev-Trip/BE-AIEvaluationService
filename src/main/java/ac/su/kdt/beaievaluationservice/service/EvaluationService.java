package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

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
    private final EvaluationEventPublisher evaluationEventPublisher;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void processEvaluation(MissionCompletedEvent event) {
        String missionAttemptId = event.getMissionAttemptId();
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        log.info("=== AI EVALUATION PROCESS STARTED ===");
        log.info("MissionAttemptId: {}", missionAttemptId);
        log.info("UserId: {}, MissionId: {}", event.getUserId(), event.getMissionId());
        log.info("MissionType: {}, MissionTitle: {}", event.getMissionType(), event.getMissionTitle());
        log.info("Processing started at: {}", processingStartTime);
        
        if (aiEvaluationRepository.existsByMissionAttemptId(missionAttemptId)) {
            log.warn("=== DUPLICATE EVALUATION DETECTED ===");
            log.warn("Evaluation already exists for missionAttemptId: {}", missionAttemptId);
            return;
        }

        AIEvaluation evaluation = createInitialEvaluation(event);
        evaluation = aiEvaluationRepository.save(evaluation);
        
        recordStatusChange(evaluation, null, AIEvaluation.EvaluationStatus.PENDING, "Initial evaluation request");
        
        try {
            log.info("=== EVALUATION STATUS: PROCESSING ===");
            updateEvaluationStatus(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, "Starting AI evaluation");
            
            // S3 데이터 준비
            String s3StorageUrl = event.getS3StorageUrl();
            String preSignedUrl = event.getS3PreSignedUrl();
            
            log.info("=== S3 DATA PREPARATION ===");
            log.info("S3 Storage URL: {}", s3StorageUrl);
            log.info("Pre-Signed URL available: {}", preSignedUrl != null && !preSignedUrl.isEmpty());
            
            // AI 평가 수행
            log.info("=== CALLING GEMINI AI EVALUATION ===");
            log.info("Code length: {} characters", event.getCode() != null ? event.getCode().length() : 0);
            log.info("Statistics available: {}", event.getStatistics() != null);
            
            EvaluationResultDTO result = geminiEvaluationService.evaluateCode(
                event.getCode(), 
                event.getMissionType(),
                event.getMissionId(),
                event.getMissionObjective(),
                event.getChecklist(),
                s3StorageUrl,
                preSignedUrl,
                event.getStatistics()
            );
            
            log.info("=== AI EVALUATION COMPLETED ===");
            log.info("Overall Score: {}", result.getOverallScore());
            
            // 결과 저장 및 이벤트 발행
            completeEvaluation(evaluation, result, event, processingStartTime);
            
        } catch (RuntimeException e) {
            log.error("=== EVALUATION RUNTIME ERROR ===");
            log.error("Runtime error during evaluation for missionAttemptId: {} - Error: {}", missionAttemptId, e.getMessage(), e);
            
            String errorDetail = String.format("Runtime error: %s", e.getMessage());
            if (e.getCause() != null) {
                errorDetail += String.format(" (Caused by: %s)", e.getCause().getMessage());
            }
            failEvaluationWithEvent(evaluation, event, errorDetail);
            
        } catch (Exception e) {
            log.error("=== EVALUATION UNEXPECTED ERROR ===");
            log.error("Unexpected error during evaluation for missionAttemptId: {} - Error type: {} - Message: {}", 
                     missionAttemptId, e.getClass().getSimpleName(), e.getMessage(), e);
            
            String errorDetail = String.format("Unexpected %s: %s", e.getClass().getSimpleName(), e.getMessage());
            failEvaluationWithEvent(evaluation, event, errorDetail);
        }
    }
    
    /**
     * 초기 평가 객체 생성
     */
    private AIEvaluation createInitialEvaluation(MissionCompletedEvent event) {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setMissionAttemptId(event.getMissionAttemptId());
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-2.0-flash-exp");
        
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
    
    private void completeEvaluation(AIEvaluation evaluation, EvaluationResultDTO result, 
                                   MissionCompletedEvent event, LocalDateTime processingStartTime) {
        String missionAttemptId = evaluation.getMissionAttemptId();
        
        try {
            log.info("=== SAVING EVALUATION RESULT ===");
            
            // JSON 직렬화 시도
            String resultJson;
            try {
                resultJson = objectMapper.writeValueAsString(result);
                if (resultJson == null || resultJson.trim().isEmpty()) {
                    throw new IllegalStateException("Serialized JSON is null or empty");
                }
                log.debug("Evaluation result serialized successfully, size: {} characters", resultJson.length());
            } catch (Exception jsonEx) {
                log.error("Failed to serialize evaluation result to JSON for {}: {}", missionAttemptId, jsonEx.getMessage());
                throw new RuntimeException("JSON serialization failed", jsonEx);
            }
            
            // 데이터베이스 저장 시도
            try {
                evaluation.setEvaluationResult(resultJson);
                evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
                evaluation = aiEvaluationRepository.save(evaluation);
                log.info("Evaluation result saved successfully for {}", missionAttemptId);
            } catch (Exception dbEx) {
                log.error("Failed to save evaluation to database for {}: {}", missionAttemptId, dbEx.getMessage());
                throw new RuntimeException("Database save failed", dbEx);
            }
            
            // 평가 요약 생성 시도
            try {
                log.info("=== CREATING EVALUATION SUMMARY ===");
                createEvaluationSummary(evaluation, result, event);
                log.info("Evaluation summary created successfully for {}", missionAttemptId);
            } catch (Exception summaryEx) {
                log.error("Failed to create evaluation summary for {}: {}", missionAttemptId, summaryEx.getMessage());
                // Summary 생성 실패는 전체 평가를 실패시키지 않음
            }
            
            // 상태 변경 이력 기록
            try {
                recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                                 AIEvaluation.EvaluationStatus.COMPLETED, "Evaluation completed successfully");
            } catch (Exception historyEx) {
                log.warn("Failed to record status change history for {}: {}", missionAttemptId, historyEx.getMessage());
                // 이력 기록 실패는 전체 프로세스를 중단시키지 않음
            }
            
            // 평가 완료 이벤트 발행
            try {
                log.info("=== PUBLISHING EVALUATION COMPLETED EVENT ===");
                publishEvaluationCompletedEvent(evaluation, result, event, processingStartTime);
            } catch (Exception eventEx) {
                log.error("Failed to publish evaluation completed event for {}: {}", missionAttemptId, eventEx.getMessage());
                // 이벤트 발행 실패는 평가 자체를 실패시키지 않음
            }
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            log.info("=== EVALUATION PROCESS COMPLETED ===");
            log.info("MissionAttemptId: {}", missionAttemptId);
            log.info("Total processing time: {} ms", processingTimeMs);
            log.info("Final score: {}", result.getOverallScore());
            
        } catch (RuntimeException e) {
            log.error("=== SAVE RESULT ERROR ===");
            log.error("Failed to complete evaluation for {}: {}", missionAttemptId, e.getMessage(), e);
            failEvaluationWithEvent(evaluation, event, "Failed to complete evaluation: " + e.getMessage());
        } catch (Exception e) {
            log.error("=== UNEXPECTED SAVE ERROR ===");
            log.error("Unexpected error during evaluation completion for {}: {}", missionAttemptId, e.getMessage(), e);
            failEvaluationWithEvent(evaluation, event, "Unexpected error during completion: " + e.getMessage());
        }
    }
    
    private void createEvaluationSummary(AIEvaluation evaluation, EvaluationResultDTO result, MissionCompletedEvent event) {
        if (result == null) {
            throw new IllegalArgumentException("EvaluationResultDTO cannot be null");
        }
        
        try {
            EvaluationSummary summary = new EvaluationSummary();
            summary.setUserId(event.getUserId());
            summary.setMissionId(event.getMissionId());
            summary.setMissionAttemptId(event.getMissionAttemptId());
            summary.setMissionTitle(event.getMissionTitle());
            summary.setMissionType(event.getMissionType());
            
            // Null-safe score setting
            summary.setOverallScore(result.getOverallScore() != null ? result.getOverallScore() : 0);
            summary.setCodeQualityScore(result.getCodeQuality() != null ? result.getCodeQuality().getScore() : null);
            summary.setSecurityScore(result.getSecurity() != null ? result.getSecurity().getScore() : null);
            summary.setStyleScore(result.getStyle() != null ? result.getStyle().getScore() : null);
            
            summary.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            
            // Feedback summary with length validation
            String feedbackSummary = result.getFeedback();
            if (feedbackSummary != null && feedbackSummary.length() > 2000) {
                feedbackSummary = feedbackSummary.substring(0, 1997) + "...";
                log.warn("Feedback summary truncated for missionAttemptId: {}", event.getMissionAttemptId());
            }
            summary.setFeedbackSummary(feedbackSummary);
            summary.setAiEvaluation(evaluation);
            
            evaluationSummaryRepository.save(summary);
            log.debug("Evaluation summary saved successfully for {}", event.getMissionAttemptId());
            
        } catch (Exception e) {
            log.error("Error creating evaluation summary for {}: {}", event.getMissionAttemptId(), e.getMessage());
            throw new RuntimeException("Failed to create evaluation summary", e);
        }
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


    /**
     * 평가 완료 이벤트를 Kafka로 발행한다
     */
    private void publishEvaluationCompletedEvent(AIEvaluation evaluation, EvaluationResultDTO result, 
                                                MissionCompletedEvent event, LocalDateTime processingStartTime) {
        try {
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            EvaluationCompletedEvent.EvaluationCompletedEventBuilder eventBuilder = EvaluationCompletedEvent.builder()
                    .missionAttemptId(event.getMissionAttemptId())
                    .userId(event.getUserId())
                    .missionId(event.getMissionId())
                    .missionTitle(event.getMissionTitle())
                    .missionType(event.getMissionType())
                    .evaluationId(evaluation.getId())
                    .overallScore(result.getOverallScore())
                    .feedbackSummary(result.getFeedback())
                    .aiModelVersion(evaluation.getAiModelVersion())
                    .evaluationStatus("COMPLETED")
                    .completedAt(LocalDateTime.now())
                    .processingTimeMs(processingTimeMs);

            // AI 평가 점수들 설정
            if (result.getCodeQuality() != null) {
                eventBuilder.codeQualityScore(result.getCodeQuality().getScore());
            }
            if (result.getSecurity() != null) {
                eventBuilder.securityScore(result.getSecurity().getScore());
            }
            if (result.getStyle() != null) {
                eventBuilder.styleScore(result.getStyle().getScore());
            }

            // 통계 정보가 있으면 포함
            if (event.getStatistics() != null) {
                MissionCompletedEvent.SimpleStatistics stats = event.getStatistics();
                eventBuilder
                        .commandSuccessCount(stats.getCommandSuccessCount())
                        .commandFailureCount(stats.getCommandFailureCount())
                        .averageCpuUsage(stats.getAverageCpuUsage())
                        .maxCpuUsage(stats.getMaxCpuUsage())
                        .averageMemoryUsage(stats.getAverageMemoryUsage())
                        .maxMemoryUsage(stats.getMaxMemoryUsage())
                        .totalExecutionTime(stats.getTotalExecutionTime());
            }

            EvaluationCompletedEvent completedEvent = eventBuilder.build();
            
            log.info("=== KAFKA EVENT PUBLISHING ===");
            log.info("Publishing evaluation.completed event for missionAttemptId: {}", event.getMissionAttemptId());
            log.info("Event contains: overallScore={}, processingTime={}ms", result.getOverallScore(), processingTimeMs);
            
            evaluationEventPublisher.publishEvaluationCompleted(completedEvent);

            log.info("=== KAFKA EVENT PUBLISHED SUCCESSFULLY ===");
            log.info("Evaluation completed event published for missionAttemptId: {}", event.getMissionAttemptId());

        } catch (Exception e) {
            log.error("=== KAFKA PUBLISH ERROR ===");
            log.error("Failed to publish evaluation completed event for missionAttemptId: {}", 
                     event.getMissionAttemptId(), e);
            // 이벤트 발행 실패는 전체 평가를 실패시키지 않음
        }
    }

    /**
     * 평가 실패 시 실패 이벤트도 함께 발행한다
     */
    private void failEvaluationWithEvent(AIEvaluation evaluation, MissionCompletedEvent event, String errorMessage) {
        // 기존 실패 처리
        failEvaluation(evaluation, errorMessage);

        // 실패 이벤트 발행
        try {
            evaluationEventPublisher.publishEvaluationFailed(
                event.getMissionAttemptId(), 
                event.getUserId(), 
                errorMessage
            );
            log.info("Published evaluation failed event for missionAttemptId: {}", event.getMissionAttemptId());
        } catch (Exception e) {
            log.error("Failed to publish evaluation failed event for missionAttemptId: {}", 
                     event.getMissionAttemptId(), e);
        }
    }

    private void failEvaluation(AIEvaluation evaluation, String errorMessage) {
        String missionAttemptId = evaluation.getMissionAttemptId();
        
        try {
            // 오류 메시지 길이 제한 (데이터베이스 컴럼 사이즈 고려)
            String truncatedErrorMessage = errorMessage;
            if (errorMessage != null && errorMessage.length() > 2000) {
                truncatedErrorMessage = errorMessage.substring(0, 1997) + "...";
                log.warn("Error message truncated for missionAttemptId: {}", missionAttemptId);
            }
            
            evaluation.setStatus(AIEvaluation.EvaluationStatus.FAILED);
            evaluation.setErrorMessage(truncatedErrorMessage);
            
            try {
                aiEvaluationRepository.save(evaluation);
                log.info("Failed evaluation status saved for {}", missionAttemptId);
            } catch (Exception dbEx) {
                log.error("Critical: Failed to save failure status for {}: {}", missionAttemptId, dbEx.getMessage());
                // 이 경우에도 예외를 다시 던지지 않음 (무한 루프 방지)
            }
            
            try {
                recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                                 AIEvaluation.EvaluationStatus.FAILED, truncatedErrorMessage);
            } catch (Exception historyEx) {
                log.warn("Failed to record failure status change history for {}: {}", missionAttemptId, historyEx.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Critical error while failing evaluation for {}: {}", missionAttemptId, e.getMessage());
        }
    }
    
}