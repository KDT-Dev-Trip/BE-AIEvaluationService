package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Disabled;

// Gemini AI를 사용하여 코드 평가를 수행하는 서비스의 기능을 검증하는 테스트 (S3 통합 버전에서 일시 비활성화)
@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiEvaluationService 단위 테스트")
@Disabled("S3 통합 버전으로 업데이트 후 새로운 테스트로 대체됨")
class GeminiEvaluationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GeminiEvaluationService geminiEvaluationService;

    private String testCode;
    private String testMissionType;
    private String testMissionId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(geminiEvaluationService, "geminiApiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiEvaluationService, "geminiApiUrl", "https://test-gemini-api.com/generate");
        
        testCode = "FROM ubuntu:20.04\nRUN apt-get update\nEXPOSE 8080";
        testMissionType = "Docker Container";
        testMissionId = "mission-123";
    }

    @Test
    @DisplayName("정상적인 Gemini API 호출 및 응답 처리")
    void evaluateCode_Success() throws Exception {
        // Given
        String geminiResponse = createValidGeminiResponse();
        String expectedJsonContent = createValidJsonResponse();
        EvaluationResultDTO expectedResult = createExpectedEvaluationResult();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));
        when(objectMapper.readValue(expectedJsonContent, EvaluationResultDTO.class))
            .thenReturn(expectedResult);

        // When
        EvaluationResultDTO result = geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId);

        // Then
        assertNotNull(result);
        assertEquals(85, result.getOverallScore());
        assertEquals("Overall good code quality", result.getFeedback());
        assertNotNull(result.getCodeQuality());
        assertNotNull(result.getSecurity());
        assertNotNull(result.getStyle());
        
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(objectMapper).readValue(expectedJsonContent, EvaluationResultDTO.class);
    }

    @Test
    @DisplayName("Gemini API HTTP 에러 응답 처리")
    void evaluateCode_HttpError() {
        // Given
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Error", HttpStatus.BAD_REQUEST));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId));
        
        assertTrue(exception.getMessage().contains("Gemini API call failed"));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("RestTemplate 예외 발생 처리")
    void evaluateCode_RestTemplateException() {
        // Given
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("Connection timeout"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId));
        
        assertTrue(exception.getMessage().contains("Failed to call Gemini API"));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("Gemini 응답 파싱 실패 - Fallback 결과 반환")
    void evaluateCode_ParseResponseFailed() throws Exception {
        // Given
        String geminiResponse = createValidGeminiResponse();
        String expectedJsonContent = createValidJsonResponse();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));
        when(objectMapper.readValue(expectedJsonContent, EvaluationResultDTO.class))
            .thenThrow(new RuntimeException("JSON parsing failed"));

        // When
        EvaluationResultDTO result = geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId);

        // Then - Fallback 결과 검증
        assertNotNull(result);
        assertEquals(50, result.getOverallScore());
        assertTrue(result.getFeedback().contains("AI 평가 중 오류가 발생했습니다"));
        assertNotNull(result.getCodeQuality());
        assertNotNull(result.getSecurity());
        assertNotNull(result.getStyle());
        assertEquals(50, result.getCodeQuality().getScore());
        assertEquals(50, result.getSecurity().getScore());
        assertEquals(50, result.getStyle().getScore());
        
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(objectMapper).readValue(expectedJsonContent, EvaluationResultDTO.class);
    }

    @Test
    @DisplayName("잘못된 Gemini 응답 구조 - Fallback 결과 반환")
    void evaluateCode_InvalidGeminiResponseStructure() throws Exception {
        // Given
        String invalidGeminiResponse = "{\"invalid\": \"structure\"}";

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(invalidGeminiResponse, HttpStatus.OK));

        // When
        EvaluationResultDTO result = geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId);

        // Then - Fallback 결과 검증
        assertNotNull(result);
        assertEquals(50, result.getOverallScore());
        assertTrue(result.getFeedback().contains("AI 평가 중 오류가 발생했습니다"));
        
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("API 요청 URL 및 헤더 검증")
    void evaluateCode_VerifyRequestConfiguration() {
        // Given
        String geminiResponse = createValidGeminiResponse();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(geminiResponse, HttpStatus.OK));

        // When
        geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId);

        // Then - API 호출이 올바른 URL과 설정으로 이루어졌는지 검증
        verify(restTemplate).exchange(
            argThat(url -> url.toString().contains("https://test-gemini-api.com/generate") && url.toString().contains("key=test-api-key")),
            eq(HttpMethod.POST),
            argThat(entity -> {
                // 요청 본문과 헤더 검증
                assertNotNull(entity.getHeaders().getContentType());
                assertNotNull(entity.getBody());
                return true;
            }),
            eq(String.class)
        );
    }

    @Test
    @DisplayName("마크다운으로 감싸진 JSON 응답 처리")
    void evaluateCode_MarkdownWrappedJsonResponse() throws Exception {
        // Given
        String markdownWrappedResponse = createMarkdownWrappedGeminiResponse();
        String expectedJsonContent = createValidJsonResponse();
        EvaluationResultDTO expectedResult = createExpectedEvaluationResult();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(markdownWrappedResponse, HttpStatus.OK));
        when(objectMapper.readValue(expectedJsonContent, EvaluationResultDTO.class))
            .thenReturn(expectedResult);

        // When
        EvaluationResultDTO result = geminiEvaluationService.evaluateCode(testCode, testMissionType, testMissionId);

        // Then
        assertNotNull(result);
        assertEquals(85, result.getOverallScore());
        
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(objectMapper).readValue(expectedJsonContent, EvaluationResultDTO.class);
    }

    private String createValidGeminiResponse() {
        return """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "%s"
                      }
                    ]
                  }
                }
              ]
            }
            """.formatted(createValidJsonResponse());
    }

    private String createMarkdownWrappedGeminiResponse() {
        return """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "```json\\n%s\\n```"
                      }
                    ]
                  }
                }
              ]
            }
            """.formatted(createValidJsonResponse());
    }

    private String createValidJsonResponse() {
        return """
            {
              "codeQuality": {
                "score": 80,
                "feedback": "Code structure is well organized",
                "suggestions": "Add more comments"
              },
              "security": {
                "score": 90,
                "feedback": "No major security issues",
                "vulnerabilities": "None detected",
                "recommendations": "Continue security practices"
              },
              "style": {
                "score": 85,
                "feedback": "Consistent style",
                "styleIssues": "Minor issues",
                "improvements": "Use consistent indentation"
              },
              "overallScore": 85,
              "feedback": "Overall good code quality",
              "detailedAnalysis": "Detailed analysis..."
            }
            """;
    }

    private EvaluationResultDTO createExpectedEvaluationResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(85);
        result.setFeedback("Overall good code quality");
        result.setDetailedAnalysis("Detailed analysis...");

        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(80);
        codeQuality.setFeedback("Code structure is well organized");
        codeQuality.setSuggestions("Add more comments");
        result.setCodeQuality(codeQuality);

        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(90);
        security.setFeedback("No major security issues");
        security.setVulnerabilities("None detected");
        security.setRecommendations("Continue security practices");
        result.setSecurity(security);

        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(85);
        style.setFeedback("Consistent style");
        style.setStyleIssues("Minor issues");
        style.setImprovements("Use consistent indentation");
        result.setStyle(style);

        return result;
    }
}