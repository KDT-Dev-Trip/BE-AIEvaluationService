package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationStartedEventDTO;
import ac.su.kdt.beaievaluationservice.dto.EvaluationFailedEventDTO;
import ac.su.kdt.beaievaluationservice.dto.EvaluationRetryRequestedEventDTO;
import ac.su.kdt.beaievaluationservice.dto.EvaluationRetryCompletedEventDTO;
import ac.su.kdt.beaievaluationservice.util.KafkaLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 평가 서비스 이벤트 발행자
 * - 평가 관련 4가지 이벤트를 Kafka로 발행
 */
@Slf4j
@Service
public class EvaluationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaLogHelper kafkaLogHelper;

    public EvaluationEventPublisher(@Qualifier("objectKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
                                   KafkaLogHelper kafkaLogHelper) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaLogHelper = kafkaLogHelper;
    }

    private static final String EVALUATION_EVENTS_TOPIC = "evaluation-events";

    /**
     * 평가 시작 이벤트 발행
     */
    public CompletableFuture<Void> publishEvaluationStarted(String evaluationId, String missionId, String missionTitle,
                                                            String attemptId, Long userId, String userEmail, String userName,
                                                            String evaluationType, Integer commandLogCount, String s3BucketPath) {
        return CompletableFuture.runAsync(() -> {
            try {
                EvaluationStartedEventDTO event = EvaluationStartedEventDTO.createDefault(
                    evaluationId, missionId, missionTitle, attemptId, userId, userEmail, userName,
                    evaluationType, commandLogCount, s3BucketPath
                );
                
                kafkaTemplate.send(EVALUATION_EVENTS_TOPIC, "evaluation.started", event);
                
                log.info("🚀 [AI_SERVICE] Evaluation started event published successfully: evaluationId={}, missionId={}, userId={}", 
                    evaluationId, missionId, userId);
                
                kafkaLogHelper.logEventPublished(EVALUATION_EVENTS_TOPIC, "evaluation.started", "evaluation.started");
                    
            } catch (Exception e) {
                log.error("❌ [AI_SERVICE] Failed to publish evaluation started event: evaluationId={}", evaluationId, e);
                throw new RuntimeException("Failed to publish evaluation started event", e);
            }
        });
    }

    /**
     * 평가 시작 이벤트 발행 - 기본값 사용
     */
    public CompletableFuture<Void> publishEvaluationStartedWithDefaults(String evaluationId, String missionId, 
                                                                        String attemptId, Long userId) {
        return publishEvaluationStarted(evaluationId, missionId, "미션 평가", attemptId, userId, 
            "user" + userId + "@devtrip.com", "사용자" + userId, "AI_AUTOMATED", 0, null);
    }

    /**
     * 평가 실패 이벤트 발행
     */
    public CompletableFuture<Void> publishEvaluationFailed(String evaluationId, String missionId, String missionTitle,
                                                          String attemptId, Long userId, String userEmail, String userName,
                                                          String failureReason, String errorCode, String errorMessage,
                                                          Integer retryAttempt) {
        return CompletableFuture.runAsync(() -> {
            try {
                EvaluationFailedEventDTO event = EvaluationFailedEventDTO.createDefault(
                    evaluationId, missionId, missionTitle, attemptId, userId, userEmail, userName,
                    failureReason, errorCode, errorMessage, retryAttempt
                );
                
                kafkaTemplate.send(EVALUATION_EVENTS_TOPIC, "evaluation.failed", event);
                
                log.error("🚨 [AI_SERVICE] Evaluation failed event published: evaluationId={}, reason={}, retryAttempt={}", 
                    evaluationId, failureReason, retryAttempt);
                
                kafkaLogHelper.logEventPublished(EVALUATION_EVENTS_TOPIC, "evaluation.failed", "evaluation.failed");
                    
            } catch (Exception e) {
                log.error("❌ [AI_SERVICE] Failed to publish evaluation failed event: evaluationId={}", evaluationId, e);
                throw new RuntimeException("Failed to publish evaluation failed event", e);
            }
        });
    }

    /**
     * 평가 실패 이벤트 발행 - 기본값 사용
     */
    public CompletableFuture<Void> publishEvaluationFailedWithDefaults(String evaluationId, String missionId,
                                                                       String attemptId, Long userId, 
                                                                       String failureReason, Integer retryAttempt) {
        return publishEvaluationFailed(evaluationId, missionId, "미션 평가", attemptId, userId,
            "user" + userId + "@devtrip.com", "사용자" + userId, failureReason, 
            "EVAL_ERROR_" + System.currentTimeMillis(), "평가 프로세스 실패", retryAttempt);
    }

    /**
     * 평가 재시도 요청 이벤트 발행
     */
    public CompletableFuture<Void> publishEvaluationRetryRequested(String evaluationId, String originalEvaluationId,
                                                                  String missionId, String missionTitle, String attemptId,
                                                                  Long userId, String userEmail, String userName,
                                                                  Integer retryAttempt, String previousFailureReason,
                                                                  String retryTrigger) {
        return CompletableFuture.runAsync(() -> {
            try {
                EvaluationRetryRequestedEventDTO event = EvaluationRetryRequestedEventDTO.createDefault(
                    evaluationId, originalEvaluationId, missionId, missionTitle, attemptId, userId, userEmail, userName,
                    retryAttempt, previousFailureReason, retryTrigger
                );
                
                kafkaTemplate.send(EVALUATION_EVENTS_TOPIC, "evaluation.retry-requested", event);
                
                log.warn("🔄 [AI_SERVICE] Evaluation retry requested event published: evaluationId={}, retryAttempt={}, trigger={}", 
                    evaluationId, retryAttempt, retryTrigger);
                
                kafkaLogHelper.logEventPublished(EVALUATION_EVENTS_TOPIC, "evaluation.retry-requested", "evaluation.retry-requested");
                    
            } catch (Exception e) {
                log.error("❌ [AI_SERVICE] Failed to publish evaluation retry requested event: evaluationId={}", evaluationId, e);
                throw new RuntimeException("Failed to publish evaluation retry requested event", e);
            }
        });
    }

    /**
     * 평가 재시도 요청 이벤트 발행 - 기본값 사용  
     */
    public CompletableFuture<Void> publishEvaluationRetryRequestedWithDefaults(String evaluationId, String originalEvaluationId,
                                                                               String missionId, String attemptId, Long userId,
                                                                               Integer retryAttempt, String previousFailureReason) {
        return publishEvaluationRetryRequested(evaluationId, originalEvaluationId, missionId, "미션 평가", attemptId,
            userId, "user" + userId + "@devtrip.com", "사용자" + userId, retryAttempt, previousFailureReason, "AUTOMATIC");
    }

    /**
     * 평가 재시도 완료 이벤트 발행 - 성공
     */
    public CompletableFuture<Void> publishEvaluationRetryCompletedSuccess(String evaluationId, String originalEvaluationId,
                                                                         String missionId, String missionTitle, String attemptId,
                                                                         Long userId, String userEmail, String userName,
                                                                         Integer retryAttempt, Integer finalScore, Integer maxScore,
                                                                         LocalDateTime retryStartedAt, String evaluationEngine,
                                                                         String retryStrategy) {
        return CompletableFuture.runAsync(() -> {
            try {
                EvaluationRetryCompletedEventDTO event = EvaluationRetryCompletedEventDTO.createSuccessful(
                    evaluationId, originalEvaluationId, missionId, missionTitle, attemptId, userId, userEmail, userName,
                    retryAttempt, finalScore, maxScore, retryStartedAt, evaluationEngine, retryStrategy
                );
                
                kafkaTemplate.send(EVALUATION_EVENTS_TOPIC, "evaluation.retry-completed", event);
                
                log.info("✅ [AI_SERVICE] Evaluation retry completed successfully: evaluationId={}, finalScore={}/{}, retryAttempt={}", 
                    evaluationId, finalScore, maxScore, retryAttempt);
                
                kafkaLogHelper.logEventPublished(EVALUATION_EVENTS_TOPIC, "evaluation.retry-completed", "evaluation.retry-completed");
                    
            } catch (Exception e) {
                log.error("❌ [AI_SERVICE] Failed to publish evaluation retry completed success event: evaluationId={}", evaluationId, e);
                throw new RuntimeException("Failed to publish evaluation retry completed success event", e);
            }
        });
    }

    /**
     * 평가 재시도 완료 이벤트 발행 - 실패
     */
    public CompletableFuture<Void> publishEvaluationRetryCompletedFailed(String evaluationId, String originalEvaluationId,
                                                                        String missionId, String missionTitle, String attemptId,
                                                                        Long userId, String userEmail, String userName,
                                                                        Integer retryAttempt, String failureReason,
                                                                        LocalDateTime retryStartedAt, String evaluationEngine) {
        return CompletableFuture.runAsync(() -> {
            try {
                EvaluationRetryCompletedEventDTO event = EvaluationRetryCompletedEventDTO.createFailed(
                    evaluationId, originalEvaluationId, missionId, missionTitle, attemptId, userId, userEmail, userName,
                    retryAttempt, failureReason, retryStartedAt, evaluationEngine
                );
                
                kafkaTemplate.send(EVALUATION_EVENTS_TOPIC, "evaluation.retry-completed", event);
                
                log.error("❌ [AI_SERVICE] Evaluation retry completed with failure: evaluationId={}, reason={}, retryAttempt={}", 
                    evaluationId, failureReason, retryAttempt);
                
                kafkaLogHelper.logEventPublished(EVALUATION_EVENTS_TOPIC, "evaluation.retry-completed", "evaluation.retry-completed");
                    
            } catch (Exception e) {
                log.error("❌ [AI_SERVICE] Failed to publish evaluation retry completed failed event: evaluationId={}", evaluationId, e);
                throw new RuntimeException("Failed to publish evaluation retry completed failed event", e);
            }
        });
    }

    /**
     * 재시도 완료 이벤트 발행 - 성공 기본값
     */
    public CompletableFuture<Void> publishEvaluationRetryCompletedSuccessWithDefaults(String evaluationId, String originalEvaluationId,
                                                                                     String missionId, String attemptId, Long userId,
                                                                                     Integer retryAttempt, Integer finalScore,
                                                                                     LocalDateTime retryStartedAt) {
        return publishEvaluationRetryCompletedSuccess(evaluationId, originalEvaluationId, missionId, "미션 평가", attemptId,
            userId, "user" + userId + "@devtrip.com", "사용자" + userId, retryAttempt, finalScore, 100,
            retryStartedAt, "GEMINI", "IMMEDIATE");
    }

    /**
     * 재시도 완료 이벤트 발행 - 실패 기본값
     */
    public CompletableFuture<Void> publishEvaluationRetryCompletedFailedWithDefaults(String evaluationId, String originalEvaluationId,
                                                                                    String missionId, String attemptId, Long userId,
                                                                                    Integer retryAttempt, String failureReason,
                                                                                    LocalDateTime retryStartedAt) {
        return publishEvaluationRetryCompletedFailed(evaluationId, originalEvaluationId, missionId, "미션 평가", attemptId,
            userId, "user" + userId + "@devtrip.com", "사용자" + userId, retryAttempt, failureReason,
            retryStartedAt, "GEMINI");
    }
}