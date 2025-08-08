package ac.su.kdt.beaievaluationservice.kafka.publisher;

import ac.su.kdt.beaievaluationservice.kafka.event.EvaluationCompletedEvent;

// AI 평가 관련 이벤트를 Kafka로 발행하는 인터페이스
public interface EvaluationEventPublisher {
    
    /**
     * 평가 완료 이벤트를 evaluation.completed 토픽으로 발행한다
     * @param event 평가 완료 이벤트 데이터
     */
    void publishEvaluationCompleted(EvaluationCompletedEvent event);
    
    /**
     * 평가 실패 이벤트를 evaluation.completed 토픽으로 발행한다
     * @param missionAttemptId 미션 시도 ID
     * @param userId 사용자 ID
     * @param errorMessage 오류 메시지
     */
    void publishEvaluationFailed(String missionAttemptId, String userId, String errorMessage);
}