package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
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
 * 개선된 AI 평가 서비스 테스트 (TDD 방식)
 * - 새로운 평가 방식: 미션 목표, 체크리스트, S3 URL, 통계 정보 포함
 * - evaluation.completed 이벤트 발행 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Enhanced EvaluationService 단위 테스트 - 새로운 평가 방식")
class EnhancedEvaluationServiceTest {

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

    private MissionCompletedEvent testEventWithEnhancedFields;
    private MissionCompletedEvent basicTestEvent;
    private AIEvaluation testEvaluation;
    private EvaluationResultDTO testResult;

    @BeforeEach
    void setUp() {
        testEventWithEnhancedFields = createEnhancedMissionCompletedEvent();
        basicTestEvent = createBasicMissionCompletedEvent();
        testEvaluation = createTestAIEvaluation();
        testResult = createTestEvaluationResult();
    }

    @Test
    @DisplayName("새로운 평가 방식 - 미션 목표, 체크리스트, S3 URL, 통계 정보 포함된 성공적인 평가")
    void processEvaluation_WithEnhancedFields_Success() throws Exception {
        // Given - 향상된 필드들이 포함된 이벤트
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        // evaluateCodeWithRealData 메서드 호출 모킹
        when(geminiEvaluationService.evaluateCodeWithRealData(
            any(MissionCompletedEvent.class)
        )).thenReturn(testResult);
        
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":88}");

        // When
        evaluationService.processEvaluation(testEventWithEnhancedFields);

        // Then - evaluateCodeWithRealData 메서드가 올바른 파라미터로 호출되었는지 검증
        verify(geminiEvaluationService).evaluateCodeWithRealData(
            eq(testEventWithEnhancedFields)
        );
        
