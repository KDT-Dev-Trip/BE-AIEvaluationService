package ac.su.kdt.beaievaluationservice.client;

import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Prometheus 메트릭 조회 클라이언트 인터페이스
public interface PrometheusClient {
    
    /**
     * 지정된 시간 구간의 메트릭 시계열 데이터를 조회한다
     * @param query Prometheus PromQL 쿼리
     * @param startTime 조회 시작 시간
     * @param endTime 조회 종료 시간  
     * @param step 데이터 간격 (초)
     * @return 메트릭명별 시계열 데이터 맵
     */
    Map<String, List<MetricDataPoint>> queryRange(
        String query, 
        LocalDateTime startTime, 
        LocalDateTime endTime, 
        int step
    );
    
    /**
     * 미션 시도 ID로 필터링된 메트릭 조회
     * @param metricName 메트릭명 (예: cpu_usage, memory_usage)
     * @param missionAttemptId 미션 시도 ID
     * @param startTime 조회 시작 시간
     * @param endTime 조회 종료 시간
     * @param step 데이터 간격 (초)
     * @return 시계열 데이터 리스트
     */
    List<MetricDataPoint> queryMissionMetrics(
        String metricName,
        String missionAttemptId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int step
    );
}