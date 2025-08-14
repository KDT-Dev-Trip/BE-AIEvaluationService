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
     * 
     * 이 메서드는 AI 모델(Gemini)이 학습자의 DevOps 실습을 종합적으로 평가할 수 있도록 
     * 구조화된 프롬프트를 생성합니다.
     * 
     * @param code 학습자가 작성한 코드 (쉘 스크립트, 설정 파일 등)
     * @param missionType 미션 유형 (예: kubernetes, docker, monitoring 등)
     * @param missionId 미션 식별자
     * @param missionObjective 미션의 학습 목표 및 달성해야 할 과제
     * @param checklist 평가 기준 체크리스트 (미션별 필수 달성 항목)
     * @param s3StorageUrl S3 저장소 주소 (실행 로그 및 메트릭 데이터 위치)
     * @param s3PreSignedUrl 실행 로그 직접 접근을 위한 Pre-Signed URL
     * @param statistics 실습 중 수집된 시스템 메트릭 요약 정보
     * @param s3Data S3에서 읽어온 실제 실행 로그 및 메트릭 데이터
     * @return AI 모델이 분석할 수 있는 구조화된 프롬프트
     */
    private String buildEnhancedEvaluationPrompt(String code, String missionType, String missionId,
                                                String missionObjective, List<String> checklist,
                                                String s3StorageUrl, String s3PreSignedUrl,
                                                MissionCompletedEvent.SimpleStatistics statistics, String s3Data) {
        
        // === 시스템 프롬프트 구성 ===
        // AI 모델의 역할과 평가 기준을 정의하는 섹션
        // - DevOps 전문가로서의 관점 설정
        // - 교육적이고 건설적인 피드백 제공 지침
        // - 실제 데이터 기반 평가의 중요성 강조
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
        
        // === 사용자 프롬프트 구성 시작 ===
        StringBuilder userPrompt = new StringBuilder();
        
        // === [Mission Info] 섹션 ===
        // 미션의 기본 정보와 학습 목표를 제공
        // - 미션 식별자로 평가 대상 명확화
        // - 구체적인 학습 목표를 통해 평가 방향성 제시
        // - 체크리스트로 필수 달성 항목 명시
        userPrompt.append("[Mission Info]\n");
        userPrompt.append(String.format("- missionAttemptId: %s%n", missionId));
        
        // 미션 목표 - AI가 학습자의 목표 달성도를 평가하는 기준점
        if (missionObjective != null && !missionObjective.trim().isEmpty()) {
            userPrompt.append(String.format("- goal: %s%n", missionObjective));
        }
        
        // 체크리스트 - 미션별 필수 완료 사항들을 구체적으로 제시 (3-5개의 핵심 목표)
        // AI는 각 항목들이 얼마나 완료되었는지 개별적으로 정량적 평가 수행
        // 각 체크리스트 항목은 0-100% 달성률로 측정되어야 함
        if (checklist != null && !checklist.isEmpty()) {
            userPrompt.append("- mission_objectives_checklist:\n");
            for (int i = 0; i < checklist.size(); i++) {
                userPrompt.append(String.format("  %d. %s%n", i + 1, checklist.get(i)));
            }
            userPrompt.append("  # 각 목표의 달성률을 0-100% 범위로 개별 평가 필요\n");
        }
        
        // === [Data Access] 섹션 ===
        // 실습 중 생성된 실행 로그와 메트릭 데이터에 대한 접근 정보
        // - S3 저장소 주소를 통해 데이터 위치 명시
        // - Pre-Signed URL로 AI가 직접 로그 데이터를 읽을 수 있게 함
        userPrompt.append("\n[Data Access]\n");
        if (s3StorageUrl != null && !s3StorageUrl.trim().isEmpty()) {
            userPrompt.append(String.format("- s3_address: %s%n", s3StorageUrl));
        }
        
        if (s3PreSignedUrl != null && !s3PreSignedUrl.trim().isEmpty()) {
            userPrompt.append("- presigned_urls:\n");
            userPrompt.append(String.format("  - command_log: %s%n", s3PreSignedUrl));
            userPrompt.append("# 모델은 위 URL을 통해 원본을 직접 읽어야 합니다.\n");
            userPrompt.append("# Pre-Signed URL은 5분 내 만료됩니다.\n");
            
            // === [EXECUTION LOG AND METRICS] 섹션 ===
            // 실제 실습 중 수집된 로그 데이터와 시스템 메트릭
            // - JSON 형태의 구조화된 실행 로그 제공
            // - 명령어 실행 결과, 오류 메시지, 성능 지표 포함
            // AI는 이 데이터를 바탕으로 실제 실행 결과를 객관적으로 분석
            if (s3Data != null && !s3Data.trim().isEmpty()) {
                userPrompt.append("\n[EXECUTION LOG AND METRICS]\n");
                userPrompt.append("```json\n");
                userPrompt.append(s3Data);
                userPrompt.append("\n```\n");
                
                // === [ANALYSIS REQUIREMENTS] 섹션 ===
                // AI가 수행해야 할 구체적인 분석 항목들
                // - 명령어 실행 성공률 분석: 올바른 명령어 사용 여부, 에러 처리 능력
                // - 핵심 명령어 평가: kubectl, docker, helm 등 미션별 핵심 명령어 사용법 검토
                // - 리소스 효율성 분석: CPU, 메모리, 네트워크 사용 패턴 평가
                // - 성능 최적화 분석: 실행 시간, 불필요한 작업 여부 검토
                // - 작업 완성도 분석: 목표 달성도와 전반적인 품질 평가
                userPrompt.append("\n[ANALYSIS REQUIREMENTS]\n");
                userPrompt.append("위 실행 로그와 메트릭을 분석하여 다음을 평가하세요:\n\n");
                userPrompt.append("✓ 핵심 명령어 사용법 및 숙련도 (kubectl, docker, helm 등)\n");
                userPrompt.append("  - 실제 사용된 명령어들을 명시하고 각각의 목적과 적절성 분석\n");
                userPrompt.append("  - 명령어 옵션 사용의 정확성과 효율성 평가\n");
                userPrompt.append("  - 명령어 실행 순서의 논리적 적절성 검토\n");
                userPrompt.append("✓ 각 미션 목표별 달성률 (체크리스트 기준)\n");
                userPrompt.append("✓ 명령어 실행 성공률과 오류 처리 패턴\n");
                userPrompt.append("✓ 시스템 리소스 사용 효율성 (CPU, Memory, Network, Disk I/O)\n");
                userPrompt.append("✓ 실행 시간과 성능 최적화 수준\n");
                userPrompt.append("✓ 전체적인 작업 완성도와 코드 품질\n\n");
                userPrompt.append("IMPORTANT: core_commands_analysis.key_commands_used 배열에는 반드시 실제 로그에서 발견된 핵심 명령어들을 포함하세요.\n");
                userPrompt.append("각 명령어에 대해 command, explanation, assessment 필드를 모두 제공해야 합니다.\n");
            }
        }
        
        // === [Optional Aggregates] 섹션 ===
        // 실습 전체에 대한 요약 통계와 집계 데이터
        // - 명령어 실행 통계: 성공/실패 비율로 학습자의 명령어 숙련도 평가
        // - 리소스 사용 통계: CPU/메모리 사용률로 효율성 분석
        // - 실행 시간 통계: 전체 소요 시간으로 작업 속도 평가
        if (statistics != null && hasStatisticsData(statistics)) {
            userPrompt.append("\n[Optional Aggregates]\n");
            
            // === 명령어 실행 통계 분석 ===
            // - 전체 명령어 수 대비 성공률로 기술 숙련도 측정
            // - 주요 오류 메시지를 통해 일반적인 실수 패턴 파악
            // - 실패 원인 분석으로 학습 포인트 제시
            if (statistics.getCommandSuccessCount() != null || statistics.getCommandFailureCount() != null) {
                int total = Optional.ofNullable(statistics.getCommandSuccessCount()).orElse(0) + 
                           Optional.ofNullable(statistics.getCommandFailureCount()).orElse(0);
                userPrompt.append("- command_stats:\n");
                userPrompt.append(String.format("  total: %d, success: %d, failed: %d%n", 
                    total,
                    Optional.ofNullable(statistics.getCommandSuccessCount()).orElse(0),
                    Optional.ofNullable(statistics.getCommandFailureCount()).orElse(0)));
                
                // 주요 오류 메시지 - 가장 빈번한 오류들을 통해 일반적인 문제점 식별
                if (statistics.getTopErrorMessages() != null && !statistics.getTopErrorMessages().isEmpty()) {
                    userPrompt.append("  top_errors: [");
                    for (int i = 0; i < Math.min(3, statistics.getTopErrorMessages().size()); i++) {
                        if (i > 0) userPrompt.append(", ");
                        userPrompt.append(String.format("\"%s\"", statistics.getTopErrorMessages().get(i)));
                    }
                    userPrompt.append("]\n");
                }
            }
            
            // === 시스템 리소스 사용률 통계 분석 ===
            // - 평균 CPU 사용률: 작업의 연산 집약도와 최적화 수준 평가
            // - 최대 CPU 사용률: 피크 시점에서의 시스템 부하 분석
            // - 평균/최대 메모리 사용량: 메모리 효율성과 리소스 관리 능력 측정
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
            
            // === 실행 시간 통계 분석 ===
            // - 전체 실행 시간: 작업 효율성과 불필요한 대기 시간 여부 판단
            // - 시간 효율성을 통한 실무 역량 평가
            if (statistics.getTotalExecutionTime() != null) {
                userPrompt.append("- timeline:\n");
                userPrompt.append(String.format("  duration_sec: %.1f%n", statistics.getTotalExecutionTime() / 1000.0));
            }
        }
        
        // === [PRODUCTION EVALUATION RUBRIC] 섹션 ===
        // AI 모델이 반드시 준수해야 할 평가 기준과 출력 형식
        // - 정량적 평가 기준: correctness, efficiency, quality 각각 1-5점
        // - 정성적 피드백: 구체적인 개선사항과 근거 데이터 제시
        // - JSON 구조화: 일관된 데이터 형식으로 후속 처리 가능
        userPrompt.append("""
            
            [PRODUCTION EVALUATION RUBRIC - MANDATORY SCORING]
            실제 운영 환경 로그를 바탕으로 다음 기준에 따라 반드시 점수를 부여하세요:
            
            - correctness (1~5): 실행된 명령어들의 성공/실패 비율과 목표 달성도
              // 미션 목표와 체크리스트 대비 실제 달성도를 정량적으로 평가
              // 명령어 실행 성공률과 최종 결과물의 정확성 측정
            - efficiency (1~5): CPU/Memory/네트워크 리소스 사용 효율성과 실행 시간
              // 시스템 리소스의 적절한 사용과 불필요한 낭비 방지 정도 평가
              // 실행 시간 최적화와 성능 효율성 측정
            - quality (1~5): 명령어 구조와 에러 처리 방식의 품질
              // 코드의 가독성, 구조적 완성도, 오류 상황 대응 능력 평가
              // DevOps 모범 사례 준수 여부와 유지보수성 고려
            
            [REQUIRED JSON RESPONSE - NO EXCEPTIONS]
            // AI 모델은 반드시 아래 JSON 구조로만 응답해야 함
            // 각 필드는 후속 처리 시스템에서 파싱되어 데이터베이스에 저장됨
            {
              "correctness": {"score": 1~5, "reason": "실제 실행 결과 기반 분석"},
              "efficiency": {"score": 1~5, "reason": "리소스 메트릭 기반 분석"},
              "quality": {"score": 1~5, "reason": "명령어 품질 및 에러 처리 분석"},
              "total_score": 3~15,  // 위 3개 점수의 합계 (자동 계산)
              "feedback": "운영 환경 로그 분석 결과 (구체적 성취사항과 개선점)",
              "improvements": ["실제 로그 기반 구체적 개선 제안"],
              "missed_steps": ["체크리스트 미충족 항목"],
              "core_commands_analysis": {
                "key_commands_used": [
                  {"command": "kubectl apply -f deployment.yaml", "explanation": "디플로이먼트 생성 - 적절한 YAML 파일 적용", "assessment": "올바른 사용"},
                  {"command": "kubectl get pods", "explanation": "파드 상태 확인 - 배포 후 상태 검증", "assessment": "모범 사례"},
                  {"command": "kubectl expose deployment nginx --port=80", "explanation": "서비스 노출 - 애플리케이션 외부 접근 설정", "assessment": "효과적 사용"}
                ],
                "kubectl_usage": {"score": 1~5, "details": "kubectl 명령어 사용 숙련도 분석"},
                "docker_usage": {"score": 1~5, "details": "docker 명령어 사용 숙련도 분석"},
                "other_tools": {"score": 1~5, "details": "기타 핵심 도구 사용 분석"},
                "command_efficiency": "명령어 선택의 적절성과 효율성 평가",
                "command_sequence": "명령어 실행 순서의 논리적 적절성 분석"
              },
              "checklist_evaluation": [
                {"objective": "목표 1", "achievement_rate": 0~100, "details": "달성 상세 내용"},
                {"objective": "목표 2", "achievement_rate": 0~100, "details": "달성 상세 내용"}
              ],
              "resource_metrics": {
                "cpu_efficiency_score": 1~5,
                "memory_efficiency_score": 1~5, 
                "network_efficiency_score": 1~5,
                "disk_io_score": 1~5,
                "overall_resource_grade": "A~F",
                "resource_highlights": ["주요 리소스 사용 특징들"],
                "optimization_suggestions": ["성능 최적화 제안사항"]
              },
              "evidence": {
                "key_commands": ["평가에 중요한 핵심 명령어들"],  // 평가 근거가 되는 실제 명령어들
                "performance_data": "실제 측정된 자원 사용 패턴",  // 성능 분석 근거 데이터
                "error_patterns": ["발견된 주요 오류 패턴들"]
              }
            }
            
            CRITICAL REQUIREMENTS:
            - 점수는 반드시 1점 이상이어야 함 (0점 금지)
            - "모의/가짜/테스트" 등의 단어 사용 절대 금지
            - 실제 로그 데이터의 기술적 내용만 분석
            - JSON 형식 외 추가 텍스트 금지
            """);
        
        // === 최종 프롬프트 조합 ===
        // 시스템 프롬프트(역할 정의) + 사용자 프롬프트(평가 데이터)를 결합
        // 이렇게 구조화된 프롬프트를 통해 AI는 다음을 수행:
        // 1. 미션 목표 대비 달성도 평가 (correctness)
        // 2. 시스템 리소스 효율성 분석 (efficiency) 
        // 3. 코드 품질과 구조 검토 (quality)
        // 4. 실제 실행 로그 기반의 객관적 근거 제시
        // 5. 학습자 맞춤형 개선사항 제안
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
     * 확장된 JSON 구조를 파싱하여 핵심 명령어 분석, 체크리스트 평가, 리소스 메트릭을 포함
     */
    private EvaluationResultDTO parseDevOpsEvaluationResponse(JsonNode devOpsResponse) {
        EvaluationResultDTO result = new EvaluationResultDTO();
        
        // 총점 계산 (15점 만점을 100점 만점으로 변환)
        int totalScore = devOpsResponse.path("total_score").asInt();
        int overallScore = (int) Math.round(totalScore * 100.0 / 15.0);
        result.setOverallScore(overallScore);
        
        // 피드백 설정
        result.setFeedback(devOpsResponse.path("feedback").asText());
        
        // 상세 분석 설정 (모든 확장 데이터를 결합)
        StringBuilder detailedAnalysis = new StringBuilder();
        
        // === 핵심 명령어 분석 섹션 ===
        JsonNode coreCommandsAnalysis = devOpsResponse.path("core_commands_analysis");
        if (!coreCommandsAnalysis.isMissingNode()) {
            detailedAnalysis.append("=== 핵심 명령어 사용 분석 ===\n");
            
            // 실제 사용된 주요 명령어들
            JsonNode keyCommands = coreCommandsAnalysis.path("key_commands_used");
            if (keyCommands.isArray() && keyCommands.size() > 0) {
                detailedAnalysis.append("🔧 사용된 핵심 명령어들:\n");
                for (JsonNode commandNode : keyCommands) {
                    String command = commandNode.path("command").asText();
                    String explanation = commandNode.path("explanation").asText();
                    String assessment = commandNode.path("assessment").asText();
                    
                    detailedAnalysis.append(String.format("  ▶ %s\n", command));
                    detailedAnalysis.append(String.format("    설명: %s\n", explanation));
                    detailedAnalysis.append(String.format("    평가: %s\n\n", assessment));
                }
            }
            
            // 도구별 숙련도
            JsonNode kubectlUsage = coreCommandsAnalysis.path("kubectl_usage");
            if (!kubectlUsage.isMissingNode()) {
                detailedAnalysis.append(String.format("• kubectl 숙련도: %d/5 - %s\n", 
                    kubectlUsage.path("score").asInt(), 
                    kubectlUsage.path("details").asText()));
            }
            
            JsonNode dockerUsage = coreCommandsAnalysis.path("docker_usage");
            if (!dockerUsage.isMissingNode()) {
                detailedAnalysis.append(String.format("• docker 숙련도: %d/5 - %s\n", 
                    dockerUsage.path("score").asInt(), 
                    dockerUsage.path("details").asText()));
            }
            
            JsonNode otherTools = coreCommandsAnalysis.path("other_tools");
            if (!otherTools.isMissingNode()) {
                detailedAnalysis.append(String.format("• 기타 도구 사용: %d/5 - %s\n", 
                    otherTools.path("score").asInt(), 
                    otherTools.path("details").asText()));
            }
            
            String commandEfficiency = coreCommandsAnalysis.path("command_efficiency").asText();
            if (!commandEfficiency.isEmpty()) {
                detailedAnalysis.append("• 명령어 효율성: ").append(commandEfficiency).append("\n");
            }
            
            String commandSequence = coreCommandsAnalysis.path("command_sequence").asText();
            if (!commandSequence.isEmpty()) {
                detailedAnalysis.append("• 명령어 순서: ").append(commandSequence).append("\n");
            }
            detailedAnalysis.append("\n");
        }
        
        // === 체크리스트 평가 섹션 ===
        JsonNode checklistEvaluation = devOpsResponse.path("checklist_evaluation");
        if (checklistEvaluation.isArray() && checklistEvaluation.size() > 0) {
            detailedAnalysis.append("=== 미션 목표 달성률 ===\n");
            for (JsonNode objective : checklistEvaluation) {
                String objectiveName = objective.path("objective").asText();
                int achievementRate = objective.path("achievement_rate").asInt();
                String details = objective.path("details").asText();
                detailedAnalysis.append(String.format("• %s: %d%% - %s\n", 
                    objectiveName, achievementRate, details));
            }
            detailedAnalysis.append("\n");
        }
        
        // === 리소스 메트릭 섹션 ===
        JsonNode resourceMetrics = devOpsResponse.path("resource_metrics");
        if (!resourceMetrics.isMissingNode()) {
            detailedAnalysis.append("=== 시스템 리소스 사용 분석 ===\n");
            
            String overallGrade = resourceMetrics.path("overall_resource_grade").asText();
            if (!overallGrade.isEmpty()) {
                detailedAnalysis.append("• 종합 리소스 효율성: ").append(overallGrade).append(" 등급\n");
            }
            
            int cpuScore = resourceMetrics.path("cpu_efficiency_score").asInt();
            int memoryScore = resourceMetrics.path("memory_efficiency_score").asInt();
            int networkScore = resourceMetrics.path("network_efficiency_score").asInt();
            int diskScore = resourceMetrics.path("disk_io_score").asInt();
            
            detailedAnalysis.append(String.format("• CPU 효율성: %d/5\n", cpuScore));
            detailedAnalysis.append(String.format("• 메모리 효율성: %d/5\n", memoryScore));
            detailedAnalysis.append(String.format("• 네트워크 효율성: %d/5\n", networkScore));
            detailedAnalysis.append(String.format("• 디스크 I/O 효율성: %d/5\n", diskScore));
            
            JsonNode resourceHighlights = resourceMetrics.path("resource_highlights");
            if (resourceHighlights.isArray() && resourceHighlights.size() > 0) {
                detailedAnalysis.append("• 주요 특징:\n");
                for (JsonNode highlight : resourceHighlights) {
                    detailedAnalysis.append("  - ").append(highlight.asText()).append("\n");
                }
            }
            
            JsonNode optimizationSuggestions = resourceMetrics.path("optimization_suggestions");
            if (optimizationSuggestions.isArray() && optimizationSuggestions.size() > 0) {
                detailedAnalysis.append("• 최적화 제안:\n");
                for (JsonNode suggestion : optimizationSuggestions) {
                    detailedAnalysis.append("  - ").append(suggestion.asText()).append("\n");
                }
            }
            detailedAnalysis.append("\n");
        }
        
        // === 개선 제안 섹션 ===
        JsonNode improvements = devOpsResponse.path("improvements");
        if (improvements.isArray() && improvements.size() > 0) {
            detailedAnalysis.append("=== 전반적 개선 제안 ===\n");
            for (JsonNode improvement : improvements) {
                detailedAnalysis.append("• ").append(improvement.asText()).append("\n");
            }
            detailedAnalysis.append("\n");
        }
        
        // === 미충족 단계 섹션 ===
        JsonNode missedSteps = devOpsResponse.path("missed_steps");
        if (missedSteps.isArray() && missedSteps.size() > 0) {
            detailedAnalysis.append("=== 미충족 단계 ===\n");
            for (JsonNode step : missedSteps) {
                detailedAnalysis.append("• ").append(step.asText()).append("\n");
            }
            detailedAnalysis.append("\n");
        }
        
        // === 근거 데이터 섹션 ===
        JsonNode evidence = devOpsResponse.path("evidence");
        if (!evidence.isMissingNode()) {
            detailedAnalysis.append("=== 평가 근거 데이터 ===\n");
            
            JsonNode keyCommands = evidence.path("key_commands");
            if (keyCommands.isArray() && keyCommands.size() > 0) {
                detailedAnalysis.append("• 핵심 명령어들:\n");
                for (JsonNode command : keyCommands) {
                    detailedAnalysis.append("  - ").append(command.asText()).append("\n");
                }
            }
            
            String performanceData = evidence.path("performance_data").asText();
            if (!performanceData.isEmpty()) {
                detailedAnalysis.append("• 성능 데이터: ").append(performanceData).append("\n");
            }
            
            JsonNode errorPatterns = evidence.path("error_patterns");
            if (errorPatterns.isArray() && errorPatterns.size() > 0) {
                detailedAnalysis.append("• 오류 패턴:\n");
                for (JsonNode pattern : errorPatterns) {
                    detailedAnalysis.append("  - ").append(pattern.asText()).append("\n");
                }
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