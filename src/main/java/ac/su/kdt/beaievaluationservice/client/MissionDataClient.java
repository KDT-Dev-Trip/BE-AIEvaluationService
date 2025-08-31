package ac.su.kdt.beaievaluationservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 미션 데이터 클라이언트 (Fallback 용도)
 * 
 * 주요 기능: Kafka 이벤트에 실제 데이터가 비어있을 때 fallback으로 사용
 * 운영/디버깅 또는 비상 상황에서 직접 데이터 조회용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionDataClient {

    private final RestTemplate restTemplate;
    
    @Value("${mission.management.service.url:http://localhost:8081}")
    private String missionServiceUrl;

    /**
     * 미션 관리 서비스에서 AI 평가용 데이터 조회
     */
    public EvaluationDataResponse getEvaluationData(String attemptId) {
        String url = missionServiceUrl + "/api/mission-evaluation/" + attemptId + "/evaluation-data";
        
        log.info("미션 데이터 조회 요청: attemptId={}, url={}", attemptId, url);
        
        try {
            ResponseEntity<EvaluationDataResponse> response = restTemplate.getForEntity(url, EvaluationDataResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                EvaluationDataResponse data = response.getBody();
                log.info("미션 데이터 조회 성공: attemptId={}, 명령어수={}", 
                        attemptId, data.getCommandHistory() != null ? data.getCommandHistory().size() : 0);
                return data;
            } else {
                log.warn("미션 데이터 조회 실패 - 빈 응답: attemptId={}, status={}", attemptId, response.getStatusCode());
                return null;
            }
            
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("미션 데이터를 찾을 수 없음: attemptId={}", attemptId);
            return null;
        } catch (ResourceAccessException e) {
            log.error("미션 관리 서비스 연결 실패: attemptId={}, error={}", attemptId, e.getMessage());
            throw new RuntimeException("미션 관리 서비스 연결 실패", e);
        } catch (Exception e) {
            log.error("미션 데이터 조회 중 예외 발생: attemptId={}", attemptId, e);
            throw new RuntimeException("미션 데이터 조회 실패", e);
        }
    }

    /**
     * 미션 관리 서비스 연결 상태 확인
     */
    public boolean isServiceAvailable() {
        try {
            String healthUrl = missionServiceUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            boolean available = response.getStatusCode().is2xxSuccessful();
            log.debug("미션 관리 서비스 상태: {}", available ? "정상" : "비정상");
            return available;
        } catch (Exception e) {
            log.debug("미션 관리 서비스 상태 확인 실패: {}", e.getMessage());
            return false;
        }
    }
}