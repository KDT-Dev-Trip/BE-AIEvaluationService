package ac.su.kdt.beaievaluationservice.integration;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.service.EvaluationService;
import ac.su.kdt.beaievaluationservice.testutil.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

// AI 평가 시스템의 End-to-End 통합 테스트
// 1. MissionCompletedEvent 수신
// 2. AIEvaluation 엔티티 생성 (PENDING 상태)
// 3. Gemini API 호출 (비동기)
// 4. Gemini API 응답 처리
//    - 성공: AIEvaluation 상태 COMPLETED로 변경, EvaluationSummary 생성
//    - 실패: AIEvaluation 상태 FAILED로 변경, 에러 메시지 저장
// 5. EvaluationHistory 기록 (상태 변경 이력 저장)

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "gemini.api.key={GEMINI_API_KEY}",
    "gemini.api.url=https://test-gemini-api.com/generate" // 테스트용 URL
})
@DisplayName("AI 평가 시스템 End-to-End 통합 테스트")
class EvaluationIntegrationTest {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private AIEvaluationRepository aiEvaluationRepository;

    @Autowired
    private EvaluationSummaryRepository evaluationSummaryRepository;

    @Autowired
    private EvaluationHistoryRepository evaluationHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestTemplate restTemplate;

    private MissionCompletedEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = TestDataBuilder.missionCompletedEvent()
            .missionAttemptId("integration-test-attempt-001")
            .build();
        //
    }

    @Test
    @DisplayName("전체 평가 프로세스 End-to-End 테스트 - 성공 시나리오")
    void fullEvaluationProcess_Success() throws Exception {
        // Given - Gemini API 성공 응답 설정
        setupSuccessfulGeminiApiResponse();

        // When - 평가 프로세스 실행
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 비동기 처리 완료 대기 및 검증
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. AIEvaluation 엔티티 생성 및 완료 상태 확인
            Optional<AIEvaluation> evaluation = aiEvaluationRepository
                .findByMissionAttemptId(testEvent.getMissionAttemptId());
            
            assertTrue(evaluation.isPresent());
            assertEquals(AIEvaluation.EvaluationStatus.COMPLETED, evaluation.get().getStatus());
            assertNotNull(evaluation.get().getEvaluationResult());
            assertEquals("gemini-1.5-pro", evaluation.get().getAiModelVersion());
            assertNull(evaluation.get().getErrorMessage());

            // 2. EvaluationSummary 생성 확인
            Optional<EvaluationSummary> summary = evaluationSummaryRepository
                .findByMissionAttemptId(testEvent.getMissionAttemptId());
            
            assertTrue(summary.isPresent());
            assertEquals(testEvent.getUserId(), summary.get().getUserId());
            assertEquals(testEvent.getMissionId(), summary.get().getMissionId());
            assertEquals(testEvent.getMissionTitle(), summary.get().getMissionTitle());
            assertEquals(85, summary.get().getOverallScore());
            assertEquals(80, summary.get().getCodeQualityScore());
            assertEquals(90, summary.get().getSecurityScore());
            assertEquals(85, summary.get().getStyleScore());
            assertEquals(evaluation.get(), summary.get().getAiEvaluation());

            // 3. EvaluationHistory 기록 확인
            List<EvaluationHistory> histories = evaluationHistoryRepository
                .findByAiEvaluationOrderByCreatedAtDesc(evaluation.get());
            
            assertEquals(3, histories.size()); // PENDING → PROCESSING → COMPLETED
            
            EvaluationHistory completedHistory = histories.get(0);
            assertEquals(AIEvaluation.EvaluationStatus.COMPLETED, completedHistory.getNewStatus());
            assertEquals(AIEvaluation.EvaluationStatus.PROCESSING, completedHistory.getPreviousStatus());
            assertTrue(completedHistory.getChangeReason().contains("completed successfully"));

            EvaluationHistory processingHistory = histories.get(1);
            assertEquals(AIEvaluation.EvaluationStatus.PROCESSING, processingHistory.getNewStatus());
            assertEquals(AIEvaluation.EvaluationStatus.PENDING, processingHistory.getPreviousStatus());

            EvaluationHistory pendingHistory = histories.get(2);
            assertEquals(AIEvaluation.EvaluationStatus.PENDING, pendingHistory.getNewStatus());
            assertNull(pendingHistory.getPreviousStatus());
        });

        // 4. Gemini API 호출 확인
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("중복 평가 요청 처리 테스트")
    void duplicateEvaluationRequest() throws Exception {
        // Given - 기존 평가 레코드 생성
        AIEvaluation existingEvaluation = TestDataBuilder.aiEvaluation()
            .missionAttemptId(testEvent.getMissionAttemptId())
            .status(AIEvaluation.EvaluationStatus.COMPLETED)
            .build();
        aiEvaluationRepository.save(existingEvaluation);

        // When - 중복 평가 요청
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 새로운 평가가 생성되지 않음
        List<AIEvaluation> evaluations = aiEvaluationRepository
            .findAll().stream()
            .filter(e -> e.getMissionAttemptId().equals(testEvent.getMissionAttemptId()))
            .toList();
        
        assertEquals(1, evaluations.size());
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Gemini API 실패 시나리오 End-to-End 테스트")
    void fullEvaluationProcess_GeminiApiFailed() throws Exception {
        // Given - Gemini API 실패 응답 설정
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("Gemini API connection failed"));

        // When - 평가 프로세스 실행
        evaluationService.processEvaluationAsync(testEvent);

        // Then - 실패 처리 확인
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. AIEvaluation 실패 상태 확인
            Optional<AIEvaluation> evaluation = aiEvaluationRepository
                .findByMissionAttemptId(testEvent.getMissionAttemptId());
            
            assertTrue(evaluation.isPresent());
            assertEquals(AIEvaluation.EvaluationStatus.FAILED, evaluation.get().getStatus());
            assertNotNull(evaluation.get().getErrorMessage());
            assertTrue(evaluation.get().getErrorMessage().contains("Gemini API connection failed"));

            // 2. EvaluationSummary 생성되지 않음 확인
            Optional<EvaluationSummary> summary = evaluationSummaryRepository
                .findByMissionAttemptId(testEvent.getMissionAttemptId());
            assertFalse(summary.isPresent());

            // 3. 실패 이력 기록 확인
            List<EvaluationHistory> histories = evaluationHistoryRepository
                .findByAiEvaluationOrderByCreatedAtDesc(evaluation.get());
            
            assertTrue(histories.size() >= 3); // PENDING → PROCESSING → FAILED
            
            EvaluationHistory failedHistory = histories.stream()
                .filter(h -> h.getNewStatus() == AIEvaluation.EvaluationStatus.FAILED)
                .findFirst()
                .orElseThrow();
            
            assertEquals(AIEvaluation.EvaluationStatus.PROCESSING, failedHistory.getPreviousStatus());
            assertTrue(failedHistory.getChangeReason().contains("Gemini API connection failed"));
        });
    }

    @Test
    @DisplayName("JSON 파싱 실패 시나리오 End-to-End 테스트")
    void fullEvaluationProcess_JsonParsingFailed() throws Exception {
        // Given - 잘못된 JSON 응답 설정
        String invalidGeminiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Invalid JSON content here"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(invalidGeminiResponse, HttpStatus.OK));

        // When - 평가 프로세스 실행
        evaluationService.processEvaluationAsync(testEvent);

        // Then - Fallback 처리 확인
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1. AIEvaluation 완료 상태 확인 (Fallback 결과 사용)
            Optional<AIEvaluation> evaluation = aiEvaluationRepository
                .findByMissionAttemptId(testEvent.getMissionAttemptId());
            
            assertTrue(evaluation.isPresent());
            assertEquals(AIEvaluation.EvaluationStatus.COMPLETED, evaluation.get().getStatus());
            assertNotNull(evaluation.get().getEvaluationResult());

            // 2. EvaluationSummary에서 Fallback 점수 확인
            Optional<EvaluationSummary> summary = evaluationSummaryRepository
                .findByMissionAttemptId(testEvent.getMissionAttemptId());
            
            assertTrue(summary.isPresent());
            assertEquals(50, summary.get().getOverallScore()); // Fallback 점수
            assertTrue(summary.get().getFeedbackSummary().contains("AI 평가 중 오류가 발생했습니다"));
        });
    }

    @Test
    @DisplayName("데이터베이스 제약조건 테스트")
    void databaseConstraints_Test() {
        // Given - 동일한 missionAttemptId로 두 개의 평가 생성 시도
        AIEvaluation evaluation1 = TestDataBuilder.aiEvaluation()
            .missionAttemptId("duplicate-test-001")
            .build();
        
        AIEvaluation evaluation2 = TestDataBuilder.aiEvaluation()
            .id(2L)
            .missionAttemptId("duplicate-test-001")
            .build();

        // When & Then - 중복 제약조건 위반 확인
        aiEvaluationRepository.save(evaluation1);
        
        assertThrows(Exception.class, () -> {
            aiEvaluationRepository.save(evaluation2);
            aiEvaluationRepository.flush(); // 즉시 DB 반영하여 제약조건 확인
        });
    }

    private void setupSuccessfulGeminiApiResponse() {
        String successfulGeminiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"codeQuality\\": {\\"score\\": 80, \\"feedback\\": \\"Code structure is well organized\\", \\"suggestions\\": \\"Add more comments\\"}, \\"security\\": {\\"score\\": 90, \\"feedback\\": \\"No major security issues\\", \\"vulnerabilities\\": \\"None detected\\", \\"recommendations\\": \\"Continue security practices\\"}, \\"style\\": {\\"score\\": 85, \\"feedback\\": \\"Consistent style\\", \\"styleIssues\\": \\"Minor issues\\", \\"improvements\\": \\"Use consistent indentation\\"}, \\"overallScore\\": 85, \\"feedback\\": \\"Overall good code quality\\", \\"detailedAnalysis\\": \\"Detailed analysis...\\"}"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(successfulGeminiResponse, HttpStatus.OK));
    }
}