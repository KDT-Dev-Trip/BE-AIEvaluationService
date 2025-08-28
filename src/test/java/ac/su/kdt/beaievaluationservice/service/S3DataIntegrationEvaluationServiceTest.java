package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * S3 데이터 직접 읽기 기능 테스트
 * 요구사항: S3Uploader가 {커맨드, 커맨드 아웃풋, 리소스 메트릭}을 AWS S3에 업로드하고,
 * AISvc가 Pre-Signed URL을 통해 데이터를 직접 읽어 평가하는 플로우 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 데이터 직접 읽기 통합 평가 서비스 테스트")
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
    @DisplayName("S3 데이터 직접 읽기 통합 평가 - 커맨드 로그, 메트릭 포함 성공적 평가")
    void processEvaluation_WithS3DirectDataReading_Success() throws Exception {
        // Given - S3에서 직접 데이터를 읽어 평가하는 시나리오
        when(aiEvaluationRepository.existsByMissionAttemptId("s3-mission-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        // Gemini 서비스가 S3 URL과 Pre-signed URL을 받아 데이터를 직접 읽도록 설정
        when(geminiEvaluationService.evaluateCode(
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(List.class),
            any(String.class),
            any(String.class),
            any(MissionCompletedEvent.SimpleStatistics.class)
        )).thenReturn(s3BasedResult);
        
        when(objectMapper.writeValueAsString(s3BasedResult)).thenReturn("{\"overallScore\":92}");

        // When - S3 통합 평가 프로세스 실행
        evaluationService.processEvaluation(s3IntegratedEvent);

        // Then - S3 데이터를 직접 읽어 평가했는지 검증
        verify(geminiEvaluationService).evaluateCode(
            any(String.class),
            any(String.class),
            any(String.class),
            any(String.class),
            any(List.class),
            any(String.class),
            any(String.class),
            any(MissionCompletedEvent.SimpleStatistics.class)
        );
        
        // 평가 프로세스 완료 검증
        verify(aiEvaluationRepository, atLeast(2)).save(any(AIEvaluation.class));
        verify(evaluationSummaryRepository, atMost(1)).save(any(EvaluationSummary.class));
        verify(evaluationEventPublisher, atMost(1)).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("S3 데이터 기반 evaluation.completed 이벤트 발행 - 실행 로그 분석 결과 포함")
    void processEvaluation_PublishS3BasedEvaluationEvent() throws Exception {
        // Given - S3 데이터 분석 결과를 포함한 이벤트 발행
        when(aiEvaluationRepository.existsByMissionAttemptId("s3-mission-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        when(geminiEvaluationService.evaluateCode(
            anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()
        )).thenReturn(s3BasedResult);
        
        when(objectMapper.writeValueAsString(s3BasedResult)).thenReturn("{\"overallScore\":92}");

        // When
        evaluationService.processEvaluation(s3IntegratedEvent);

        // Then - S3 데이터 분석 결과가 포함된 이벤트 발행 검증
        verify(evaluationEventPublisher).publishEvaluationCompleted(argThat(event -> {
            // 기본 정보 검증
            assertEquals("s3-mission-123", event.getMissionAttemptId());
            assertEquals("COMPLETED", event.getEvaluationStatus());
            
            // S3 데이터 기반 평가 결과 검증
            assertEquals(s3BasedResult.getOverallScore(), event.getOverallScore());
            
            // 실행 통계 정보 검증 (S3에서 읽은 데이터 기반)
            MissionCompletedEvent.SimpleStatistics stats = s3IntegratedEvent.getStatistics();
            assertEquals(stats.getCommandSuccessCount(), event.getCommandSuccessCount());
            assertEquals(stats.getCommandFailureCount(), event.getCommandFailureCount());
            
            return true;
        }));
    }

    @Test
    @DisplayName("Pre-signed URL이 없는 경우 - S3 데이터 직접 읽기 불가 알림 포함 평가")
    void processEvaluation_WithoutPreSignedUrl_EvaluationWithWarning() throws Exception {
        // Given - Pre-signed URL이 없는 경우
        MissionCompletedEvent eventWithoutPreSignedUrl = createEventWithoutPreSignedUrl();
        
        when(aiEvaluationRepository.existsByMissionAttemptId("no-presigned-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        // Pre-signed URL 없이도 기본 평가는 수행
        when(geminiEvaluationService.evaluateCode(
            anyString(), anyString(), anyString(), anyString(), any(), anyString(), isNull(), any()
        )).thenReturn(s3BasedResult);
        
        when(objectMapper.writeValueAsString(s3BasedResult)).thenReturn("{\"overallScore\":75}");

        // When
        evaluationService.processEvaluation(eventWithoutPreSignedUrl);

        // Then - Pre-signed URL이 null로 전달되어 S3 데이터 읽기 불가 상황 처리
        verify(geminiEvaluationService).evaluateCode(
            eq(eventWithoutPreSignedUrl.getCode()),
            eq(eventWithoutPreSignedUrl.getMissionType()),
            eq(eventWithoutPreSignedUrl.getMissionId()),
            eq(eventWithoutPreSignedUrl.getMissionObjective()),
            eq(eventWithoutPreSignedUrl.getChecklist()),
            eq(eventWithoutPreSignedUrl.getS3StorageUrl()),
            isNull(), // Pre-signed URL이 없음
            eq(eventWithoutPreSignedUrl.getStatistics())
        );
        
        verify(evaluationEventPublisher).publishEvaluationCompleted(any());
    }

    // Helper methods for creating test data with S3 integration

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
        
        // 미션 요구사항
        event.setMissionObjective("Kubernetes 클러스터에 nginx 애플리케이션을 배포하고, 외부에서 접근 가능하도록 서비스를 설정하세요.");
        event.setChecklist(List.of(
            "Deployment 리소스 생성",
            "Service 리소스 생성 (LoadBalancer 타입)",
            "Ingress 설정 (옵션)",
            "Pod 상태 확인",
            "외부 접근 테스트 완료"
        ));
        
        // S3 통합: 실제 S3Uploader가 업로드한 데이터 위치
        event.setS3StorageUrl("s3://devtrip-logs/missions/s3-mission-123/execution-data.zip");
        event.setS3PreSignedUrl("https://devtrip-logs.s3.amazonaws.com/missions/s3-mission-123/execution-data.zip?X-Amz-Expires=300&token=abc123");
        
        // S3에 저장된 실제 실행 데이터 기반 통계
        MissionCompletedEvent.SimpleStatistics stats = new MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(15); // kubectl 명령 성공
        stats.setCommandFailureCount(2);  // 일부 명령 실패
        stats.setTopErrorMessages(List.of(
            "Error: services \"nginx-service\" already exists",
            "Warning: kubectl apply should be used on resource created by either kubectl create --save-config",
            "Error: the server doesn't have a resource type \"ingresss\""
        ));
        stats.setAverageCpuUsage(23.4); // 클러스터 평균 CPU
        stats.setMaxCpuUsage(67.8);     // 최대 CPU 스파이크
        stats.setAverageMemoryUsage(445.2); // 평균 메모리
        stats.setMaxMemoryUsage(892.1);     // 최대 메모리
        stats.setTotalExecutionTime(28500L); // 28.5초 실행
        event.setStatistics(stats);
        
        return event;
    }
    
    private MissionCompletedEvent createEventWithoutPreSignedUrl() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("100002");
        event.setMissionId("500002");
        event.setMissionAttemptId("no-presigned-123");
        event.setMissionType("Docker Compose");
        event.setCode("version: '3.8'\nservices:\n  web:\n    image: nginx");
        event.setMissionTitle("Docker Compose 서비스 구성");
        event.setCompletedAt(LocalDateTime.now());
        
        event.setMissionObjective("Docker Compose를 사용하여 nginx 웹 서버를 구성하세요.");
        event.setChecklist(List.of("docker-compose.yml 작성", "서비스 실행", "포트 확인"));
        
        // S3 저장소 주소는 있지만 Pre-signed URL은 없음
        event.setS3StorageUrl("s3://devtrip-logs/missions/no-presigned-123/");
        event.setS3PreSignedUrl(null); // Pre-signed URL 없음
        
        MissionCompletedEvent.SimpleStatistics stats = new MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(5);
        stats.setCommandFailureCount(1);
        stats.setAverageCpuUsage(15.2);
        stats.setMaxCpuUsage(32.1);
        stats.setTotalExecutionTime(8500L);
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
        result.setFeedback("S3 저장된 실행 로그와 메트릭을 분석한 결과, Kubernetes 배포가 성공적으로 완료되었습니다. 실제 실행 환경에서의 성능도 우수하며, 일부 발생한 경고는 학습 과정에서 자연스러운 현상입니다.");
        result.setDetailedAnalysis("S3 데이터 분석 결과: kubectl 명령 성공률 88% (15/17), 평균 응답 시간 1.2초, 메모리 사용량 정상 범위. 'services already exists' 경고는 재실행으로 인한 것으로 문제없음. Ingress 오타는 수정 필요.");

        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(94);
        codeQuality.setFeedback("Kubernetes 매니페스트가 올바르게 작성되었고, 실제 배포도 성공적으로 완료되었습니다.");
        codeQuality.setSuggestions("리소스 제한(resources.limits)을 추가하면 더욱 안정적인 배포가 가능합니다.");
        result.setCodeQuality(codeQuality);

        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(89);
        security.setFeedback("기본 보안 설정이 적절히 적용되었습니다.");
        security.setVulnerabilities("기본 nginx 이미지 사용 중 - 보안 취약점 검토 필요");
        security.setRecommendations("공식 nginx 이미지의 최신 버전 사용 및 비root 사용자 설정을 권장합니다.");
        result.setSecurity(security);

        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(93);
        style.setFeedback("YAML 문법과 Kubernetes 리소스 구조가 표준을 잘 따르고 있습니다.");
        style.setStyleIssues("일부 라벨과 셀렉터가 누락되었습니다.");
        style.setImprovements("app.kubernetes.io/name 등의 권장 라벨을 추가하세요.");
        result.setStyle(style);

        return result;
    }
}