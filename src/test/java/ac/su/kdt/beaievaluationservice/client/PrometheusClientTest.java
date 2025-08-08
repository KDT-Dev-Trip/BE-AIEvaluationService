package ac.su.kdt.beaievaluationservice.client;

import ac.su.kdt.beaievaluationservice.client.dto.MetricDataPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

// PrometheusRestClient의 기능을 테스트하는 클래스입니다.
@DisplayName("PrometheusClient 테스트")
class PrometheusClientTest {

    private MockWebServer mockWebServer;
    private PrometheusRestClient prometheusClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        objectMapper = new ObjectMapper();
        OkHttpClient httpClient = new OkHttpClient();
        prometheusClient = new PrometheusRestClient(objectMapper, httpClient);
        
        // MockWebServer URL로 설정
        String baseUrl = mockWebServer.url("/").toString();
        ReflectionTestUtils.setField(prometheusClient, "prometheusBaseUrl", baseUrl);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("query_range API 호출 성공 시 시계열 데이터 반환")
    void queryRange_Success() throws Exception {
        // Given
        String mockResponse = """
            {
                "status": "success",
                "data": {
                    "resultType": "matrix",
                    "result": [
                        {
                            "metric": {
                                "__name__": "cpu_usage",
                                "job": "mission-app",
                                "instance": "localhost:8080",
                                "mission_attempt_id": "mission-123",
                                "user_id": "user-456"
                            },
                            "values": [
                                [1609459200, "50.5"],
                                [1609459260, "60.2"],
                                [1609459320, "45.8"]
                            ]
                        }
                    ]
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mockResponse));

        LocalDateTime startTime = LocalDateTime.of(2021, 1, 1, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2021, 1, 1, 1, 0, 0);

        // When
        Map<String, List<MetricDataPoint>> result = prometheusClient.queryRange(
            "cpu_usage{mission_attempt_id=\"mission-123\"}", 
            startTime, 
            endTime, 
            60
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result).containsKey("cpu_usage");
        
        List<MetricDataPoint> cpuData = result.get("cpu_usage");
        assertThat(cpuData).hasSize(3);
        assertThat(cpuData.get(0).getValue()).isEqualTo(50.5);
        assertThat(cpuData.get(1).getValue()).isEqualTo(60.2);
        assertThat(cpuData.get(2).getValue()).isEqualTo(45.8);

        // HTTP 요청 검증
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/api/v1/query_range");
        assertThat(request.getPath()).contains("query=cpu_usage");
        assertThat(request.getPath()).contains("mission_attempt_id");
    }

    @Test
    @DisplayName("미션별 메트릭 조회 성공")
    void queryMissionMetrics_Success() throws Exception {
        // Given
        String mockResponse = """
            {
                "status": "success",
                "data": {
                    "resultType": "matrix",
                    "result": [
                        {
                            "metric": {
                                "__name__": "memory_usage",
                                "mission_attempt_id": "mission-789"
                            },
                            "values": [
                                [1609459200, "1024.0"],
                                [1609459260, "1536.5"]
                            ]
                        }
                    ]
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mockResponse));

        LocalDateTime startTime = LocalDateTime.of(2021, 1, 1, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2021, 1, 1, 1, 0, 0);

        // When
        List<MetricDataPoint> result = prometheusClient.queryMissionMetrics(
            "memory_usage",
            "mission-789",
            startTime,
            endTime,
            60
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getValue()).isEqualTo(1024.0);
        assertThat(result.get(1).getValue()).isEqualTo(1536.5);

        // HTTP 요청 검증 - mission_attempt_id 필터링 확인
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("memory_usage");
        assertThat(request.getPath()).contains("mission_attempt_id");
        assertThat(request.getPath()).contains("mission-789");
    }

    @Test
    @DisplayName("Prometheus 서버 오류 시 예외 발생")
    void queryRange_ServerError() {
        // Given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        LocalDateTime startTime = LocalDateTime.of(2021, 1, 1, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2021, 1, 1, 1, 0, 0);

        // When & Then
        assertThatThrownBy(() -> prometheusClient.queryRange(
            "cpu_usage",
            startTime,
            endTime,
            60
        )).isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Prometheus query failed");
    }

    @Test
    @DisplayName("잘못된 응답 형식 시 예외 발생")
    void queryRange_InvalidResponse() {
        // Given
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("invalid json"));

        LocalDateTime startTime = LocalDateTime.of(2021, 1, 1, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2021, 1, 1, 1, 0, 0);

        // When & Then
        assertThatThrownBy(() -> prometheusClient.queryRange(
            "cpu_usage",
            startTime,
            endTime,
            60
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("빈 결과 반환 시 빈 맵 반환")
    void queryRange_EmptyResult() {
        // Given
        String mockResponse = """
            {
                "status": "success",
                "data": {
                    "resultType": "matrix",
                    "result": []
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(mockResponse));

        LocalDateTime startTime = LocalDateTime.of(2021, 1, 1, 0, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2021, 1, 1, 1, 0, 0);

        // When
        Map<String, List<MetricDataPoint>> result = prometheusClient.queryRange(
            "nonexistent_metric",
            startTime,
            endTime,
            60
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}