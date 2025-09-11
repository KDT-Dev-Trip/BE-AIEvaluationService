package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.kafka.publisher.EvaluationEventPublisher;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3 데이터 통합 평가 서비스 테스트")
class S3DataIntegrationEvaluationServiceTest {

    @Mock
    private AIEvaluationRepository aiEvaluationRepository;

    @Mock
    private EvaluationSummaryRepository evaluationSummaryRepository;

    @Mock
    private EvaluationHistoryRepository evaluationHistoryRepository;

    @Mock
    private GeminiEvaluationService geminiEvaluationService;
    
    @Mock
    private EvaluationEventPublisher evaluationEventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EvaluationService evaluationService;

    private MissionCompletedEvent s3IntegratedEvent;
    private AIEvaluation testEvaluation;
    private EvaluationResultDTO s3BasedResult;

    @BeforeEach
    void setUp() {
        s3IntegratedEvent = createS3IntegratedMissionCompletedEvent();
        testEvaluation = createTestAIEvaluation();
        s3BasedResult = createS3BasedEvaluationResult();
    }

    @Test
    @DisplayName("S3 데이터 통합 평가 - 성공적인 처리")
    void processS3IntegratedEvaluation_Success() throws Exception {
        // Given
        lenient().when(aiEvaluationRepository.existsByMissionAttemptId("s3-mission-123")).thenReturn(false);
        lenient().when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        lenient().when(geminiEvaluationService.evaluateCodeWithRealData(any(MissionCompletedEvent.class))).thenReturn(s3BasedResult);
        lenient().when(objectMapper.writeValueAsString(s3BasedResult)).thenReturn("{\"overallScore\":92}");

        // When
        evaluationService.processEvaluation(s3IntegratedEvent);

        // Then
        verify(geminiEvaluationService, atMost(1)).evaluateCodeWithRealData(eq(s3IntegratedEvent));
        verify(evaluationEventPublisher, atMost(1)).publishEvaluationCompleted(any());
    }

    private MissionCompletedEvent createS3IntegratedMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("100001");
        event.setMissionId("500001");
        event.setMissionAttemptId("s3-mission-123");
        event.setMissionType("Kubernetes Deployment");
        event.setCode("apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: nginx-deployment");
        event.setMissionTitle("Kubernetes 애플리케이션 배포 실습");
        event.setCompletedAt(LocalDateTime.now());
        
        event.setEvaluationCriteria("{\"objectives\":[\"Deployment 생성\",\"Service 생성\",\"외부 접근 테스트\"]}");
        event.setMissionGuide("# Kubernetes 애플리케이션 배포\n\nKubernetes 클러스터에 nginx 애플리케이션을 배포하고, 외부에서 접근 가능하도록 서비스를 설정하세요.");
        
        event.setS3StorageUrl("s3://devtrip-logs/missions/s3-mission-123/execution-data.zip");
        event.setS3PreSignedUrl("https://devtrip-logs.s3.amazonaws.com/missions/s3-mission-123/execution-data.zip?X-Amz-Expires=300&token=abc123");
        
        MissionCompletedEvent.SimpleStatistics stats = new MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(15);
        stats.setCommandFailureCount(2);
        stats.setTopErrorMessages(List.of(
            "Error: services \"nginx-service\" already exists",
            "Warning: kubectl apply should be used on resource created by either kubectl create --save-config"
        ));
        stats.setAverageCpuUsage(23.4);
        stats.setMaxCpuUsage(67.8);
        stats.setAverageMemoryUsage(445.2);
        stats.setMaxMemoryUsage(892.1);
        stats.setTotalExecutionTime(28500L);
        event.setStatistics(stats);
        
        return event;
    }

    private AIEvaluation createTestAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("s3-mission-123");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());
        return evaluation;
    }

    private EvaluationResultDTO createS3BasedEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(92);
        result.setFeedback("S3 저장된 실행 로그와 메트릭을 분석한 결과, Kubernetes 배포가 성공적으로 완료되었습니다.");
        result.setDetailedAnalysis("실제 실행 환경에서의 성능도 우수하며, 일부 발생한 경고는 학습 과정에서 자연스러운 현상입니다.");

        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(90);
        codeQuality.setFeedback("YAML 구조가 정확하고 Kubernetes 모범 사례를 잘 따릅니다.");
        result.setCodeQuality(codeQuality);

        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(88);
        security.setFeedback("기본적인 보안 설정이 적절히 적용되었습니다.");
        result.setSecurity(security);

        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(94);
        style.setFeedback("YAML 스타일이 일관성 있고 읽기 쉽습니다.");
        result.setStyle(style);

        return result;
    }
}