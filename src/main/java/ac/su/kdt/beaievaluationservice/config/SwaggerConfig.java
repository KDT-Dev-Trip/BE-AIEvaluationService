package ac.su.kdt.beaievaluationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 코드 평가 시스템 API")
                        .description("""
                                ## AI 코드 평가 시스템 REST API
                                
                                사용자가 작성한 코드를 AI로 평가하고 피드백을 제공하는 시스템입니다.
                                
                                ### 주요 기능
                                - 📝 **임시 저장**: 미션 진행 중 코드 임시 저장
                                - 🤖 **AI 평가**: Gemini API를 통한 자동 코드 평가  
                                - 📊 **성능 분석**: Prometheus 메트릭을 활용한 성능 분석
                                - 🔄 **이벤트 기반**: Kafka를 통한 비동기 처리
                                
                                ### 사용법
                                1. **임시 저장**: `/temp-save` API로 코드 저장
                                2. **AI 평가**: `/evaluation/start` API로 평가 시작  
                                3. **결과 확인**: `/evaluation/{id}` API로 결과 조회
                                
                                ### 테스트 데이터
                                - 시스템 테스트를 위해 `/test/data/sample` API로 샘플 데이터 생성 가능
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AI 평가 시스템 개발팀")
                                .email("dev-team@example.com")
                                .url("https://github.com/example/ai-evaluation-system"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://api.example.com")
                                .description("운영 서버")
                ));
    }
}