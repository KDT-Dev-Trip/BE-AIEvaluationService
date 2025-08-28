package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 평가 재시도 및 상태 모니터링 서비스
// 이 서비스는 5분 이상 처리 중인 평가를 찾아 실패로 표시하고,
// 평가 상태 통계를 주기적으로 기록
// 3분마다 stuck된 평가를 찾아 실패로 표시하고, 5분마다 평가 상태 통계를 기록

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationRetryService {

    private final AIEvaluationRepository aiEvaluationRepository;
    private final EvaluationService evaluationService;
    private final EvaluationEventPublisher evaluationEventPublisher;

    @Scheduled(fixedDelay = 180000) // 3분마다 실행
    @Transactional
    public void retryStuckEvaluations() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
        
        List<AIEvaluation> stuckEvaluations = aiEvaluationRepository
            .findByStatusAndCreatedAtBefore(AIEvaluation.EvaluationStatus.PROCESSING, cutoffTime);
        
        if (!stuckEvaluations.isEmpty()) {
            log.warn("Found {} stuck evaluations older than 30 minutes", stuckEvaluations.size());
            
            for (AIEvaluation evaluation : stuckEvaluations) {
                try {
                    log.info("Marking stuck evaluation as failed: missionAttemptId={}", 
                            evaluation.getMissionAttemptId());
                    
                    evaluation.setStatus(AIEvaluation.EvaluationStatus.FAILED);
                    evaluation.setErrorMessage("Evaluation timeout - marked as failed by retry service");
                    aiEvaluationRepository.save(evaluation);
                    
                    // 평가 실패 이벤트 발행
                    try {
                        evaluationEventPublisher.publishEvaluationFailedWithDefaults(
                            evaluation.getId().toString(),
                            "UNKNOWN", // missionId가 없어서 기본값 사용
                            evaluation.getMissionAttemptId(),
                            evaluation.getUserId(),
                            "Evaluation timeout - marked as failed by retry service",
                            0 // 재시도 횟수
                        ).get();
                        log.info("Published evaluation failed event for stuck evaluation: missionAttemptId={}", 
                            evaluation.getMissionAttemptId());
                    } catch (Exception eventEx) {
                        log.warn("Failed to publish evaluation failed event for stuck evaluation: missionAttemptId={}, error={}", 
                            evaluation.getMissionAttemptId(), eventEx.getMessage());
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to mark stuck evaluation as failed: missionAttemptId={}", 
                            evaluation.getMissionAttemptId(), e);
                }
            }
        }
    }

    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    @Transactional(readOnly = true)
    public void logEvaluationStats() {
        long pendingCount = aiEvaluationRepository.findByStatus(AIEvaluation.EvaluationStatus.PENDING).size();
        long processingCount = aiEvaluationRepository.findByStatus(AIEvaluation.EvaluationStatus.PROCESSING).size();
        long completedCount = aiEvaluationRepository.findByStatus(AIEvaluation.EvaluationStatus.COMPLETED).size();
        long failedCount = aiEvaluationRepository.findByStatus(AIEvaluation.EvaluationStatus.FAILED).size();
        
        log.info("Evaluation Status Summary - Pending: {}, Processing: {}, Completed: {}, Failed: {}", 
                pendingCount, processingCount, completedCount, failedCount);
        
        if (processingCount > 10) {
            log.warn("High number of processing evaluations detected: {}", processingCount);
        }
        
        if (failedCount > 50) {
            log.error("High failure rate detected - Failed evaluations: {}", failedCount);
        }
    }
}