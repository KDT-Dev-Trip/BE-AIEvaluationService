package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Gemini AI를 사용하여 코드 평가를 수행하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiEvaluationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent}")
    private String geminiApiUrl;

    public EvaluationResultDTO evaluateCode(String code, String missionType, String missionId) {
        log.info("Starting Gemini AI evaluation for missionId: {}, missionType: {}", missionId, missionType);
        
        try {
            String prompt = buildEvaluationPrompt(code, missionType, missionId);
            String geminiResponse = callGeminiApi(prompt);
            
            return parseGeminiResponse(geminiResponse);
            
        } catch (Exception e) {
            log.error("Failed to evaluate code with Gemini API for missionId: {}", missionId, e);
            throw new RuntimeException("Gemini API evaluation failed", e);
        }
    }
    
    private String buildEvaluationPrompt(String code, String missionType, String missionId) {
        return String.format("""
            DevOps 교육 플랫폼의 %s 미션에 대한 코드를 평가해주세요.
            
            미션 ID: %s
            미션 타입: %s
            
            평가할 코드:
            ```
            %s
            ```
            
            다음 기준으로 평가하고 JSON 형태로 응답해주세요:
            
            1. 코드 품질 (Code Quality) - 100점 만점
               - 코드 구조와 가독성
               - 함수/변수 네이밍
               - 주석 적절성
               - 모듈화 정도
            
            2. 보안 (Security) - 100점 만점  
               - 보안 취약점 존재 여부
               - 민감한 정보 노출 위험
               - 권한 관리 적절성
               - 안전한 코딩 패턴 적용
            
            3. 스타일 (Style) - 100점 만점
               - 코딩 컨벤션 준수
               - 들여쓰기와 포맷팅
               - 일관성 있는 스타일
            
            응답 형식:
            {
              "codeQuality": {
                "score": 점수(0-100),
                "feedback": "피드백 메시지",
                "suggestions": "개선 제안"
              },
              "security": {
                "score": 점수(0-100), 
                "feedback": "보안 피드백",
                "vulnerabilities": "발견된 취약점",
                "recommendations": "보안 권장사항"
              },
              "style": {
                "score": 점수(0-100),
                "feedback": "스타일 피드백", 
                "styleIssues": "발견된 스타일 이슈",
                "improvements": "개선 방안"
              },
              "overallScore": 전체점수(0-100),
              "feedback": "전체 피드백",
              "detailedAnalysis": "상세 분석 내용"
            }
            """, missionType, missionId, missionType, code);
    }
    
    private String callGeminiApi(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, String> part = new HashMap<>();
            part.put("text", prompt);
            content.put("parts", List.of(part));
            requestBody.put("contents", List.of(content));
            
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.3);
            generationConfig.put("topP", 0.8);
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.put("generationConfig", generationConfig);
            
            String url = geminiApiUrl + "?key=" + geminiApiKey;
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new RuntimeException("Gemini API call failed with status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }
    
    private EvaluationResultDTO parseGeminiResponse(String geminiResponse) {
        try {
            JsonNode responseJson = objectMapper.readTree(geminiResponse);
            String content = responseJson
                .path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();
            
            // JSON 부분만 추출 (마크다운 형태로 감싸져 있을 수 있음)
            String jsonContent = extractJsonFromResponse(content);
            
            return objectMapper.readValue(jsonContent, EvaluationResultDTO.class);
            
        } catch (Exception e) {
            log.error("Failed to parse Gemini response", e);
            return createFallbackResult();
        }
    }
    
    private String extractJsonFromResponse(String content) {
        int jsonStart = content.indexOf("{");
        int jsonEnd = content.lastIndexOf("}") + 1;
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd);
        }
        
        return content;
    }
    
    private EvaluationResultDTO createFallbackResult() {
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(50);
        result.setFeedback("AI 평가 중 오류가 발생했습니다. 기본 점수가 부여되었습니다.");
        result.setDetailedAnalysis("평가 분석을 완료할 수 없어 기본 결과를 제공합니다.");
        
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(50);
        codeQuality.setFeedback("평가 오류로 기본 점수 부여");
        codeQuality.setSuggestions("수동 코드 리뷰를 권장합니다.");
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(50);
        security.setFeedback("보안 평가 오류로 기본 점수 부여");
        security.setVulnerabilities("수동 보안 검토 필요");
        security.setRecommendations("보안 전문가의 검토를 받으시기 바랍니다.");
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(50);
        style.setFeedback("스타일 평가 오류로 기본 점수 부여");
        style.setStyleIssues("스타일 검사 도구 활용 권장");
        style.setImprovements("린터 도구를 사용하여 코드 스타일을 개선하세요.");
        result.setStyle(style);
        
        return result;
    }
}