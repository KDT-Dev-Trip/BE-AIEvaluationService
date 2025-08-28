package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.HealthCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DevTrip 표준 Health Check 서비스
 * 모든 서비스에서 일관된 헬스체크 로직 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService implements HealthIndicator {
    
    private final DataSource dataSource;
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${info.app.version:1.0.0}")
    private String version;
    
    /**
     * Spring Boot Actuator HealthIndicator 구현
     */
    @Override
    public Health health() {
        try {
            HealthCheckResponse response = performHealthCheck();
            
            if ("UP".equals(response.getStatus())) {
                return Health.up()
                    .withDetail("service", response.getService())
                    .withDetail("version", response.getVersion())
                    .withDetail("components", response.getComponents())
                    .build();
            } else {
                return Health.down()
                    .withDetail("service", response.getService())
                    .withDetail("version", response.getVersion())
                    .withDetail("components", response.getComponents())
                    .build();
            }
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    /**
     * 종합적인 헬스체크 수행
     */
    public HealthCheckResponse performHealthCheck() {
        Map<String, HealthCheckResponse.ComponentHealth> components = new HashMap<>();
        Map<String, Object> details = new HashMap<>();
        
        // 데이터베이스 상태 체크
        HealthCheckResponse.ComponentHealth dbHealth = checkDatabase();
        components.put("database", dbHealth);
        
        // 메모리 상태 체크
        HealthCheckResponse.ComponentHealth memoryHealth = checkMemory();
        components.put("memory", memoryHealth);
        
        // 디스크 상태 체크 (추가 정보)
        details.put("diskSpace", checkDiskSpace());
        
        // 전체 상태 결정
        String overallStatus = determineOverallStatus(components);
        
        return HealthCheckResponse.builder()
            .status(overallStatus)
            .service(serviceName)
            .version(version)
            .timestamp(LocalDateTime.now())
            .components(components)
            .details(details)
            .build();
    }
    
    /**
     * 데이터베이스 연결 상태 체크
     */
    private HealthCheckResponse.ComponentHealth checkDatabase() {
        try {
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(3); // 3초 타임아웃
                
                Map<String, Object> details = new HashMap<>();
                details.put("database", connection.getMetaData().getDatabaseProductName());
                details.put("validationQuery", "connection.isValid()");
                details.put("timeout", "3 seconds");
                
                return HealthCheckResponse.ComponentHealth.builder()
                    .status(isValid ? "UP" : "DOWN")
                    .details(details)
                    .build();
            }
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return HealthCheckResponse.ComponentHealth.builder()
                .status("DOWN")
                .details(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    /**
     * 메모리 상태 체크
     */
    private HealthCheckResponse.ComponentHealth checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double usagePercentage = (double) usedMemory / maxMemory * 100;
        
        Map<String, Object> details = new HashMap<>();
        details.put("max", formatBytes(maxMemory));
        details.put("total", formatBytes(totalMemory));
        details.put("used", formatBytes(usedMemory));
        details.put("free", formatBytes(freeMemory));
        details.put("usagePercentage", String.format("%.2f%%", usagePercentage));
        
        String status = usagePercentage < 80 ? "UP" : (usagePercentage < 90 ? "WARNING" : "DOWN");
        
        return HealthCheckResponse.ComponentHealth.builder()
            .status(status)
            .details(details)
            .build();
    }
    
    /**
     * 디스크 공간 체크
     */
    private Map<String, Object> checkDiskSpace() {
        Map<String, Object> diskInfo = new HashMap<>();
        try {
            java.io.File root = new java.io.File("/");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            double usagePercentage = (double) usedSpace / totalSpace * 100;
            
            diskInfo.put("total", formatBytes(totalSpace));
            diskInfo.put("free", formatBytes(freeSpace));
            diskInfo.put("used", formatBytes(usedSpace));
            diskInfo.put("usagePercentage", String.format("%.2f%%", usagePercentage));
        } catch (Exception e) {
            diskInfo.put("error", e.getMessage());
        }
        return diskInfo;
    }
    
    /**
     * 전체 상태 결정 로직
     */
    private String determineOverallStatus(Map<String, HealthCheckResponse.ComponentHealth> components) {
        boolean hasDown = components.values().stream()
            .anyMatch(component -> "DOWN".equals(component.getStatus()));
        
        if (hasDown) {
            return "DOWN";
        }
        
        boolean hasWarning = components.values().stream()
            .anyMatch(component -> "WARNING".equals(component.getStatus()));
        
        return hasWarning ? "WARNING" : "UP";
    }
    
    /**
     * 바이트를 사람이 읽기 쉬운 형식으로 변환
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}