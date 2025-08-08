package ac.su.kdt.beaievaluationservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

// Prometheus query_range API 응답 DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusQueryResponse {
    
    private String status;
    private QueryData data;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryData {
        private String resultType;
        private List<MetricResult> result;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricResult {
        private MetricInfo metric;
        private List<Object[]> values; // [timestamp, value] 형태의 시계열 데이터
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricInfo {
        @JsonProperty("__name__")
        private String name;
        private String job;
        private String instance;
        @JsonProperty("mission_attempt_id")
        private String missionAttemptId;
        @JsonProperty("user_id")
        private String userId;
    }
}