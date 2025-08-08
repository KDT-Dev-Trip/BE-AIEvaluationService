package ac.su.kdt.beaievaluationservice.config;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Prometheus 클라이언트 설정
@Configuration
public class PrometheusConfig {
    
    @Value("${prometheus.client.timeout:30}") // 기본 타임아웃 설정 (초 단위)
    private int timeoutSeconds;
    
    @Value("${prometheus.client.logging.enabled:false}") // 로깅 활성화 여부 (기본값: false)
    private boolean loggingEnabled;
    
    @Bean
    public OkHttpClient prometheusHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(timeoutSeconds))
            .readTimeout(Duration.ofSeconds(timeoutSeconds))
            .writeTimeout(Duration.ofSeconds(timeoutSeconds));
            
        // 로깅 인터셉터 추가 (개발 환경에서만 사용)
        if (loggingEnabled) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);
        }
        
        return builder.build();
    }
}