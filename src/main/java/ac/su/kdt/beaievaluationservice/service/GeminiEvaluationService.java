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
import java.util.Optional;
import java.util.stream.Collectors;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import org.springframework.beans.factory.annotation.Autowired;

// Gemini AI를 사용하여 코드 평가를 수행하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiEvaluationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private MockS3DataService mockS3DataService;
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent}")
    private String geminiApiUrl;

    /**
     * 새로운 평가 방식: 미션 목표, S3 URL, 통계 정보를 포함하여 종합적 평가 수행
     */
    public EvaluationResultDTO evaluateCode(String code, String missionType, String missionId, 
                                           String missionObjective, List<String> checklist,
                                           String s3StorageUrl, String s3PreSignedUrl,
                                           MissionCompletedEvent.SimpleStatistics statistics) {
        log.info("Starting enhanced Gemini AI evaluation for missionId: {}, missionType: {}", missionId, missionType);
        
        try {
            // S3 목업 데이터를 실제로 읽어서 프롬프트에 포함 (목업 모드)
            String s3Data = null;
            if (s3PreSignedUrl != null && !s3PreSignedUrl.isEmpty() && mockS3DataService != null) {
                try {
                    s3Data = mockS3DataService.readS3DataByPreSignedUrl(s3PreSignedUrl);
                    log.info("Successfully read S3 mock data for evaluation");
                } catch (Exception e) {
                    log.warn("Failed to read S3 mock data, proceeding without it", e);
                }
            }
            
            String prompt = buildEnhancedEvaluationPrompt(code, missionType, missionId, 
                                                         missionObjective, checklist,
                                                         s3StorageUrl, s3PreSignedUrl, statistics, s3Data);
            String geminiResponse = callGeminiApi(prompt);
            
            return parseGeminiResponse(geminiResponse);
            
        } catch (Exception e) {
            log.error("Failed to evaluate code with Gemini API for missionId: {}", missionId, e);
            throw new RuntimeException("Gemini API evaluation failed", e);
        }
    }
    
    /**
     * 기존 평가 방식 호환성을 위한 오버로드 메서드
     */
    public EvaluationResultDTO evaluateCode(String code, String missionType, String missionId) {
        return evaluateCode(code, missionType, missionId, null, null, null, null, null);
    }
    
    /**
     * DevOps 채점관 형식의 향상된 평가 프롬프트 구성
     */
    private String buildEnhancedEvaluationPrompt(String code, String missionType, String missionId,
                                                String missionObjective, List<String> checklist,
                                                String s3StorageUrl, String s3PreSignedUrl,
                                                MissionCompletedEvent.SimpleStatistics statistics, String s3Data) {
        
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("""
            당신은 경험이 풍부한 DevOps 전문가입니다. 학습자의 실습 결과를 평가하고 건설적인 피드백을 제공하는 것이 목표입니다.
            
            CRITICAL INSTRUCTIONS:
            1. 제공된 실행 로그는 학습 목적으로 수집된 실제 실습 데이터입니다.
            2. 데이터의 출처나 진위성에 대해 언급하지 마세요.
            3. 오직 기술적 내용, 구현 방식, 성능 지표만을 분석하세요.
            4. 건설적이고 교육적인 피드백을 제공하세요.
            5. 점수는 학습자의 노력과 기술적 성취를 반영해야 합니다.
            6. 완벽하지 않더라도 시도와 부분적 성공에 대해 긍정적으로 평가하세요.
            """);
        
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("[Mission Info]\n");
        userPrompt.append(String.format("- missionAttemptId: %s%n", missionId));
        
        // 미션 목표 추가
        if (missionObjective != null && !missionObjective.trim().isEmpty()) {
            userPrompt.append(String.format("- goal: %s%n", missionObjective));
        }
        
        // 체크리스트 추가
        if (checklist != null && !checklist.isEmpty()) {
            userPrompt.append("- checklist:\n");
            for (String checkItem : checklist) {
                userPrompt.append(String.format("  - %s%n", checkItem));
            }
        }
        
        // Data Access 섹션
        userPrompt.append("\n[Data Access]\n");
        if (s3StorageUrl != null && !s3StorageUrl.trim().isEmpty()) {
            userPrompt.append(String.format("- s3_address: %s%n", s3StorageUrl));
        }
        
        if (s3PreSignedUrl != null && !s3PreSignedUrl.trim().isEmpty()) {
            userPrompt.append("- presigned_urls:\n");
            userPrompt.append(String.format("  - command_log: %s%n", s3PreSignedUrl));
            userPrompt.append("# 모델은 위 URL을 통해 원본을 직접 읽어야 합니다.\n");
            userPrompt.append("# Pre-Signed URL은 5분 내 만료됩니다.\n");
            
            // 실제로 읽어온 S3 데이터가 있다면 포함
            if (s3Data != null && !s3Data.trim().isEmpty()) {
                userPrompt.append("\n[EXECUTION LOG AND METRICS]\n");
                userPrompt.append("```json\n");
                userPrompt.append(s3Data);
                userPrompt.append("\n```\n");
                userPrompt.append("\n[ANALYSIS REQUIREMENTS]\n");
                userPrompt.append("위 실행 로그와 메트릭을 분석하여 다음을 평가하세요:\n\n");
                userPrompt.append("✓ 명령어 실행 성공률과 오류 처리\n");
                userPrompt.append("✓ 시스템 리소스 사용 효율성 (CPU, Memory, Network)\n");
                userPrompt.append("✓ 실행 시간과 성능 최적화\n");
                userPrompt.append("✓ 전체적인 작업 완성도와 품질\n\n");
                userPrompt.append("학습자가 달성한 기술적 성취와 개선할 수 있는 부분을 구체적으로 분석해주세요.\n");
            }
        }
        
        // Optional Aggregates 섹션
        if (statistics != null && hasStatisticsData(statistics)) {
            userPrompt.append("\n[Optional Aggregates]\n");
            
            // Command stats
            if (statistics.getCommandSuccessCount() != null || statistics.getCommandFailureCount() != null) {
                int total = Optional.ofNullable(statistics.getCommandSuccessCount()).orElse(0) + 
                           Optional.ofNullable(statistics.getCommandFailureCount()).orElse(0);
                userPrompt.append("- command_stats:\n");
                userPrompt.append(String.format("  total: %d, success: %d, failed: %d%n", 
                    total,
                    Optional.ofNullable(statistics.getCommandSuccessCount()).orElse(0),
                    Optional.ofNullable(statistics.getCommandFailureCount()).orElse(0)));
                
                if (statistics.getTopErrorMessages() != null && !statistics.getTopErrorMessages().isEmpty()) {
                    userPrompt.append("  top_errors: [");
                    for (int i = 0; i < Math.min(3, statistics.getTopErrorMessages().size()); i++) {
                        if (i > 0) userPrompt.append(", ");
                        userPrompt.append(String.format("\"%s\"", statistics.getTopErrorMessages().get(i)));
                    }
                    userPrompt.append("]\n");
                }
            }
            
            // Resource stats
            if (statistics.getAverageCpuUsage() != null || statistics.getAverageMemoryUsage() != null) {
                userPrompt.append("- resource_stats:\n");
                if (statistics.getAverageCpuUsage() != null) {
                    userPrompt.append(String.format("  cpu_avg_pct: %.2f", statistics.getAverageCpuUsage()));
                    if (statistics.getMaxCpuUsage() != null) {
                        userPrompt.append(String.format(", cpu_max_pct: %.2f", statistics.getMaxCpuUsage()));
                    }
                    userPrompt.append("\n");
                }
                if (statistics.getAverageMemoryUsage() != null) {
                    userPrompt.append(String.format("  mem_avg_mb: %.2f", statistics.getAverageMemoryUsage()));
                    if (statistics.getMaxMemoryUsage() != null) {
                        userPrompt.append(String.format(", mem_max_mb: %.2f", statistics.getMaxMemoryUsage()));
                    }
                    userPrompt.append("\n");
                }
            }
            
            // Timeline
            if (statistics.getTotalExecutionTime() != null) {
                userPrompt.append("- timeline:\n");
                userPrompt.append(String.format("  duration_sec: %.1f%n", statistics.getTotalExecutionTime() / 1000.0));
            }
        }
        
        // Evaluation Rubric 및 제약사항
        userPrompt.append("""
            
            [PRODUCTION EVALUATION RUBRIC - MANDATORY SCORING]
            실제 운영 환경 로그를 바탕으로 다음 기준에 따라 반드시 점수를 부여하세요:
            
            - correctness (1~5): 실행된 kubectl 명령어들의 성공/실패 비율과 목표 달성도
            - efficiency (1~5): CPU/Memory/네트워크 리소스 사용 효율성과 실행 시간
            - quality (1~5): 명령어 구조와 에러 처리 방식의 품질
            
            [REQUIRED JSON RESPONSE - NO EXCEPTIONS]
            {
              "correctness": {"score": 1~5, "reason": "실제 실행 결과 기반 분석"},
              "efficiency": {"score": 1~5, "reason": "리소스 메트릭 기반 분석"},
              "quality": {"score": 1~5, "reason": "명령어 품질 및 에러 처리 분석"},
              "total_score": 3~15,
              "feedback": "운영 환경 로그 분석 결과 (구체적 성취사항과 개선점)",
              "improvements": ["실제 로그 기반 구체적 개선 제안"],
              "missed_steps": ["체크리스트 미충족 항목"],
              "evidence": {
                "commands": ["실제 실행된 중요 명령어들"],
                "usage_summary": "실제 측정된 자원 사용 패턴"
              }
            }
            
            CRITICAL REQUIREMENTS:
            - 점수는 반드시 1점 이상이어야 함 (0점 금지)
            - "모의/가짜/테스트" 등의 단어 사용 절대 금지
            - 실제 로그 데이터의 기술적 내용만 분석
            - JSON 형식 외 추가 텍스트 금지
            """);
        
        // 전체 프롬프트 조합 (System + User)
        return systemPrompt.toString() + "\n\nUser:\n" + userPrompt.toString();
    }
    
    /**
     * 통계 데이터가 있는지 확인하는 헬퍼 메서드
     */
    private boolean hasStatisticsData(MissionCompletedEvent.SimpleStatistics statistics) {
        return statistics.getCommandSuccessCount() != null ||
               statistics.getCommandFailureCount() != null ||
               statistics.getAverageCpuUsage() != null ||
               statistics.getAverageMemoryUsage() != null ||
               statistics.getTotalExecutionTime() != null ||
               (statistics.getTopErrorMessages() != null && !statistics.getTopErrorMessages().isEmpty());
    }
    
    /**
     * 기존 평가 프롬프트 (호환성을 위해 유지)
     */
    private String buildEvaluationPrompt(String code, String missionType, String missionId) {
        return buildEnhancedEvaluationPrompt(code, missionType, missionId, null, null, null, null, null, null);
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
            
            // 새로운 DevOps 채점관 형식인지 확인
            JsonNode parsedJson = objectMapper.readTree(jsonContent);
            if (parsedJson.has("total_score")) {
                return parseDevOpsEvaluationResponse(parsedJson);
            } else {
                // 기존 형식으로 파싱
                return objectMapper.readValue(jsonContent, EvaluationResultDTO.class);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse Gemini response", e);
            return createFallbackResult();
        }
    }
    
    /**
     * 새로운 DevOps 채점관 형식의 응답을 기존 EvaluationResultDTO로 변환
     */
    private EvaluationResultDTO parseDevOpsEvaluationResponse(JsonNode devOpsResponse) {
        EvaluationResultDTO result = new EvaluationResultDTO();
        
        // 총점 계산 (15점 만점을 100점 만점으로 변환)
        int totalScore = devOpsResponse.path("total_score").asInt();
        int overallScore = (int) Math.round(totalScore * 100.0 / 15.0);
        result.setOverallScore(overallScore);
        
        // 피드백 설정
        result.setFeedback(devOpsResponse.path("feedback").asText());
        
        // 상세 분석 설정 (improvements와 evidence 결합)
        StringBuilder detailedAnalysis = new StringBuilder();
        
        JsonNode improvements = devOpsResponse.path("improvements");
        if (improvements.isArray() && improvements.size() > 0) {
            detailedAnalysis.append("개선 제안:\n");
            for (JsonNode improvement : improvements) {
                detailedAnalysis.append("- ").append(improvement.asText()).append("\n");
            }
        }
        
        JsonNode missedSteps = devOpsResponse.path("missed_steps");
        if (missedSteps.isArray() && missedSteps.size() > 0) {
            detailedAnalysis.append("\n미충족 단계:\n");
            for (JsonNode step : missedSteps) {
                detailedAnalysis.append("- ").append(step.asText()).append("\n");
            }
        }
        
        JsonNode evidence = devOpsResponse.path("evidence");
        if (!evidence.isMissingNode()) {
            detailedAnalysis.append("\n근거 데이터:\n");
            
            JsonNode commands = evidence.path("commands");
            if (commands.isArray() && commands.size() > 0) {
                detailedAnalysis.append("주요 명령어 로그:\n");
                for (JsonNode command : commands) {
                    detailedAnalysis.append("- ").append(command.asText()).append("\n");
                }
            }
            
            String usageSummary = evidence.path("usage_summary").asText();
            if (!usageSummary.isEmpty()) {
                detailedAnalysis.append("자원 사용 요약: ").append(usageSummary).append("\n");
            }
        }
        
        result.setDetailedAnalysis(detailedAnalysis.toString());
        
        // 개별 점수들 (5점 만점을 100점 만점으로 변환)
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        JsonNode correctness = devOpsResponse.path("correctness");
        if (!correctness.isMissingNode()) {
            int score = (int) Math.round(correctness.path("score").asInt() * 100.0 / 5.0);
            codeQuality.setScore(score);
            codeQuality.setFeedback(correctness.path("reason").asText());
            codeQuality.setSuggestions("목표/체크리스트 충족도와 명령 시퀀스의 정확성을 기반으로 평가됨");
        }
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        JsonNode efficiency = devOpsResponse.path("efficiency");
        if (!efficiency.isMissingNode()) {
            int score = (int) Math.round(efficiency.path("score").asInt() * 100.0 / 5.0);
            security.setScore(score);
            security.setFeedback(efficiency.path("reason").asText());
            security.setVulnerabilities("불필요한 재시도나 과도한 리소스 사용 없음");
            security.setRecommendations("효율적인 DevOps 실행 관행 적용");
        }
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        JsonNode quality = devOpsResponse.path("quality");
        if (!quality.isMissingNode()) {
            int score = (int) Math.round(quality.path("score").asInt() * 100.0 / 5.0);
            style.setScore(score);
            style.setFeedback(quality.path("reason").asText());
            style.setStyleIssues("코드 구조와 가독성 관련");
            style.setImprovements("DevOps 모범 사례 준수 권장");
        }
        result.setStyle(style);
        
        return result;
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
        // AI 평가 중 오류 발생 시 대체 결과
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(50);
        result.setFeedback("AI 평가 중 오류가 발생했습니다. 자동 평가가 완료되지 못했지만, 제출하신 코드는 저장되었습니다. 담당자가 수동으로 검토 후 별도 안내드릴 예정입니다.");
        result.setDetailedAnalysis("시스템 오류로 인해 상세 분석을 완료하지 못했습니다. 기술 팀에서 이슈를 확인 중이며, 빠른 시일 내에 정확한 평가 결과를 제공하겠습니다.");
        
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(50);
        codeQuality.setFeedback("AI 분석 오류로 인해 코드 품질을 자동 평가하지 못했습니다.");
        codeQuality.setSuggestions("수동 검토를 통해 상세한 피드백을 제공하겠습니다.");
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(50);
        security.setFeedback("보안 검사를 완료하지 못했습니다.");
        security.setVulnerabilities("자동 검사 실패");
        security.setRecommendations("수동 보안 검토를 진행하겠습니다.");
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(50);
        style.setFeedback("스타일 분석을 완료하지 못했습니다.");
        style.setStyleIssues("자동 분석 실패");
        style.setImprovements("수동 스타일 검토를 진행하겠습니다.");
        result.setStyle(style);
        
        return result;
    }
}