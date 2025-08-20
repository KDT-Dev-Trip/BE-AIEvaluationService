package ac.su.kdt.beaievaluationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 목업 S3 데이터 서비스
 * 개발 및 테스트 환경에서 실제 S3 연동 전 목업 데이터를 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "false", matchIfMissing = false)
public class MockS3DataService {

    private final ObjectMapper objectMapper;
    private final Map<String, Object> mockDataCache = new HashMap<>();

    /**
     * Pre-signed URL을 통해 S3 데이터를 읽어오는 것을 시뮬레이션
     * 실제로는 로컬 JSON 파일에서 목업 데이터를 반환
     */
    public String readS3DataByPreSignedUrl(String preSignedUrl) {
        log.info("Reading S3 data from Pre-signed URL: {}", preSignedUrl);
        
        try {
            // Pre-signed URL에서 missionAttemptId 추출 (목업용)
            String missionAttemptId = extractMissionAttemptIdFromUrl(preSignedUrl);
            
            // 목업 데이터 파일 매핑
            String mockDataFile = getMockDataFileForMission(missionAttemptId);
            
            // 캐시에서 먼저 확인
            String cacheKey = mockDataFile;
            if (mockDataCache.containsKey(cacheKey)) {
                log.debug("Returning cached mock data for: {}", mockDataFile);
                return objectMapper.writeValueAsString(mockDataCache.get(cacheKey));
            }
            
            // JSON 파일에서 목업 데이터 로드
            ClassPathResource resource = new ClassPathResource("mock-data/" + mockDataFile);
            
            if (!resource.exists()) {
                log.warn("Mock data file not found: {}", mockDataFile);
                return generateFallbackMockData(missionAttemptId);
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                Object mockData = objectMapper.readValue(inputStream, Object.class);
                mockDataCache.put(cacheKey, mockData); // 캐시에 저장
                
                log.info("Successfully loaded mock data from: {}", mockDataFile);
                return objectMapper.writeValueAsString(mockData);
            }
            
        } catch (Exception e) {
            log.error("Failed to read mock S3 data from URL: {}", preSignedUrl, e);
            return generateFallbackMockData("unknown-mission");
        }
    }

    /**
     * Pre-signed URL에서 missionAttemptId 추출 (목업용 간단한 파싱)
     */
    private String extractMissionAttemptIdFromUrl(String preSignedUrl) {
        if (preSignedUrl == null || preSignedUrl.isEmpty()) {
            return "backend-service-123";
        }
        
        // URL에서 mission ID 패턴 찾기
        if (preSignedUrl.contains("kubernetes-mission") || preSignedUrl.contains("k8s")) {
            return "kubernetes-mission-123";
        } else if (preSignedUrl.contains("docker-compose") || preSignedUrl.contains("docker")) {
            return "docker-compose-mission-456";
        } else if (preSignedUrl.contains("terraform")) {
            return "terraform-mission-789";
        } else if (preSignedUrl.contains("backend-service")) {
            return "backend-service-123";
        } else {
            return "backend-service-123"; // 기본값을 백엔드 서비스로 변경
        }
    }

    /**
     * 미션 타입에 따른 목업 데이터 파일 매핑
     */
    private String getMockDataFileForMission(String missionAttemptId) {
        if (missionAttemptId.contains("kubernetes") || missionAttemptId.contains("k8s")) {
            return "kubernetes-deployment-commands.json";
        } else if (missionAttemptId.contains("docker-compose")) {
            return "docker-compose-commands.json";
        } else if (missionAttemptId.contains("terraform")) {
            return "terraform-infrastructure-commands.json";
        } else if (missionAttemptId.contains("backend-service")) {
            return "backend-service-commands.json";
        } else {
            return "backend-service-commands.json"; // 기본값을 백엔드 서비스로 변경
        }
    }

    /**
     * 목업 데이터 파일을 찾을 수 없을 때 사용할 기본 데이터 생성
     */
    private String generateFallbackMockData(String missionAttemptId) {
        try {
            Map<String, Object> fallbackData = new HashMap<>();
            fallbackData.put("mission_attempt_id", missionAttemptId);
            fallbackData.put("note", "Fallback mock data - actual S3 data file not found");
            
            Map<String, Object> executionLog = new HashMap<>();
            executionLog.put("commands", java.util.List.of(
                Map.of(
                    "timestamp", "2025-01-15T12:00:00Z",
                    "command", "echo 'Mock command execution'",
                    "output", "Mock command execution",
                    "exit_code", 0,
                    "duration_ms", 100
                )
            ));
            executionLog.put("summary", Map.of(
                "total_commands", 1,
                "successful_commands", 1,
                "failed_commands", 0,
                "total_duration_ms", 100
            ));
            fallbackData.put("execution_log", executionLog);
            
            Map<String, Object> resourceMetrics = new HashMap<>();
            resourceMetrics.put("metrics", java.util.List.of(
                Map.of(
                    "timestamp", "2025-01-15T12:00:00Z",
                    "cpu_usage_percent", 10.5,
                    "memory_usage_mb", 256,
                    "network_rx_bytes", 1024,
                    "network_tx_bytes", 2048
                )
            ));
            resourceMetrics.put("summary", Map.of(
                "avg_cpu_usage_percent", 10.5,
                "max_cpu_usage_percent", 10.5,
                "avg_memory_usage_mb", 256,
                "max_memory_usage_mb", 256
            ));
            fallbackData.put("resource_metrics", resourceMetrics);
            
            return objectMapper.writeValueAsString(fallbackData);
            
        } catch (Exception e) {
            log.error("Failed to generate fallback mock data", e);
            return "{}";
        }
    }

    /**
     * 캐시 클리어 (테스트용)
     */
    public void clearCache() {
        mockDataCache.clear();
        log.info("Mock data cache cleared");
    }

    /**
     * 사용 가능한 목업 데이터 파일 목록 반환
     */
    public java.util.List<String> getAvailableMockDataFiles() {
        return java.util.List.of(
            "backend-service-commands.json",
            "kubernetes-deployment-commands.json", 
            "docker-compose-commands.json",
            "terraform-infrastructure-commands.json"
        );
    }
}