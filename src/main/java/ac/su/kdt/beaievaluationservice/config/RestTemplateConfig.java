package ac.su.kdt.beaievaluationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 설정 클래스
 * Spring의 RestTemplate을 사용하여 외부 API 호출을 위한 HTTP 클라이언트를 구성
 * SimpleClientHttpRequestFactory를 사용하여 커넥션 타임아웃과 요청 타임아웃을 설정
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        
        return new RestTemplate(factory);
    }
}