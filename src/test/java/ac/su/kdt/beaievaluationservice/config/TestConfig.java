package ac.su.kdt.beaievaluationservice.config;

import ac.su.kdt.beaievaluationservice.service.MockS3DataService;
import ac.su.kdt.beaievaluationservice.client.MissionDataClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

@TestConfiguration
public class TestConfig {
    
    @MockBean
    private MockS3DataService mockS3DataService;
    
    @MockBean
    private MissionDataClient missionDataClient;
    
    @MockBean
    private ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher kafkaEventPublisher;
    
    @MockBean
    private ac.su.kdt.beaievaluationservice.service.EvaluationEventPublisher serviceEventPublisher;
    
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}