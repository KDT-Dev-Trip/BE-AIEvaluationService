package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
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
import org.springframework.scheduling.annotation.Async;
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
    private final MissionTempSaveService missionTempSaveService;
    
    @Transactional
    public void processEvaluationSync(MissionCompletedEvent event) {
        processEvaluation(event);
    }

    @Async
    public void processEvaluationAsync(MissionCompletedEvent event) {
        processEvaluation(event);
    }

    private void processEvaluation(MissionCompletedEvent event) {
        String missionAttemptId = event.getMissionAttemptId();
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        if (aiEvaluationRepository.existsByMissionAttemptId(missionAttemptId)) {
            log.warn("Evaluation already exists for missionAttemptId: {}", missionAttemptId);
            return;
        }

        // 임시 저장 데이터 확인 및 최종 완료 상태 업데이트
        Optional<MissionTempSave> tempSaveData = checkAndUpdateTempSave(missionAttemptId);
        
        AIEvaluation evaluation = createInitialEvaluation(event, tempSaveData);
        evaluation = aiEvaluationRepository.save(evaluation);
        
        recordStatusChange(evaluation, null, AIEvaluation.EvaluationStatus.PENDING, "Initial evaluation request");
        
        try {
            updateEvaluationStatus(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, "Starting AI evaluation");
            
            // 1. S3 저장소 주소와 Pre-Signed URL 준비
            String s3StorageUrl = event.getS3StorageUrl();
            String preSignedUrl = event.getS3PreSignedUrl();
            
            log.info("Using S3 storage URL: {} and Pre-Signed URL for evaluation", s3StorageUrl);
            
            // 3. Pre-Signed URL과 S3 주소를 평가 모델(제미나이)에 전달해 데이터 직접 읽게 함
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
            
            // 4. 모델 응답(점수/피드백)을 ai_evaluation 테이블에 저장하고 evaluation.completed 이벤트 발행
            completeEvaluation(evaluation, result, event, processingStartTime);
            
        } catch (Exception e) {
            failEvaluationWithEvent(evaluation, event, e.getMessage());
            log.error("Failed to process evaluation for missionAttemptId: {}", missionAttemptId, e);
        }
    }
    
    /**
     * 임시 저장 데이터 확인 및 최종 완료 상태 업데이트
     */
    private Optional<MissionTempSave> checkAndUpdateTempSave(String missionAttemptId) {
        Optional<MissionTempSave> tempSaveData = missionTempSaveService.getTempSave(missionAttemptId);
        
        if (tempSaveData.isPresent()) {
            MissionTempSave tempSave = tempSaveData.get();
            log.info("Found temp save data for missionAttemptId: {}, saveCount: {}, tempCodeLength: {}", 
                    missionAttemptId, tempSave.getSaveCount(), 
                    tempSave.getTempCode() != null ? tempSave.getTempCode().length() : 0);
            
            // 임시 저장 데이터를 최종 완료 상태로 업데이트
            missionTempSaveService.markAsCompleted(missionAttemptId);
            
            return tempSaveData;
        } else {
            log.info("No temp save data found for missionAttemptId: {}", missionAttemptId);
            return Optional.empty();
        }
    }

    /**
     * 임시 저장 데이터를 고려한 초기 평가 생성
     */
    private AIEvaluation createInitialEvaluation(MissionCompletedEvent event, Optional<MissionTempSave> tempSaveData) {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setMissionAttemptId(event.getMissionAttemptId());
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-2.0-flash-exp");
        
        // 임시 저장 데이터가 있다면 메타데이터 추가
        if (tempSaveData.isPresent()) {
            MissionTempSave tempSave = tempSaveData.get();
            // 임시 저장 횟수와 최종 완료 정보를 메타데이터로 기록
            String metadata = String.format("tempSaveCount:%d,finalCompleted:%s", 
                    tempSave.getSaveCount(), tempSave.getIsFinalCompleted());
            // 메타데이터를 evaluation에 저장할 수 있는 필드가 있다면 저장
            // 현재 스키마에는 없으므로 로그로만 기록
            log.info("Evaluation includes temp save metadata: {}", metadata);
        }
        
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
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            evaluation.setEvaluationResult(resultJson);
            evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            aiEvaluationRepository.save(evaluation);
            
            createEvaluationSummary(evaluation, result, event);
            recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                             AIEvaluation.EvaluationStatus.COMPLETED, "Evaluation completed successfully");
            
            // 평가 완료 이벤트 발행
            publishEvaluationCompletedEvent(evaluation, result, event, processingStartTime);
            
            log.info("Successfully completed evaluation for missionAttemptId: {}", evaluation.getMissionAttemptId());
            
        } catch (Exception e) {
            failEvaluationWithEvent(evaluation, event, "Failed to save evaluation result: " + e.getMessage());
        }
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
            evaluationEventPublisher.publishEvaluationCompleted(completedEvent);

            log.info("Published evaluation completed event for missionAttemptId: {}", event.getMissionAttemptId());

        } catch (Exception e) {
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
        evaluation.setStatus(AIEvaluation.EvaluationStatus.FAILED);
        evaluation.setErrorMessage(errorMessage);
        aiEvaluationRepository.save(evaluation);
        
        recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                         AIEvaluation.EvaluationStatus.FAILED, errorMessage);
    }
    
}