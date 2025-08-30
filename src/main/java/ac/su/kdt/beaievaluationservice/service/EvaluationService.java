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
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

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
        } catch (Exception e) {
            log.error("Async evaluation failed for missionAttemptId: {}", event.getMissionAttemptId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 실제 평가 처리 로직 (공통 부분)
     */
    private void processEvaluationInternal(MissionCompletedEvent event) {
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
        
        log.info("=== BEFORE SAVE DEBUG ===");
        log.info("Evaluation before save - userId: {}, missionId: {}, missionAttemptId: {}", 
                evaluation.getUserId(), evaluation.getMissionId(), evaluation.getMissionAttemptId());
        
        evaluation = aiEvaluationRepository.save(evaluation);
        
        log.info("=== AFTER SAVE DEBUG ===");
        log.info("Evaluation after save - userId: {}, missionId: {}, missionAttemptId: {}", 
                evaluation.getUserId(), evaluation.getMissionId(), evaluation.getMissionAttemptId());
        
        recordStatusChange(evaluation, null, AIEvaluation.EvaluationStatus.PENDING, "Initial evaluation request");
        
        try {
            log.info("=== EVALUATION STATUS: PROCESSING ===");
            updateEvaluationStatus(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, "Starting AI evaluation");
            
            // 🚀 실제 API 연동: 평가 시작 이벤트 발행
            try {
                newEvaluationEventPublisher.publishEvaluationStartedWithDefaults(
                    evaluation.getId().toString(), 
                    event.getMissionId(), 
                    missionAttemptId, 
                    Long.valueOf(event.getUserId())
                ).get(); // 동기 처리로 확실한 발행 보장
                
                log.info("✅ Evaluation started event published successfully: evaluationId={}, missionId={}", 
                    evaluation.getId(), event.getMissionId());
            } catch (Exception e) {
                log.warn("⚠️ Failed to publish evaluation started event, but evaluation continues: {}", e.getMessage());
            }
            
            // S3 데이터 준비
            String s3StorageUrl = event.getS3StorageUrl();
            String preSignedUrl = event.getS3PreSignedUrl();
            
            log.info("=== S3 DATA PREPARATION ===");
            log.info("S3 Storage URL: {}", s3StorageUrl);
            log.info("Pre-Signed URL available: {}", preSignedUrl != null && !preSignedUrl.isEmpty());
            
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
                // Fallback: 기존 방식 사용
                log.warn("실제 데이터 비어있음 - fallback to legacy evaluation");
                // 기존 평가 방식 (Fallback 용도)
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
            failEvaluationWithEvent(evaluation, event, "Invalid input data: " + e.getMessage());
            
            // 🚨 실제 API 연동: 평가 실패 이벤트 발행
            publishEvaluationFailedEvent(evaluation, event, "VALIDATION_ERROR", "Invalid input data: " + e.getMessage(), 0);
            
        } catch (RuntimeException e) {
            log.error("=== EVALUATION RUNTIME ERROR ===");
            log.error("Runtime error during evaluation for missionAttemptId: {} - Error: {}", missionAttemptId, e.getMessage(), e);
            
            String errorDetail = String.format("Runtime error: %s", e.getMessage());
            if (e.getCause() != null) {
                errorDetail += String.format(" (Caused by: %s)", e.getCause().getMessage());
            }
            failEvaluationWithEvent(evaluation, event, errorDetail);
            
            // 🚨 실제 API 연동: 평가 실패 이벤트 발행
            publishEvaluationFailedEvent(evaluation, event, "RUNTIME_ERROR", e.getMessage(), 0);
            
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
        log.info("=== createInitialEvaluation DEBUG ===");
        log.info("Event userId: {}", event.getUserId());
        log.info("Event missionId: {}", event.getMissionId());
        log.info("Event missionAttemptId: {}", event.getMissionAttemptId());
        
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setMissionAttemptId(event.getMissionAttemptId());
        evaluation.setMissionId(event.getMissionId());  // String 타입으로 설정
        evaluation.setUserId(Long.parseLong(event.getUserId()));        // String을 Long으로 변환
        evaluation.setMissionType(event.getMissionType());
        evaluation.setMissionTitle(event.getMissionTitle());
        evaluation.setSubmittedCode(event.getCode());
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-2.0-flash-exp");
        
        log.info("Evaluation object after setting - userId: {}, missionId: {}, missionAttemptId: {}", 
                evaluation.getUserId(), evaluation.getMissionId(), evaluation.getMissionAttemptId());
        
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
            } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
                log.error("Failed to serialize evaluation result to JSON for {}: {}", missionAttemptId, jsonEx.getMessage());
                throw new RuntimeException("JSON serialization failed", jsonEx);
            } catch (Exception jsonEx) {
                log.error("Unexpected error during JSON serialization for {}: {}", missionAttemptId, jsonEx.getMessage());
                throw new RuntimeException("JSON serialization failed", jsonEx);
            }
            
            // 데이터베이스 저장 시도
            try {
                evaluation.setEvaluationResult(resultJson);
                evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
                evaluation = aiEvaluationRepository.save(evaluation);
                log.info("Evaluation result saved successfully for {}", missionAttemptId);
            } catch (org.springframework.dao.DataAccessException dbEx) {
                log.error("Database access error while saving evaluation for {}: {}", missionAttemptId, dbEx.getMessage());
                throw new RuntimeException("Database save failed", dbEx);
            } catch (Exception dbEx) {
                log.error("Unexpected database error while saving evaluation for {}: {}", missionAttemptId, dbEx.getMessage());
                throw new RuntimeException("Database save failed", dbEx);
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
                log.error("Unexpected error creating evaluation summary for {}: {}", missionAttemptId, summaryEx.getMessage());
                // Summary 생성 실패는 전체 평가를 실패시키지 않음
            }
            
            // 상태 변경 이력 기록
            try {
                recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                                 AIEvaluation.EvaluationStatus.COMPLETED, "Evaluation completed successfully");
            } catch (org.springframework.dao.DataAccessException historyEx) {
                log.warn("Database error recording status change history for {}: {}", missionAttemptId, historyEx.getMessage());
                // 이력 기록 실패는 전체 프로세스를 중단시키지 않음
            } catch (Exception historyEx) {
                log.warn("Unexpected error recording status change history for {}: {}", missionAttemptId, historyEx.getMessage());
                // 이력 기록 실패는 전체 프로세스를 중단시키지 않음
            }
            
            // 평가 완료 이벤트 발행
            try {
                log.info("=== PUBLISHING EVALUATION COMPLETED EVENT ===");
                publishEvaluationCompletedEvent(evaluation, result, event, processingStartTime);
            } catch (org.springframework.kafka.KafkaException eventEx) {
                log.error("Kafka error publishing evaluation completed event for {}: {}", missionAttemptId, eventEx.getMessage());
                // 이벤트 발행 실패는 평가 자체를 실패시키지 않음
            } catch (Exception eventEx) {
                log.error("Unexpected error publishing evaluation completed event for {}: {}", missionAttemptId, eventEx.getMessage());
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
            summary.setUserId(Long.parseLong(event.getUserId()));
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
            
        } catch (NumberFormatException e) {
            log.error("Invalid number format in evaluation summary for {}: {}", event.getMissionAttemptId(), e.getMessage());
            throw new RuntimeException("Failed to create evaluation summary", e);
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Database error creating evaluation summary for {}: {}", event.getMissionAttemptId(), e.getMessage());
            throw new RuntimeException("Failed to create evaluation summary", e);
        } catch (Exception e) {
            log.error("Unexpected error creating evaluation summary for {}: {}", event.getMissionAttemptId(), e.getMessage());
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
                // 이 경우에도 예외를 다시 던지지 않음 (무한 루프 방지)
            } catch (Exception dbEx) {
                log.error("Critical: Unexpected error saving failure status for {}: {}", missionAttemptId, dbEx.getMessage());
                // 이 경우에도 예외를 다시 던지지 않음 (무한 루프 방지)
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
                Long.valueOf(event.getUserId()),
                errorMessage,
                retryAttempt
            ).get();
            
            log.info("✅ Evaluation failed event published successfully: evaluationId={}, errorCode={}", 
                evaluation.getId(), errorCode);
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish evaluation failed event: {}", e.getMessage());
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
                Long.valueOf(event.getUserId()),
                retryAttempt,
                previousFailureReason
            ).get();
            
            log.info("✅ Evaluation retry requested event published successfully: evaluationId={}, retryAttempt={}", 
                evaluation.getId(), retryAttempt);
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish evaluation retry requested event: {}", e.getMessage());
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
                    Long.valueOf(event.getUserId()),
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
                    Long.valueOf(event.getUserId()),
                    retryAttempt,
                    failureReason,
                    retryStartedAt
                ).get();
            }
            
            log.info("✅ Evaluation retry completed event published successfully: evaluationId={}, success={}", 
                evaluation.getId(), success);
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish evaluation retry completed event: {}", e.getMessage());
        }
    }
    
}