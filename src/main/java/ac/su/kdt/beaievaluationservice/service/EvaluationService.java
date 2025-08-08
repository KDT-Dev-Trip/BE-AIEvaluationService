package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.analyzer.MetricAnalyzer;
import ac.su.kdt.beaievaluationservice.analyzer.dto.MetricSummaryDto;
import ac.su.kdt.beaievaluationservice.client.PrometheusClient;
import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
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
    private final PrometheusClient prometheusClient;
    private final MetricAnalyzer metricAnalyzer;
    private final EvaluationEventPublisher evaluationEventPublisher;
    private final ObjectMapper objectMapper;
    private final MissionTempSaveService missionTempSaveService;
    
    @Async
    @Transactional
    public void processEvaluationAsync(MissionCompletedEvent event) {
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
            
            // 1. Prometheus 메트릭 수집 (시간 구간이 있는 경우에만)
            MetricSummaryDto performanceSummary = collectAndAnalyzeMetrics(event);
            
            // 2. AI 코드 평가 수행
            EvaluationResultDTO result = geminiEvaluationService.evaluateCode(
                event.getCode(), 
                event.getMissionType(),
                event.getMissionId()
            );
            
            // 3. 평가 완료 처리 및 이벤트 발행
            completeEvaluation(evaluation, result, event, performanceSummary, processingStartTime);
            
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
        evaluation.setAiModelVersion("gemini-1.5-pro");
        
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
                                   MissionCompletedEvent event, MetricSummaryDto performanceSummary,
                                   LocalDateTime processingStartTime) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            evaluation.setEvaluationResult(resultJson);
            evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            aiEvaluationRepository.save(evaluation);
            
            createEvaluationSummary(evaluation, result, event);
            recordStatusChange(evaluation, AIEvaluation.EvaluationStatus.PROCESSING, 
                             AIEvaluation.EvaluationStatus.COMPLETED, "Evaluation completed successfully");
            
            // 평가 완료 이벤트 발행
            publishEvaluationCompletedEvent(evaluation, result, event, performanceSummary, processingStartTime);
            
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
     * Prometheus에서 메트릭을 수집하고 성능 분석을 수행한다
     * 시간 구간 정보가 없으면 메트릭 수집을 건너뛴다
     */
    private MetricSummaryDto collectAndAnalyzeMetrics(MissionCompletedEvent event) {
        // 시간 구간이 없으면 메트릭 수집 건너뛰기
        if (event.getStartAt() == null || event.getEndAt() == null) {
            log.info("No time range provided for missionAttemptId: {}, skipping metrics collection", 
                    event.getMissionAttemptId());
            return null;
        }

        try {
            log.info("Collecting Prometheus metrics for missionAttemptId: {} from {} to {}", 
                    event.getMissionAttemptId(), event.getStartAt(), event.getEndAt());

            Map<String, List<MetricDataPoint>> metricsData = new HashMap<>();
            
            // 주요 메트릭들을 병렬로 수집 (15초 간격)
            int step = 15;
            
            // CPU 사용률 수집
            try {
                List<MetricDataPoint> cpuData = prometheusClient.queryMissionMetrics(
                    "cpu_usage", event.getMissionAttemptId(), 
                    event.getStartAt(), event.getEndAt(), step);
                if (!cpuData.isEmpty()) {
                    metricsData.put("cpu_usage", cpuData);
                    log.debug("Collected {} CPU data points", cpuData.size());
                }
            } catch (Exception e) {
                log.warn("Failed to collect CPU metrics: {}", e.getMessage());
            }

            // 메모리 사용률 수집
            try {
                List<MetricDataPoint> memoryData = prometheusClient.queryMissionMetrics(
                    "memory_usage", event.getMissionAttemptId(), 
                    event.getStartAt(), event.getEndAt(), step);
                if (!memoryData.isEmpty()) {
                    metricsData.put("memory_usage", memoryData);
                    log.debug("Collected {} memory data points", memoryData.size());
                }
            } catch (Exception e) {
                log.warn("Failed to collect memory metrics: {}", e.getMessage());
            }

            // 응답 시간 수집
            try {
                List<MetricDataPoint> responseTimeData = prometheusClient.queryMissionMetrics(
                    "response_time", event.getMissionAttemptId(), 
                    event.getStartAt(), event.getEndAt(), step);
                if (!responseTimeData.isEmpty()) {
                    metricsData.put("response_time", responseTimeData);
                    log.debug("Collected {} response time data points", responseTimeData.size());
                }
            } catch (Exception e) {
                log.warn("Failed to collect response time metrics: {}", e.getMessage());
            }

            // 메트릭이 수집되었으면 분석 수행
            if (!metricsData.isEmpty()) {
                MetricSummaryDto summary = metricAnalyzer.analyzeMissionPerformance(
                    event.getMissionAttemptId(), event.getUserId(), metricsData);
                log.info("Performance analysis completed for missionAttemptId: {}, grade: {}", 
                        event.getMissionAttemptId(), summary.getOverallGrade());
                return summary;
            }

            log.info("No metrics collected for missionAttemptId: {}", event.getMissionAttemptId());
            return null;

        } catch (Exception e) {
            log.error("Failed to collect and analyze metrics for missionAttemptId: {}", 
                     event.getMissionAttemptId(), e);
            return null;
        }
    }

    /**
     * 평가 완료 이벤트를 Kafka로 발행한다
     */
    private void publishEvaluationCompletedEvent(AIEvaluation evaluation, EvaluationResultDTO result, 
                                                MissionCompletedEvent event, MetricSummaryDto performanceSummary,
                                                LocalDateTime processingStartTime) {
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

            // 성능 분석 결과가 있으면 포함
            if (performanceSummary != null) {
                eventBuilder
                        .performanceGrade(performanceSummary.getOverallGrade().name())
                        .hasCpuIssues(performanceSummary.isHasCpuIssues())
                        .hasMemoryIssues(performanceSummary.isHasMemoryIssues())
                        .hasResponseTimeIssues(performanceSummary.isHasResponseTimeIssues())
                        .performanceSummary(performanceSummary.getPerformanceSummary());
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