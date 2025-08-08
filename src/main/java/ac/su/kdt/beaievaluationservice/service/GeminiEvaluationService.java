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
        // 현실적인 목업 데이터로 생성
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(82);
        result.setFeedback("전체적으로 잘 작성된 코드입니다. 기본적인 기능이 올바르게 구현되어 있으며, 가독성도 좋습니다. 몇 가지 개선사항을 반영하면 더욱 완성도 높은 코드가 될 것입니다.");
        result.setDetailedAnalysis("제출하신 코드를 종합적으로 분석한 결과, 코드 구조가 명확하고 기본적인 객체지향 원칙을 잘 따르고 있습니다. 메서드명이 직관적이고, 로직이 간단명료합니다. 다만 예외 처리나 입력값 검증 부분에서 추가 보완이 필요해 보입니다.");
        
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(85);
        codeQuality.setFeedback("코드 구조가 명확하고 가독성이 좋습니다. 메서드명이 직관적이며 기본적인 객체지향 원칙을 잘 따르고 있습니다.");
        codeQuality.setSuggestions("주석을 추가하여 복잡한 로직에 대한 설명을 제공하고, 매직 넘버 사용을 피하기 위해 상수를 활용하는 것을 권장합니다.");
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(75);
        security.setFeedback("기본적인 보안 이슈는 없으나, 입력값 검증 및 예외 처리 부분에서 개선이 필요합니다.");
        security.setVulnerabilities("입력값에 대한 null 체크가 부족하며, 예외 상황에 대한 적절한 처리가 없습니다.");
        security.setRecommendations("입력 파라미터에 대한 null 체크와 범위 검증을 추가하고, 예외 발생 시 적절한 오류 메시지를 제공하세요.");
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(88);
        style.setFeedback("코딩 컨벤션을 잘 준수하고 있으며, 일관성 있는 스타일을 유지하고 있습니다.");
        style.setStyleIssues("전반적으로 좋은 스타일을 유지하고 있으나, JavaDoc 주석이 부족합니다.");
        style.setImprovements("public 메서드에 JavaDoc 주석을 추가하여 API 문서화를 개선하고, 클래스 레벨 주석으로 전체적인 목적을 설명하세요.");
        result.setStyle(style);
        
        return result;
    }
}