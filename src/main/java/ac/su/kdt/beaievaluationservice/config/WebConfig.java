package ac.su.kdt.beaievaluationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 설정 클래스
 * CORS(Cross-Origin Resource Sharing) 설정을 통해 브라우저에서 API 호출을 허용
 * Swagger UI 및 다양한 프론트엔드 애플리케이션에서의 접근을 허용
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // API 엔드포인트에 대한 CORS 설정
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*") // 모든 origin 허용 (개발용)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(false) // allowedOriginPatterns("*")와 함께 사용 시 false 권장
                .maxAge(86400); // 24시간

        // Swagger UI에 대한 CORS 설정
        registry.addMapping("/swagger-ui/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
                
        // OpenAPI 문서에 대한 CORS 설정
        registry.addMapping("/v3/api-docs/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
                
        // Health check 및 actuator 엔드포인트
        registry.addMapping("/actuator/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
