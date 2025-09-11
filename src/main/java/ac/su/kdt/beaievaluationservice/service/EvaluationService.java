package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.constants.EvaluationConstants;
import ac.su.kdt.beaievaluationservice.exception.JsonSerializationException;
import ac.su.kdt.beaievaluationservice.exception.DatabaseOperationException;
import ac.su.kdt.beaievaluationservice.exception.EvaluationException;

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
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

// AI 평가 서비스
// 미션 완료 이벤트를 처리하여 AI 평가를 수행하고, 평가 결과를 데이터베이스에 저장
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {
    
    // Constants
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MIN_SCORE_THRESHOLD = 0;
    private static final int MAX_SCORE_THRESHOLD = 100;
    private static final String DEFAULT_EVALUATION_STATUS = EvaluationConstants.STATUS_COMPLETED;
    private static final String FAILED_EVALUATION_STATUS = EvaluationConstants.STATUS_FAILED;
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String RUNTIME_ERROR_CODE = "RUNTIME_ERROR";
    private static final String AI_MODEL_VERSION = EvaluationConstants.AI_MODEL_VERSION_GEMINI_20_FLASH;
    private static final String EVALUATION_COMPLETED_MESSAGE = EvaluationConstants.SUCCESS_EVALUATION_COMPLETED;
    private static final String INVALID_INPUT_DATA_PREFIX = "Invalid input data: ";
    private static final String RUNTIME_ERROR_PREFIX = "Runtime error: ";
    
    private final AIEvaluationRepository aiEvaluationRepository;
    private final EvaluationSummaryRepository evaluationSummaryRepository;
    private final EvaluationHistoryRepository evaluationHistoryRepository;
    private final GeminiEvaluationService geminiEvaluationService;
    private final EvaluationEventPublisher evaluationEventPublisher;
    private final ac.su.kdt.beaievaluationservice.service.EvaluationEventPublisher newEvaluationEventPublisher;
    private final ObjectMapper objectMapper;
    
    /**
     * 동기식 평가 처리 메서드 (기존 호환성 유지)
     */
    @Transactional
    public void processEvaluation(MissionCompletedEvent event) {
        processEvaluationInternal(event);
    }
    
    /**
     * 비동기식 평가 처리 메서드 (새로운 방식)
     * Kafka Consumer가 블로킹되지 않도록 비동기로 처리
     */
    @Async("evaluationTaskExecutor")
    @Transactional
    public CompletableFuture<Void> processEvaluationAsync(MissionCompletedEvent event) {
        try {
            processEvaluationInternal(event);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException e) {
            log.error("Error during async evaluation for missionAttemptId: {}", event.getMissionAttemptId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 실제 평가 처리 로직 (공통 부분)
     */
    private void processEvaluationInternal(MissionCompletedEvent event) {
        String missionAttemptId = event.getMissionAttemptId();
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        logEvaluationStart(event, processingStartTime);
        
        if (isDuplicateEvaluation(missionAttemptId)) {
            return;
        }

        AIEvaluation evaluation = createAndSaveInitialEvaluation(event);
        recordStatusChange(evaluation, null, AIEvaluation.EvaluationStatus.PENDING, "Initial evaluation request");
        
        try {
            log.info("=== EVALUATION STATUS: PROCESSING ===");
            updateEvaluationStatus(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, "Starting AI evaluation");
            
            try {
                newEvaluationEventPublisher.publishEvaluationStartedWithDefaults(
                    evaluation.getId().toString(), 
                    event.getMissionId(), 
                    missionAttemptId, 
                    parseUserIdSafely(event.getUserId())
                ).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Evaluation started event publishing interrupted, but evaluation continues: {}", e.getMessage());
            } catch (java.util.concurrent.ExecutionException e) {
                log.warn("Execution error publishing evaluation started event, but evaluation continues: {}", e.getMessage());
                
                log.info("Evaluation started event published successfully: evaluationId={}, missionId={}", 
                    evaluation.getId(), event.getMissionId());
            } catch (org.springframework.kafka.KafkaException e) {
                log.warn("Kafka error publishing evaluation started event, but evaluation continues: {}", e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Runtime error publishing evaluation started event, but evaluation continues: {}", e.getMessage());
            }
            
            
            // AI 평가 수행 - 실제 데이터로 평가
            log.info("=== CALLING GEMINI AI EVALUATION WITH REAL DATA ===");
            log.info("Code length: {} characters", event.getCode() != null ? event.getCode().length() : 0);
            log.info("Real execution data available: {}", event.getRealExecutionData() != null);
            
            EvaluationResultDTO result;
            if (event.getRealExecutionData() != null) {
                // 실제 실행 데이터가 있으면 새로운 평가 방식 사용
                log.info("실제 데이터 기반 평가 시작: 명령어수={}", 
                        event.getRealExecutionData().getStatistics() != null ? 
                        event.getRealExecutionData().getStatistics().getTotalCommands() : 0);
                result = geminiEvaluationService.evaluateCodeWithRealData(event);
            } else {
                log.warn("실제 데이터 비어있음 - fallback to legacy evaluation");
                result = geminiEvaluationService.evaluateCode(
                    event.getCode(), 
                    event.getMissionType(),
                    event.getMissionId()
                );
            }
            
            log.info("=== AI EVALUATION COMPLETED ===");
            log.info("Overall Score: {}", result.getOverallScore());
            
            // 결과 저장 및 이벤트 발행
            completeEvaluation(evaluation, result, event, processingStartTime);
            
        } catch (IllegalArgumentException e) {
            log.error("=== EVALUATION VALIDATION ERROR ===");
            log.error("Invalid input data for missionAttemptId: {} - Error: {}", missionAttemptId, e.getMessage(), e);
            handleEvaluationError(evaluation, event, e, VALIDATION_ERROR_CODE, INVALID_INPUT_DATA_PREFIX + e.getMessage());
            
        } catch (RuntimeException e) {
            log.error("=== EVALUATION RUNTIME ERROR ===");
            log.error("Runtime error during evaluation for missionAttemptId: {} - Error: {}", missionAttemptId, e.getMessage(), e);
            
            String errorDetail = buildErrorDetail(e);
            handleEvaluationError(evaluation, event, e, RUNTIME_ERROR_CODE, errorDetail);
        }
    }
    
    /**
     * 초기 평가 객체 생성
     */
    private AIEvaluation createInitialEvaluation(MissionCompletedEvent event) {
        log.info("=== createInitialEvaluation DEBUG ===");
        log.info("Event userId: {}", event.getUserId());
        log.info("Event missionId: {}", event.getMissionId());
        log.info("Event missionAttemptId: {}", event.getMissionAttemptId());
        
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setMissionAttemptId(event.getMissionAttemptId());
        evaluation.setMissionId(event.getMissionId());  // String 타입으로 설정
        
        // Safe conversion of userId string to Long with validation
        Long userId = parseUserIdSafely(event.getUserId());
        evaluation.setUserId(userId);
        
        evaluation.setMissionType(event.getMissionType());
        evaluation.setMissionTitle(event.getMissionTitle());
        evaluation.setSubmittedCode(event.getCode());
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion(EvaluationConstants.AI_MODEL_VERSION_GEMINI_20_FLASH);
        
        log.info("Evaluation object after setting - userId: {}, missionId: {}, missionAttemptId: {}", 
                evaluation.getUserId(), evaluation.getMissionId(), evaluation.getMissionAttemptId());
        
        return evaluation;
    }
    
    /**
     * Safely parse userId string to Long with proper error handling
     */
    private Long parseUserIdSafely(String userIdStr) {
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            log.warn("UserId is null or empty, using default value 0");
            return 0L;
        }
        
        try {
            return Long.parseLong(userIdStr.trim());
        } catch (NumberFormatException e) {
            log.error("Invalid userId format: '{}', using default value 0", userIdStr);
            return 0L;
        }
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
            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
                log.error("Failed to serialize evaluation result to JSON for {}: {}", missionAttemptId, jsonEx.getMessage());
                throw new JsonSerializationException("JSON serialization failed", jsonEx);
            } catch (Exception jsonEx) {
                log.error("Unexpected error during JSON serialization for {}: {}", missionAttemptId, jsonEx.getMessage());
                throw new JsonSerializationException("JSON serialization failed", jsonEx);
            }
            
            // 데이터베이스 저장 시도
            try {
                evaluation.setEvaluationResult(resultJson);
                evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
                evaluation = aiEvaluationRepository.save(evaluation);
                log.info("Evaluation result saved successfully for {}", missionAttemptId);
            } catch (org.springframework.dao.DataAccessException dbEx) {
                log.error("Database access error while saving evaluation for {}: {}", missionAttemptId, dbEx.getMessage());
                throw new DatabaseOperationException("Database save failed", dbEx);
            } catch (Exception dbEx) {
                log.error("Unexpected database error while saving evaluation for {}: {}", missionAttemptId, dbEx.getMessage());
                throw new DatabaseOperationException("Database save failed", dbEx);
            }
            
            // 평가 요약 생성 시도
            try {
                log.info("=== CREATING EVALUATION SUMMARY ===");
                createEvaluationSummary(evaluation, result, event);
                log.info("Evaluation summary created successfully for {}", missionAttemptId);
            } catch (IllegalArgumentException summaryEx) {
                log.error("Invalid data for evaluation summary creation for {}: {}", missionAttemptId, summaryEx.getMessage());
                // Summary 생성 실패는 전체 평가를 실패시키지 않음
            } catch (Exception summaryEx) {
                log.error(EvaluationConstants.ERROR_CREATING_SUMMARY, missionAttemptId, summaryEx.getMessage());
                // Summary 생성 실패는 전체 평가를 실패시키지 않음
            }
            
            // 상태 변경 이력 기록
            try {
                recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                                 AIEvaluation.EvaluationStatus.COMPLETED, EvaluationConstants.SUCCESS_EVALUATION_COMPLETED);
            } catch (org.springframework.dao.DataAccessException historyEx) {
                log.warn("Database error recording status change history for {}: {}", missionAttemptId, historyEx.getMessage());
                // 이력 기록 실패는 전체 프로세스를 중단시키지 않음
            } catch (Exception historyEx) {
                log.warn("Unexpected error recording status change history for {}: {}", missionAttemptId, historyEx.getMessage());
                // 이력 기록 실패는 전체 프로세스를 중단시키지 않음
            }
            
            publishEvaluationCompletedEventWithErrorHandling(evaluation, result, event, processingStartTime, missionAttemptId);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            log.info("=== EVALUATION PROCESS COMPLETED ===");
            log.info("MissionAttemptId: {}", missionAttemptId);
            log.info("Total processing time: {} ms", processingTimeMs);
            log.info("Final score: {}", result.getOverallScore());
            
        } catch (Exception e) {
            log.error("=== EVALUATION COMPLETION ERROR ===");
            log.error("Error during evaluation completion for {}: {}", missionAttemptId, e.getMessage(), e);
            String errorMessage = e instanceof RuntimeException ? 
                "Failed to complete evaluation: " + e.getMessage() : 
                "Unexpected error during completion: " + e.getMessage();
            failEvaluationWithEvent(evaluation, event, errorMessage);
        }
    }
    
    private void createEvaluationSummary(AIEvaluation evaluation, EvaluationResultDTO result, MissionCompletedEvent event) {
        if (result == null) {
            throw new IllegalArgumentException("EvaluationResultDTO cannot be null");
        }
        
        try {
            EvaluationSummary summary = new EvaluationSummary();
            summary.setUserId(parseUserIdSafely(event.getUserId()));
            summary.setMissionId(event.getMissionId());
            summary.setMissionAttemptId(event.getMissionAttemptId());
            summary.setMissionTitle(event.getMissionTitle());
            summary.setMissionType(event.getMissionType());
            
            // Null-safe score setting
            summary.setOverallScore(result.getOverallScore() != null ? result.getOverallScore() : 0);
            summary.setCodeQualityScore(result.getCodeQuality() != null ? result.getCodeQuality().getScore() : null);
            summary.setSecurityScore(result.getSecurity() != null ? result.getSecurity().getScore() : null);
            summary.setStyleScore(result.getStyle() != null ? result.getStyle().getScore() : null);
            
            // 추가 DevOps 평가 지표들 설정 (이전에 누락되었던 필드들)
            summary.setBestPracticeScore(result.getBestPracticeScore());
            summary.setReliabilityScore(result.getReliabilityScore());
            summary.setSecurityRiskLevel(result.getSecurityRiskLevel());
            summary.setEfficiencyGrade(result.getEfficiencyGrade());
            
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
            
        } catch (NumberFormatException e) {
            log.error("Invalid number format in evaluation summary for {}: {}", event.getMissionAttemptId(), e.getMessage());
            throw new EvaluationException("Failed to create evaluation summary", e);
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Database error creating evaluation summary for {}: {}", event.getMissionAttemptId(), e.getMessage());
            throw new EvaluationException("Failed to create evaluation summary", e);
        } catch (Exception e) {
            log.error(EvaluationConstants.ERROR_CREATING_SUMMARY, event.getMissionAttemptId(), e.getMessage());
            throw new EvaluationException("Failed to create evaluation summary", e);
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
                    .evaluationStatus(EvaluationConstants.STATUS_COMPLETED)
                    .completedAt(LocalDateTime.now())
                    .processingTimeMs(processingTimeMs);

            int overallScore = result.getOverallScore();
            eventBuilder.correctnessScore(Math.max(MIN_SCORE_THRESHOLD, Math.min(MAX_SCORE_THRESHOLD, overallScore)));
            eventBuilder.efficiencyScore(Math.max(MIN_SCORE_THRESHOLD, Math.min(MAX_SCORE_THRESHOLD, overallScore - 5)));
            eventBuilder.qualityScore(Math.max(MIN_SCORE_THRESHOLD, Math.min(MAX_SCORE_THRESHOLD, overallScore + 5)));
            
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
                        .commandsExecuted(stats.getCommandSuccessCount() + stats.getCommandFailureCount())
                        .significantCommands(stats.getCommandSuccessCount())
                        .errorCommands(stats.getCommandFailureCount())
                        .totalExecutionTimeMs(stats.getTotalExecutionTime())
                        .stampsEarned(calculateStampsEarned(extractScoreFromResult(evaluation.getEvaluationResult())))
                        .pointsAwarded(calculatePointsAwarded(extractScoreFromResult(evaluation.getEvaluationResult())))
                        .averageCpuUsage(stats.getAverageCpuUsage())
                        .maxCpuUsage(stats.getMaxCpuUsage())
                        .averageMemoryUsage(stats.getAverageMemoryUsage())
                        .maxMemoryUsage(stats.getMaxMemoryUsage());
            }

            EvaluationCompletedEvent completedEvent = eventBuilder.build();
            
            log.info("=== KAFKA EVENT PUBLISHING ===");
            log.info("Publishing evaluation.completed event for missionAttemptId: {}", event.getMissionAttemptId());
            log.info("Event contains: overallScore={}, processingTime={}ms", result.getOverallScore(), processingTimeMs);
            
            evaluationEventPublisher.publishEvaluationCompleted(completedEvent);

            log.info("=== KAFKA EVENT PUBLISHED SUCCESSFULLY ===");
            log.info("Evaluation completed event published for missionAttemptId: {}", event.getMissionAttemptId());

        } catch (org.springframework.kafka.KafkaException e) {
            log.error("=== KAFKA PUBLISH ERROR ===");
            log.error("Kafka error publishing evaluation completed event for missionAttemptId: {}", 
                     event.getMissionAttemptId(), e);
            // 이벤트 발행 실패는 전체 평가를 실패시키지 않음
        } catch (Exception e) {
            log.error("=== UNEXPECTED KAFKA ERROR ===");
            log.error("Unexpected error publishing evaluation completed event for missionAttemptId: {}", 
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
        } catch (org.springframework.kafka.KafkaException e) {
            log.error("Kafka error publishing evaluation failed event for missionAttemptId: {}", 
                     event.getMissionAttemptId(), e);
        } catch (Exception e) {
            log.error("Unexpected error publishing evaluation failed event for missionAttemptId: {}", 
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
            } catch (org.springframework.dao.DataAccessException dbEx) {
                log.error("Critical: Database error saving failure status for {}: {}", missionAttemptId, dbEx.getMessage());
            } catch (Exception dbEx) {
                log.error("Critical: Unexpected error saving failure status for {}: {}", missionAttemptId, dbEx.getMessage());
            }
            
            try {
                recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                                 AIEvaluation.EvaluationStatus.FAILED, truncatedErrorMessage);
            } catch (org.springframework.dao.DataAccessException historyEx) {
                log.warn("Database error recording failure status change history for {}: {}", missionAttemptId, historyEx.getMessage());
            } catch (Exception historyEx) {
                log.warn("Unexpected error recording failure status change history for {}: {}", missionAttemptId, historyEx.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Critical error while failing evaluation for {}: {}", missionAttemptId, e.getMessage());
        }
    }

    /**
     * 평가 실패 이벤트 발행 헬퍼
     */
    private void publishEvaluationFailedEvent(AIEvaluation evaluation, MissionCompletedEvent event, 
                                             String errorCode, String errorMessage, Integer retryAttempt) {
        try {
            newEvaluationEventPublisher.publishEvaluationFailedWithDefaults(
                evaluation.getId().toString(), 
                event.getMissionId(), 
                event.getMissionAttemptId(), 
                parseUserIdSafely(event.getUserId()),
                errorMessage,
                retryAttempt
            ).get();
            
            log.info("Evaluation failed event published successfully: evaluationId={}, errorCode={}", 
                evaluation.getId(), errorCode);
        } catch (Exception e) {
            log.warn("Failed to publish evaluation failed event: {}", e.getMessage());
        }
    }

    /**
     * 평가 재시도 요청 이벤트 발행 헬퍼
     */
    private void publishEvaluationRetryRequestedEvent(AIEvaluation evaluation, MissionCompletedEvent event,
                                                     String originalEvaluationId, Integer retryAttempt, 
                                                     String previousFailureReason) {
        try {
            newEvaluationEventPublisher.publishEvaluationRetryRequestedWithDefaults(
                evaluation.getId().toString(),
                originalEvaluationId,
                event.getMissionId(),
                event.getMissionAttemptId(),
                parseUserIdSafely(event.getUserId()),
                retryAttempt,
                previousFailureReason
            ).get();
            
            log.info("Evaluation retry requested event published successfully: evaluationId={}, retryAttempt={}", 
                evaluation.getId(), retryAttempt);
        } catch (Exception e) {
            log.warn("Failed to publish evaluation retry requested event: {}", e.getMessage());
        }
    }

    /**
     * 평가 재시도 완료 이벤트 발행 헬퍼
     */
    private void publishEvaluationRetryCompletedEvent(AIEvaluation evaluation, MissionCompletedEvent event,
                                                     String originalEvaluationId, Integer retryAttempt,
                                                     boolean success, Integer finalScore, String failureReason,
                                                     LocalDateTime retryStartedAt) {
        try {
            if (success) {
                newEvaluationEventPublisher.publishEvaluationRetryCompletedSuccessWithDefaults(
                    evaluation.getId().toString(),
                    originalEvaluationId,
                    event.getMissionId(),
                    event.getMissionAttemptId(),
                    parseUserIdSafely(event.getUserId()),
                    retryAttempt,
                    finalScore,
                    retryStartedAt
                ).get();
            } else {
                newEvaluationEventPublisher.publishEvaluationRetryCompletedFailedWithDefaults(
                    evaluation.getId().toString(),
                    originalEvaluationId,
                    event.getMissionId(),
                    event.getMissionAttemptId(),
                    parseUserIdSafely(event.getUserId()),
                    retryAttempt,
                    failureReason,
                    retryStartedAt
                ).get();
            }
            
            log.info("Evaluation retry completed event published successfully: evaluationId={}, success={}", 
                evaluation.getId(), success);
        } catch (Exception e) {
            log.warn("Failed to publish evaluation retry completed event: {}", e.getMessage());
        }
    }
    
    protected int extractScoreFromResult(String evaluationResult) {
        if (evaluationResult == null || evaluationResult.trim().isEmpty()) {
            return 0;
        }
        try {
            // JSON에서 총점 추출 (간단한 파싱)
            if (evaluationResult.contains("\"totalScore\"")) {
                String scoreStr = evaluationResult.substring(evaluationResult.indexOf("\"totalScore\""));
                scoreStr = scoreStr.substring(scoreStr.indexOf(":") + 1);
                scoreStr = scoreStr.substring(0, scoreStr.indexOf(",")).trim();
                return Integer.parseInt(scoreStr);
            }
            return 70;
        } catch (Exception e) {
            log.warn("Failed to extract score from evaluation result: {}", e.getMessage());
            return 70;
        }
    }
    
    protected int calculateStampsEarned(int score) {
        if (score >= 85) return 3;
        if (score >= 70) return 2;
        if (score >= 60) return 1;
        return 0;
    }
    
    protected int calculatePointsAwarded(int score) {
        return score * 10;
    }
    
    /**
     * Helper Methods for processEvaluationInternal
     */
    private void logEvaluationStart(MissionCompletedEvent event, LocalDateTime processingStartTime) {
        log.info("=== AI EVALUATION PROCESS STARTED ===");
        log.info("MissionAttemptId: {}", event.getMissionAttemptId());
        log.info("UserId: {}, MissionId: {}", event.getUserId(), event.getMissionId());
        log.info("MissionType: {}, MissionTitle: {}", event.getMissionType(), event.getMissionTitle());
        log.info("Processing started at: {}", processingStartTime);
    }
    
    private boolean isDuplicateEvaluation(String missionAttemptId) {
        if (aiEvaluationRepository.existsByMissionAttemptId(missionAttemptId)) {
            log.warn("=== DUPLICATE EVALUATION DETECTED ===");
            log.warn("Evaluation already exists for missionAttemptId: {}", missionAttemptId);
            return true;
        }
        return false;
    }
    
    private AIEvaluation createAndSaveInitialEvaluation(MissionCompletedEvent event) {
        AIEvaluation evaluation = createInitialEvaluation(event);
        
        log.info("=== BEFORE SAVE DEBUG ===");
        log.info("Evaluation before save - userId: {}, missionId: {}, missionAttemptId: {}", 
                evaluation.getUserId(), evaluation.getMissionId(), evaluation.getMissionAttemptId());
        
        evaluation = aiEvaluationRepository.save(evaluation);
        
        log.info("=== AFTER SAVE DEBUG ===");
        log.info("Evaluation after save - userId: {}, missionId: {}, missionAttemptId: {}", 
                evaluation.getUserId(), evaluation.getMissionId(), evaluation.getMissionAttemptId());
        
        return evaluation;
    }
    
    private void handleEvaluationError(AIEvaluation evaluation, MissionCompletedEvent event, 
                                     Exception exception, String errorCode, String errorMessage) {
        failEvaluationWithEvent(evaluation, event, errorMessage);
        publishEvaluationFailedEvent(evaluation, event, errorCode, errorMessage, 0);
    }
    
    private String buildErrorDetail(RuntimeException e) {
        String errorDetail = String.format(RUNTIME_ERROR_PREFIX + "%s", e.getMessage());
        if (e.getCause() != null) {
            errorDetail += String.format(" (Caused by: %s)", e.getCause().getMessage());
        }
        return errorDetail;
    }
    
    private String serializeEvaluationResult(EvaluationResultDTO result, String missionAttemptId) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            if (resultJson == null || resultJson.trim().isEmpty()) {
                throw new IllegalStateException("Serialized JSON is null or empty");
            }
            log.debug("Evaluation result serialized successfully, size: {} characters", resultJson.length());
            return resultJson;
        } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
            log.error("Failed to serialize evaluation result to JSON for {}: {}", missionAttemptId, jsonEx.getMessage());
            throw new JsonSerializationException("JSON serialization failed", jsonEx);
        } catch (Exception jsonEx) {
            log.error("Unexpected error during JSON serialization for {}: {}", missionAttemptId, jsonEx.getMessage());
            throw new JsonSerializationException("JSON serialization failed", jsonEx);
        }
    }
    
    private AIEvaluation saveEvaluationResult(AIEvaluation evaluation, String resultJson, String missionAttemptId) {
        try {
            evaluation.setEvaluationResult(resultJson);
            evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            AIEvaluation savedEvaluation = aiEvaluationRepository.save(evaluation);
            log.info("Evaluation result saved successfully for {}", missionAttemptId);
            return savedEvaluation;
        } catch (org.springframework.dao.DataAccessException dbEx) {
            log.error("Database access error while saving evaluation for {}: {}", missionAttemptId, dbEx.getMessage());
            throw new DatabaseOperationException("Database save failed", dbEx);
        } catch (Exception dbEx) {
            log.error("Unexpected database error while saving evaluation for {}: {}", missionAttemptId, dbEx.getMessage());
            throw new DatabaseOperationException("Database save failed", dbEx);
        }
    }
    
    private void createEvaluationSummaryWithErrorHandling(AIEvaluation evaluation, EvaluationResultDTO result, 
                                                         MissionCompletedEvent event, String missionAttemptId) {
        try {
            log.info("=== CREATING EVALUATION SUMMARY ===");
            createEvaluationSummary(evaluation, result, event);
            log.info("Evaluation summary created successfully for {}", missionAttemptId);
        } catch (IllegalArgumentException summaryEx) {
            log.error("Invalid data for evaluation summary creation for {}: {}", missionAttemptId, summaryEx.getMessage());
        } catch (Exception summaryEx) {
            log.error(EvaluationConstants.ERROR_CREATING_SUMMARY, missionAttemptId, summaryEx.getMessage());
        }
    }
    
    private void recordStatusChangeWithErrorHandling(AIEvaluation evaluation, 
                                                    AIEvaluation.EvaluationStatus previousStatus,
                                                    AIEvaluation.EvaluationStatus newStatus, 
                                                    String reason, String missionAttemptId) {
        try {
            recordStatusChange(evaluation, previousStatus, newStatus, reason);
        } catch (org.springframework.dao.DataAccessException historyEx) {
            log.warn("Database error recording status change history for {}: {}", missionAttemptId, historyEx.getMessage());
        } catch (Exception historyEx) {
            log.warn("Unexpected error recording status change history for {}: {}", missionAttemptId, historyEx.getMessage());
        }
    }
    
    private void publishEvaluationCompletedEventWithErrorHandling(AIEvaluation evaluation, EvaluationResultDTO result, 
                                                                MissionCompletedEvent event, LocalDateTime processingStartTime, 
                                                                String missionAttemptId) {
        try {
            log.info("=== PUBLISHING EVALUATION COMPLETED EVENT ===");
            publishEvaluationCompletedEvent(evaluation, result, event, processingStartTime);
        } catch (org.springframework.kafka.KafkaException eventEx) {
            log.error("Kafka error publishing evaluation completed event for {}: {}", missionAttemptId, eventEx.getMessage());
        } catch (Exception eventEx) {
            log.error("Unexpected error publishing evaluation completed event for {}: {}", missionAttemptId, eventEx.getMessage());
        }
    }
    
}