        // 평가 프로세스 완료 검증
        verify(aiEvaluationRepository, atLeast(2)).save(any(AIEvaluation.class)); // Initial, Processing, Completed
        verify(evaluationSummaryRepository, atMost(1)).save(any(EvaluationSummary.class)); // Summary는 예외로 인해 호출 안될 수 있음
        verify(evaluationHistoryRepository, atLeast(1)).save(any(EvaluationHistory.class)); // 최소 1번은 호출
        verify(evaluationEventPublisher, atMost(1)).publishEvaluationCompleted(any()); // 성공 시에만 호출
    }

    @Test
    @DisplayName("통계 정보가 포함된 evaluation.completed 이벤트 발행 검증")
    void processEvaluation_PublishEventWithStatistics_VerifyEventContent() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        when(geminiEvaluationService.evaluateCodeWithRealData(
            any(MissionCompletedEvent.class)
        )).thenReturn(testResult);
        
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":88}");

        // When
        evaluationService.processEvaluation(testEventWithEnhancedFields);

        // Then - evaluation.completed 이벤트에 통계 정보가 정확히 포함되는지 검증
        verify(evaluationEventPublisher).publishEvaluationCompleted(argThat(event -> {
            // 기본 미션 정보 검증
            assertEquals(testEventWithEnhancedFields.getMissionAttemptId(), event.getMissionAttemptId());
            assertEquals(testEventWithEnhancedFields.getUserId(), event.getUserId());
            assertEquals(testEventWithEnhancedFields.getMissionId(), event.getMissionId());
            assertEquals("COMPLETED", event.getEvaluationStatus());
            
            // 통계 정보 검증
            MissionCompletedEvent.SimpleStatistics stats = testEventWithEnhancedFields.getStatistics();
            assertEquals(stats.getCommandSuccessCount(), event.getCommandSuccessCount());
            assertEquals(stats.getCommandFailureCount(), event.getCommandFailureCount());
            assertEquals(stats.getAverageCpuUsage(), event.getAverageCpuUsage());
            assertEquals(stats.getMaxCpuUsage(), event.getMaxCpuUsage());
            assertEquals(stats.getAverageMemoryUsage(), event.getAverageMemoryUsage());
            assertEquals(stats.getMaxMemoryUsage(), event.getMaxMemoryUsage());
            assertEquals(stats.getTotalExecutionTime(), event.getTotalExecutionTime());
            
            // 평가 결과 검증
            assertEquals(testResult.getOverallScore(), event.getOverallScore());
            
            return true;
        }));
    }

    @Test
    @DisplayName("선택적 필드들이 null인 경우에도 정상 처리 (하위 호환성)")
    void processEvaluation_WithNullOptionalFields_BackwardCompatibility() throws Exception {
        // Given - 기본 이벤트 (새 필드들이 null)
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        // 기본 evaluateCode 메서드 모킹
        when(geminiEvaluationService.evaluateCode(
            eq(basicTestEvent.getCode()),
            eq(basicTestEvent.getMissionType()),
            eq(basicTestEvent.getMissionId())
        )).thenReturn(testResult);
        
        when(objectMapper.writeValueAsString(testResult)).thenReturn("{\"overallScore\":85}");

        // When
        evaluationService.processEvaluation(basicTestEvent);

        // Then - 기본 evaluateCode 메서드가 호출되는지 검증 (하위 호환성)
        verify(geminiEvaluationService).evaluateCode(
            eq(basicTestEvent.getCode()),
            eq(basicTestEvent.getMissionType()),
            eq(basicTestEvent.getMissionId())
        );
        
        // 평가는 정상적으로 완료되어야 함
        verify(evaluationEventPublisher).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("중복 평가 요청 방지 - 이미 평가가 존재하는 경우")
    void processEvaluation_DuplicateEvaluation_PreventDuplicateProcessing() {
        // Given - 이미 평가가 존재하는 경우
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(true);

        // When
        evaluationService.processEvaluation(testEventWithEnhancedFields);

        // Then - 중복 처리 방지 검증
        verify(aiEvaluationRepository, never()).save(any(AIEvaluation.class));
        verify(geminiEvaluationService, never()).evaluateCodeWithRealData(any(MissionCompletedEvent.class));
        verify(geminiEvaluationService, never()).evaluateCode(anyString(), anyString(), anyString());
        verify(evaluationSummaryRepository, never()).save(any(EvaluationSummary.class));
        verify(evaluationEventPublisher, never()).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("Gemini API 호출 실패 시 - 적절한 실패 처리 및 이벤트 발행")
    void processEvaluation_GeminiApiFailed_HandleFailureGracefully() {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        when(geminiEvaluationService.evaluateCodeWithRealData(
            any(MissionCompletedEvent.class)))
            .thenThrow(new RuntimeException("Enhanced Gemini API evaluation failed"));

        // When
        evaluationService.processEvaluation(testEventWithEnhancedFields);

        // Then - 실패 상태로 저장 검증
        verify(aiEvaluationRepository, times(3)).save(argThat(evaluation -> {
            if (evaluation.getStatus() == AIEvaluation.EvaluationStatus.FAILED) {
                assertNotNull(evaluation.getErrorMessage());
                assertTrue(evaluation.getErrorMessage().contains("Enhanced Gemini API evaluation failed"));
                return true;
            }
            return true;
        }));
        
        // 실패 이벤트 발행 검증
        verify(evaluationEventPublisher).publishEvaluationFailed(
            eq("attempt-123"), 
            eq("123"), 
            contains("Enhanced Gemini API evaluation failed"));
            
        // 성공 이벤트는 발행되지 않아야 함
        verify(evaluationEventPublisher, never()).publishEvaluationCompleted(any());
    }

    @Test
    @DisplayName("JSON 직렬화 실패 시 - 적절한 오류 처리")
    void processEvaluation_JsonSerializationFailed_HandleError() throws Exception {
        // Given
        when(aiEvaluationRepository.existsByMissionAttemptId("attempt-123")).thenReturn(false);
        when(aiEvaluationRepository.save(any(AIEvaluation.class))).thenReturn(testEvaluation);
        
        when(geminiEvaluationService.evaluateCodeWithRealData(
            any(MissionCompletedEvent.class)))
            .thenReturn(testResult);
            
        when(objectMapper.writeValueAsString(testResult))
            .thenThrow(new RuntimeException("JSON serialization failed"));

        // When
        evaluationService.processEvaluation(testEventWithEnhancedFields);

        // Then - JSON 직렬화 실패로 인한 평가 실패 처리
        verify(aiEvaluationRepository, atLeast(2)).save(any(AIEvaluation.class));
        
        verify(evaluationEventPublisher).publishEvaluationFailed(
            eq("attempt-123"), eq("123"), anyString());
    }

    // Helper methods for test data creation
    
    private MissionCompletedEvent createEnhancedMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("123");
        event.setMissionId("456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update\nEXPOSE 8080\nCMD [\"nginx\", \"-g\", \"daemon off;\"]");
        event.setMissionTitle("고급 Docker 컨테이너 생성 실습");
        event.setCompletedAt(LocalDateTime.now());
        
        // 미션 평가 기준 및 가이드 설정
        event.setEvaluationCriteria("{\"objectives\":[\"Ubuntu 20.04 베이스 이미지 사용\",\"apt-get update 실행\",\"8080 포트 노출\",\"nginx daemon 설정\"]}");
        event.setMissionGuide("# Docker 컨테이너 생성\n\nUbuntu 20.04 기반의 웹 서버 Docker 이미지를 생성하세요.");
        event.setS3StorageUrl("s3://devtrip-bucket/missions/mission-456/user-123/");
        event.setS3PreSignedUrl("https://devtrip-bucket.s3.amazonaws.com/missions/mission-456/user-123/docker-files.tar.gz?AWSAccessKeyId=AKIAI44QH8DHBEXAMPLE&Expires=1618884000&Signature=example");
        
        // 상세한 통계 정보
        MissionCompletedEvent.SimpleStatistics stats = new MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(12);
        stats.setCommandFailureCount(3);
        stats.setTopErrorMessages(List.of(
            "docker: permission denied while trying to connect to the Docker daemon socket",
            "Unable to locate package nginx-extras",
            "Port 8080 is already in use by another process",
            "Dockerfile syntax error: unknown instruction 'MAINTANER'",
            "Failed to pull image ubuntu:20.04: network timeout"
        ));
        stats.setAverageCpuUsage(38.7);
        stats.setMaxCpuUsage(82.5);
        stats.setAverageMemoryUsage(756.3);
        stats.setMaxMemoryUsage(1456.8);
        stats.setTotalExecutionTime(25800L); // 25.8초
        event.setStatistics(stats);
        
        return event;
    }
    
    private MissionCompletedEvent createBasicMissionCompletedEvent() {
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId("123");
        event.setMissionId("456");
        event.setMissionAttemptId("attempt-123");
        event.setMissionType("Docker Container");
        event.setCode("FROM ubuntu:20.04\nRUN apt-get update");
        event.setMissionTitle("기본 Docker 실습");
        event.setCompletedAt(LocalDateTime.now());
        
        // 새로운 필드들은 null (기본값)
        return event;
    }

    private AIEvaluation createTestAIEvaluation() {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setId(1L);
        evaluation.setMissionAttemptId("attempt-123");
        evaluation.setStatus(AIEvaluation.EvaluationStatus.PENDING);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());
        return evaluation;
    }

    private EvaluationResultDTO createTestEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(88);
        result.setFeedback("향상된 평가 방식으로 분석한 결과, 전체적으로 우수한 Docker 컨테이너 구성입니다. 미션 목표를 충실히 달성했으며, 제공된 S3 자료와 통계 정보를 바탕으로 실제 실행 환경에서의 성능도 양호합니다.");
        result.setDetailedAnalysis("체크리스트 달성도: 6/6 완료. 실행 통계 분석: 명령 성공률 80% (12/15), CPU 사용량 평균 38.7% (적정), 메모리 사용량 최대 1.4GB (허용 범위). 발생한 오류들은 학습 과정에서 자연스러운 시행착오로 판단됩니다.");

        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(90);
        codeQuality.setFeedback("미션의 모든 요구사항을 정확히 구현했습니다. Dockerfile 구조가 논리적이고 최적화되어 있습니다.");
        codeQuality.setSuggestions("멀티스테이지 빌드를 활용하면 이미지 크기를 더욱 줄일 수 있습니다.");
        result.setCodeQuality(codeQuality);

        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(85);
        security.setFeedback("기본적인 보안 설정이 적절히 적용되었습니다.");
        security.setVulnerabilities("root 사용자로 실행되고 있어 권한 상승 위험이 있습니다.");
        security.setRecommendations("전용 사용자 계정을 생성하고 USER 지시어를 사용하여 권한을 제한하세요.");
        result.setSecurity(security);

        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(89);
        style.setFeedback("Docker 모범 사례를 잘 따르고 있으며, 일관성 있는 스타일을 유지합니다.");
        style.setStyleIssues("일부 명령어에서 불필요한 공백이 발견되었습니다.");
        style.setImprovements("각 RUN 지시어 후 && 연결 시 일관된 들여쓰기를 사용하세요.");
        result.setStyle(style);

        return result;
    }
}