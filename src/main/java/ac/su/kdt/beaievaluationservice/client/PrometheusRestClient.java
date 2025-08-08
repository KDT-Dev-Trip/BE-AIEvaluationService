package ac.su.kdt.beaievaluationservice.client;

import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import ac.su.kdt.beaievaluationservice.client.dto.PrometheusQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

// Prometheus HTTP API를 통한 메트릭 조회 클라이언트 구현체
@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusRestClient implements PrometheusClient {
    
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    
    @Value("${prometheus.base.url:http://localhost:9090}")
    private String prometheusBaseUrl;

    @Override
    public Map<String, List<MetricDataPoint>> queryRange(
            String query, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            int step) {
        
        try {
            // Prometheus query_range API URL 구성
            HttpUrl url = HttpUrl.parse(prometheusBaseUrl + "/api/v1/query_range")
                .newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("start", String.valueOf(toEpochSeconds(startTime)))
                .addQueryParameter("end", String.valueOf(toEpochSeconds(endTime)))
                .addQueryParameter("step", step + "s")
                .build();

            Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

            log.debug("Prometheus query: {}", url);

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Prometheus query failed: " + response.code() + " " + response.message());
                }

                String responseBody = response.body().string();
                PrometheusQueryResponse queryResponse = objectMapper.readValue(responseBody, PrometheusQueryResponse.class);
                
                // 응답 데이터를 메트릭별로 그룹화하여 변환
                return convertToMetricDataPoints(queryResponse);
            }
            
        } catch (Exception e) {
            log.error("Failed to query Prometheus: query={}, startTime={}, endTime={}", 
                     query, startTime, endTime, e);
            throw new RuntimeException("Prometheus query failed", e);
        }
    }

    @Override
    public List<MetricDataPoint> queryMissionMetrics(
            String metricName,
            String missionAttemptId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int step) {
        
        // 미션 시도 ID로 필터링된 PromQL 쿼리 생성
        String query = String.format("%s{mission_attempt_id=\"%s\"}", metricName, missionAttemptId);
        
        Map<String, List<MetricDataPoint>> results = queryRange(query, startTime, endTime, step);
        
        // 해당 메트릭의 데이터만 반환 (첫 번째 결과)
        return results.values().stream()
                .findFirst()
                .orElse(Collections.emptyList());
    }
    
    /**
     * Prometheus 응답을 메트릭별 시계열 데이터 맵으로 변환
     */
    private Map<String, List<MetricDataPoint>> convertToMetricDataPoints(PrometheusQueryResponse response) {
        if (response.getData() == null || response.getData().getResult() == null) {
            return Collections.emptyMap();
        }

        return response.getData().getResult().stream()
            .collect(Collectors.toMap(
                result -> result.getMetric().getName(),
                result -> convertValuesToDataPoints(result.getValues()),
                (existing, replacement) -> existing // 중복 키 처리
            ));
    }
    
    /**
     * Prometheus values 배열을 MetricDataPoint 리스트로 변환
     */
    private List<MetricDataPoint> convertValuesToDataPoints(List<Object[]> values) {
        if (values == null) {
            return Collections.emptyList();
        }

        return values.stream()
            .map(this::convertValueToDataPoint)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Prometheus value 배열 [timestamp, value]을 MetricDataPoint로 변환
     */
    private MetricDataPoint convertValueToDataPoint(Object[] value) {
        try {
            if (value.length != 2) {
                log.warn("Invalid prometheus value format: expected [timestamp, value], got: {}", Arrays.toString(value));
                return null;
            }
            
            // timestamp는 Unix epoch seconds (double 형태)
            double timestampSeconds = ((Number) value[0]).doubleValue();
            Instant timestamp = Instant.ofEpochSecond((long) timestampSeconds);
            
            // value는 문자열로 전달되므로 파싱 필요
            double metricValue = Double.parseDouble(value[1].toString());
            
            return new MetricDataPoint(timestamp, metricValue);
            
        } catch (Exception e) {
            log.warn("Failed to parse prometheus value: {}", Arrays.toString(value), e);
            return null;
        }
    }
    
    /**
     * LocalDateTime을 Unix epoch seconds로 변환
     */
    private long toEpochSeconds(LocalDateTime dateTime) {
        return dateTime.toEpochSecond(ZoneOffset.UTC);
    }
}