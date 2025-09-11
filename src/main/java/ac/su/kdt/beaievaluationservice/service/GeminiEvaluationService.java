package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.constants.EvaluationConstants;
import ac.su.kdt.beaievaluationservice.exception.GeminiApiException;
import ac.su.kdt.beaievaluationservice.exception.EvaluationException;

import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.client.MissionDataClient;
import ac.su.kdt.beaievaluationservice.client.EvaluationDataResponse;
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
    
    private final MissionDataClient missionDataClient;
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent}")
    private String geminiApiUrl;
    
    @Value("${gemini.api.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${gemini.api.retry.delay-seconds:2}")
    private int retryDelaySeconds;
    
    @Value("${gemini.api.timeout.connect-seconds:30}")
    private int connectTimeoutSeconds;
    
    @Value("${gemini.api.timeout.read-seconds:120}")
    private int readTimeoutSeconds;

    /**
     * 새로운 평가 방식: Kafka 이벤트에서 실제 실행 데이터를 받아서 종합적 평가 수행
     */
    public EvaluationResultDTO evaluateCodeWithRealData(MissionCompletedEvent event) {
        log.info("Starting Gemini AI evaluation with real execution data for missionId: {}, missionType: {}, attemptId: {}", 
                event.getMissionId(), event.getMissionType(), event.getMissionAttemptId());
        
        try {
            // Kafka 이벤트에서 실제 실행 데이터 추출
            MissionCompletedEvent.RealExecutionData realData = event.getRealExecutionData();
            
            if (realData != null && realData.getStatistics() != null) {
                log.info("실제 실행 데이터 사용: 명령어수={}, 성공률={}%", 
                        realData.getStatistics().getTotalCommands(),
                        realData.getStatistics().getSuccessRate());
            } else {
                log.warn("실제 실행 데이터가 비어있음: attemptId={}", event.getMissionAttemptId());
            }
            
            // 하위 호환성을 위한 S3 Mock 데이터 지원 유지
            String s3Data = null;
            if (event.getS3PreSignedUrl() != null && !event.getS3PreSignedUrl().isEmpty() && mockS3DataService != null) {
                try {
                    s3Data = mockS3DataService.readS3DataByPreSignedUrl(event.getS3PreSignedUrl());
                    log.debug("S3 Mock 데이터도 fallback으로 사용: {} characters", s3Data != null ? s3Data.length() : 0);
                } catch (Exception e) {
                    log.debug("S3 Mock 데이터 조회 실패: {}", e.getMessage());
                }
            }
            
            String prompt = buildEnhancedEvaluationPromptWithRealData(event, s3Data);
            String geminiResponse = callGeminiApi(prompt);
            
            return parseGeminiResponse(geminiResponse);
            
        } catch (RuntimeException e) {
            // RuntimeException은 이미 적절히 처리된 예외이므로 그대로 전파
            log.error("RuntimeException during evaluation for missionId: {}", event.getMissionId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Error during evaluation for missionId: {}", event.getMissionId(), e);
            throw new EvaluationException("Evaluation process failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 기존 평가 방식 호환성을 위한 오버로드 메서드
     */
    /**
     * 기존 평가 방식 - 실제 Gemini API 호출
     */
    public EvaluationResultDTO evaluateCode(String code, String missionType, String missionId) {
        log.info("Starting basic Gemini AI evaluation for missionId: {}", missionId);
        
        try {
            String prompt = buildBasicEvaluationPrompt(code, missionType, missionId);
            String geminiResponse = callGeminiApi(prompt);
            return parseGeminiResponse(geminiResponse);
            
        } catch (Exception e) {
            log.error("Error during basic evaluation for missionId: {}", missionId, e);
            return createFallbackResult("AI 평가 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 기본 평가를 위한 프롬프트 생성
     */
    private String buildBasicEvaluationPrompt(String code, String missionType, String missionId) {
        return """
            당신은 경험이 풍부한 DevOps 전문가입니다. 다음 코드를 분석하고 한국어로 상세한 피드백을 제공해주세요.
            
            미션 유형: %s
            미션 ID: %s
            
            평가할 코드:
            %s
            
            다음 JSON 형식으로 평가 결과를 제공해주세요 (모든 텍스트는 한국어로 작성):
            {
                "total_score": 85,
                "correctness_score": 28,
                "efficiency_score": 28,
                "quality_score": 29,
                "security_score": 85,
                "style_score": 85,
                "best_practice_score": 85,
                "reliability_score": 85,
                "overall_feedback": "전체적인 평가와 개선 권장사항을 한국어로 상세히 설명",
                "correctness_feedback": "코드의 정확성 분석을 한국어로 설명",
                "efficiency_feedback": "성능과 효율성 분석을 한국어로 설명", 
                "quality_feedback": "코드 품질과 모범 사례 분석을 한국어로 설명",
                "security_feedback": "보안 관련 분석을 한국어로 설명",
                "style_feedback": "코드 스타일과 가독성 분석을 한국어로 설명",
                "strengths": ["강점 1", "강점 2", "강점 3"],
                "improvements": ["개선점 1", "개선점 2", "개선점 3"],
                "next_steps": ["다음 단계 추천 1", "다음 단계 추천 2"]
            }
            
            점수 기준:
            - total_score: 0-100점 (전체 점수)
            - correctness_score: 0-100점 (코드 정확성)
            - efficiency_score: 0-100점 (성능 효율성)
            - quality_score: 0-100점 (코드 품질)
            - security_score: 0-100점 (보안)
            - style_score: 0-100점 (스타일)
            - best_practice_score: 0-100점 (모범 사례)
            - reliability_score: 0-100점 (안정성)
            
            모든 피드백은 건설적이고 교육적이어야 하며, 학습자의 노력을 인정하면서도 구체적인 개선 방향을 제시해주세요.
            """.formatted(missionType, missionId, code);
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
                                                MissionCompletedEvent.SimpleStatistics statistics, String s3Data,
                                                EvaluationDataResponse missionData) {
        
        StringBuilder systemPrompt = buildSystemPrompt();
        StringBuilder userPrompt = new StringBuilder();
        
        appendMissionInfo(userPrompt, missionId, missionObjective, checklist);
        appendDataAccess(userPrompt, s3StorageUrl, s3PreSignedUrl, s3Data);
        appendRealExecutionData(userPrompt, missionData);
        appendOptionalAggregates(userPrompt, statistics);
        appendEvaluationRubric(userPrompt);
        
        return systemPrompt.toString() + "\n\nUser:\n" + userPrompt.toString();
    }
    
    private StringBuilder buildSystemPrompt() {
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
        return systemPrompt;
    }
    
    private void appendMissionInfo(StringBuilder userPrompt, String missionId, String missionObjective, List<String> checklist) {
        userPrompt.append("[Mission Info]\n");
        userPrompt.append(String.format("- missionAttemptId: %s%n", missionId));
        
        if (missionObjective != null && !missionObjective.trim().isEmpty()) {
            userPrompt.append(String.format("- goal: %s%n", missionObjective));
        }
        
        if (checklist != null && !checklist.isEmpty()) {
            userPrompt.append("- mission_objectives_checklist:\n");
            for (int i = 0; i < checklist.size(); i++) {
                userPrompt.append(String.format("  %d. %s%n", i + 1, checklist.get(i)));
            }
            userPrompt.append("  # 각 목표의 달성률을 0-100% 범위로 개별 평가 필요\n");
        }
    }
    
    private void appendDataAccess(StringBuilder userPrompt, String s3StorageUrl, String s3PreSignedUrl, String s3Data) {
        userPrompt.append("\n[Data Access]\n");
        if (s3StorageUrl != null && !s3StorageUrl.trim().isEmpty()) {
            userPrompt.append(String.format("- s3_address: %s%n", s3StorageUrl));
        }
        
        if (s3PreSignedUrl != null && !s3PreSignedUrl.trim().isEmpty()) {
            userPrompt.append("- presigned_urls:\n");
            userPrompt.append(String.format("  - command_log: %s%n", s3PreSignedUrl));
            userPrompt.append("# 모델은 위 URL을 통해 원본을 직접 읽어야 합니다.\n");
            userPrompt.append("# Pre-Signed URL은 5분 내 만료됩니다.\n");
            
            if (s3Data != null && !s3Data.trim().isEmpty()) {
                appendExecutionLogSection(userPrompt, s3Data);
                appendAnalysisRequirements(userPrompt);
            }
        }
    }
    
    private void appendExecutionLogSection(StringBuilder userPrompt, String s3Data) {
        userPrompt.append("\n[EXECUTION LOG AND METRICS]\n");
        userPrompt.append("```json\n");
        userPrompt.append(s3Data);
        userPrompt.append("\n```\n");
    }
    
    private void appendAnalysisRequirements(StringBuilder userPrompt) {
        userPrompt.append("\n[COMPREHENSIVE ANALYSIS REQUIREMENTS]\n");
        userPrompt.append("다음 모든 항목들을 상세히 분석하고 평가하세요:\n\n");
        
        appendCommandAnalysis(userPrompt);
        appendResourceAnalysis(userPrompt);
        appendOutputAnalysis(userPrompt);
        appendWorkspaceAnalysis(userPrompt);
        appendSecurityAnalysis(userPrompt);
        appendPerformanceAnalysis(userPrompt);
        
        userPrompt.append("CRITICAL: 실제 로그 데이터에 기반한 구체적이고 상세한 평가를 제공하세요.\n");
        userPrompt.append("각 명령어, 에러, 리소스 사용량에 대해 구체적인 수치와 함께 평가해야 합니다.\n");
    }
    
    private void appendCommandAnalysis(StringBuilder userPrompt) {
        userPrompt.append("=== 1. COMMAND EXECUTION ANALYSIS ===\n");
        userPrompt.append("✓ 실행된 모든 명령어 목록화 및 분석\n");
        userPrompt.append("  - 각 명령어의 목적과 의도 파악\n");
        userPrompt.append("  - 명령어 문법의 정확성 (옵션, 파라미터 사용)\n");
        userPrompt.append("  - 실행 순서의 논리적 타당성\n");
        userPrompt.append("  - 불필요하거나 중복된 명령어 식별\n");
        userPrompt.append("✓ 에러 처리 패턴 분석\n");
        userPrompt.append("  - 발생한 모든 에러 메시지 분류\n");
        userPrompt.append("  - 에러 복구 시도 및 해결 방법 평가\n");
        userPrompt.append("  - 재시도 패턴과 문제 해결 접근법\n\n");
    }
    
    private void appendResourceAnalysis(StringBuilder userPrompt) {
        userPrompt.append("=== 2. RESOURCE UTILIZATION ANALYSIS ===\n");
        userPrompt.append("✓ CPU 사용 패턴\n");
        userPrompt.append("  - 평균/최대 CPU 사용률 평가\n");
        userPrompt.append("  - CPU 스파이크 원인 분석\n");
        userPrompt.append("  - CPU 최적화 가능성 검토\n");
        userPrompt.append("✓ 메모리 사용 패턴\n");
        userPrompt.append("  - 메모리 사용량 추이 분석\n");
        userPrompt.append("  - 메모리 누수 가능성 검토\n");
        userPrompt.append("  - 메모리 효율성 평가\n");
        userPrompt.append("✓ 네트워크 I/O 분석\n");
        userPrompt.append("  - 네트워크 전송량 적절성\n");
        userPrompt.append("  - 불필요한 네트워크 호출 여부\n\n");
    }
    
    private void appendOutputAnalysis(StringBuilder userPrompt) {
        userPrompt.append("=== 3. OUTPUT ANALYSIS ===\n");
        userPrompt.append("✓ 명령어 출력 결과 검증\n");
        userPrompt.append("  - 예상된 출력과의 일치 여부\n");
        userPrompt.append("  - 경고 메시지 분석\n");
        userPrompt.append("  - 성공 메시지 확인\n");
        userPrompt.append("✓ 최종 상태 검증\n");
        userPrompt.append("  - Pod/Container 상태 확인\n");
        userPrompt.append("  - Service 접근 가능성\n");
        userPrompt.append("  - 데이터 일관성 검증\n\n");
    }
    
    private void appendWorkspaceAnalysis(StringBuilder userPrompt) {
        userPrompt.append("=== 4. WORKSPACE ANALYSIS ===\n");
        userPrompt.append("✓ 생성/수정된 파일 분석\n");
        userPrompt.append("  - YAML/JSON 파일 구조 검증\n");
        userPrompt.append("  - 설정 파일의 적절성\n");
        userPrompt.append("  - 파일 명명 규칙 준수\n");
        userPrompt.append("✓ 디렉토리 구조 평가\n");
        userPrompt.append("  - 프로젝트 구조의 체계성\n");
        userPrompt.append("  - 파일 조직화 수준\n\n");
    }
    
    private void appendSecurityAnalysis(StringBuilder userPrompt) {
        userPrompt.append("=== 5. SECURITY & BEST PRACTICES ===\n");
        userPrompt.append("✓ 보안 검사\n");
        userPrompt.append("  - 하드코딩된 시크릿/패스워드 검사\n");
        userPrompt.append("  - 권한 설정 적절성\n");
        userPrompt.append("  - 네트워크 정책 검토\n");
        userPrompt.append("✓ 모범 사례 준수\n");
        userPrompt.append("  - 라벨링 규칙 준수\n");
        userPrompt.append("  - 리소스 제한 설정\n");
        userPrompt.append("  - Health check 구성\n");
        userPrompt.append("  - 롤백 가능성\n\n");
    }
    
    private void appendPerformanceAnalysis(StringBuilder userPrompt) {
        userPrompt.append("=== 6. PERFORMANCE & EFFICIENCY ===\n");
        userPrompt.append("✓ 전체 실행 시간 평가\n");
        userPrompt.append("✓ 병목 구간 식별\n");
        userPrompt.append("✓ 최적화 가능 영역\n");
        userPrompt.append("✓ 확장성 고려사항\n\n");
    }
    
    private void appendRealExecutionData(StringBuilder userPrompt, EvaluationDataResponse missionData) {
        if (missionData != null) {
            userPrompt.append("\n[REAL EXECUTION DATA]\n");
            
            appendExecutionStatistics(userPrompt, missionData);
            appendCommandHistory(userPrompt, missionData);
            appendFailedCommands(userPrompt, missionData);
            appendResourceUsage(userPrompt, missionData);
            appendWorkspaceFiles(userPrompt, missionData);
            
            userPrompt.append("IMPORTANT: 위의 실제 실행 데이터를 기반으로 구체적이고 객관적인 평가를 수행하세요.\n");
            userPrompt.append("각 명령어의 성공/실패, 실행 시간, 리소스 사용량 등을 종합적으로 분석하여 점수를 부여하세요.\n\n");
        }
    }
    
    private void appendExecutionStatistics(StringBuilder userPrompt, EvaluationDataResponse missionData) {
        if (missionData.getStatistics() != null) {
            var stats = missionData.getStatistics();
            userPrompt.append("## Execution Statistics:\n");
            userPrompt.append(String.format("- Total Commands: %d\n", stats.getTotalCommands()));
            userPrompt.append(String.format("- Successful Commands: %d\n", stats.getSuccessfulCommands()));
            userPrompt.append(String.format("- Failed Commands: %d\n", stats.getFailedCommands()));
            userPrompt.append(String.format("- Success Rate: %.2f%%\n", stats.getSuccessRate()));
            userPrompt.append(String.format("- Total Execution Time: %d ms\n\n", stats.getTotalExecutionTimeMs()));
        }
    }
    
    private void appendCommandHistory(StringBuilder userPrompt, EvaluationDataResponse missionData) {
        if (missionData.getCommandHistory() != null && !missionData.getCommandHistory().isEmpty()) {
            userPrompt.append("## Command Execution History:\n");
            userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
            
            int count = 0;
            for (var cmd : missionData.getCommandHistory()) {
                count++;
                userPrompt.append(String.format("[%d] %s\n", count, cmd.getExecutedAt()));
                userPrompt.append(String.format(EvaluationConstants.COMMAND_FORMAT, cmd.getCommand()));
                userPrompt.append(String.format("Working Dir: %s\n", cmd.getWorkingDirectory()));
                userPrompt.append(String.format(EvaluationConstants.EXIT_CODE_FORMAT, cmd.getExitCode() != null ? cmd.getExitCode() : -1));
                userPrompt.append(String.format("Duration: %d ms\n", cmd.getDurationMs() != null ? cmd.getDurationMs() : 0));
                
                if (cmd.getOutput() != null && !cmd.getOutput().trim().isEmpty()) {
                    String output = cmd.getOutput().length() > 200 ? 
                        cmd.getOutput().substring(0, 200) + "..." : cmd.getOutput();
                    userPrompt.append(String.format("Output: %s\n", output));
                }
                userPrompt.append("---\n");
                
                if (count >= 20) {
                    userPrompt.append(String.format("... (%d more commands)\n", missionData.getCommandHistory().size() - 20));
                    break;
                }
            }
            userPrompt.append("```\n\n");
        }
    }
    
    private void appendFailedCommands(StringBuilder userPrompt, EvaluationDataResponse missionData) {
        if (missionData.getFailedCommands() != null && !missionData.getFailedCommands().isEmpty()) {
            userPrompt.append("## Failed Commands Analysis:\n");
            userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
            
            for (var failedCmd : missionData.getFailedCommands()) {
                userPrompt.append(String.format(EvaluationConstants.COMMAND_FORMAT, failedCmd.getCommand()));
                userPrompt.append(String.format(EvaluationConstants.EXIT_CODE_FORMAT, failedCmd.getExitCode()));
                userPrompt.append(String.format("Error Output: %s\n", 
                    failedCmd.getOutput() != null ? failedCmd.getOutput().substring(0, Math.min(150, failedCmd.getOutput().length())) : "None"));
                userPrompt.append("---\n");
            }
            userPrompt.append("```\n\n");
        }
    }
    
    private void appendResourceUsage(StringBuilder userPrompt, EvaluationDataResponse missionData) {
        if (missionData.getResourceUsage() != null) {
            var resource = missionData.getResourceUsage();
            userPrompt.append("## Resource Usage:\n");
            userPrompt.append(String.format("- Average CPU: %.2f%%\n", resource.getAverageCpuUsage() != null ? resource.getAverageCpuUsage() : 0.0));
            userPrompt.append(String.format("- Max CPU: %.2f%%\n", resource.getMaxCpuUsage() != null ? resource.getMaxCpuUsage() : 0.0));
            userPrompt.append(String.format("- Average Memory: %.2f MB\n", resource.getAverageMemoryUsage() != null ? resource.getAverageMemoryUsage() : 0.0));
            userPrompt.append(String.format("- Max Memory: %.2f MB\n\n", resource.getMaxMemoryUsage() != null ? resource.getMaxMemoryUsage() : 0.0));
        }
    }
    
    private void appendWorkspaceFiles(StringBuilder userPrompt, EvaluationDataResponse missionData) {
        if (missionData.getWorkspaceFiles() != null && !missionData.getWorkspaceFiles().isEmpty()) {
            userPrompt.append("## Important Workspace Files:\n");
            for (Map.Entry<String, String> file : missionData.getWorkspaceFiles().entrySet()) {
                userPrompt.append(String.format("### %s:\n", file.getKey()));
                userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
                userPrompt.append(file.getValue());
                userPrompt.append("\n```\n\n");
            }
        }
    }
    
    private void appendOptionalAggregates(StringBuilder userPrompt, MissionCompletedEvent.SimpleStatistics statistics) {
        if (statistics != null && hasStatisticsData(statistics)) {
            userPrompt.append("\n[Optional Aggregates]\n");
            
            appendCommandStatistics(userPrompt, statistics);
            appendResourceStatistics(userPrompt, statistics);
            appendTimelineStatistics(userPrompt, statistics);
        }
    }
    
    private void appendCommandStatistics(StringBuilder userPrompt, MissionCompletedEvent.SimpleStatistics statistics) {
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
    }
    
    private void appendResourceStatistics(StringBuilder userPrompt, MissionCompletedEvent.SimpleStatistics statistics) {
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
    }
    
    private void appendTimelineStatistics(StringBuilder userPrompt, MissionCompletedEvent.SimpleStatistics statistics) {
        if (statistics.getTotalExecutionTime() != null) {
            userPrompt.append("- timeline:\n");
            userPrompt.append(String.format("  duration_sec: %.1f%n", statistics.getTotalExecutionTime() / 1000.0));
        }
    }
    
    private void appendEvaluationRubric(StringBuilder userPrompt) {
        userPrompt.append("""
            
            [PRODUCTION EVALUATION RUBRIC - MANDATORY SCORING]
            실제 운영 환경 로그를 바탕으로 다음 기준에 따라 반드시 점수를 부여하세요:
            
            - correctness (1~5): 실행된 명령어들의 성공/실패 비율과 목표 달성도
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
                "key_commands": ["평가에 중요한 핵심 명령어들"],
                "performance_data": "실제 측정된 자원 사용 패턴",
                "error_patterns": ["발견된 주요 오류 패턴들"]
              }
            }
            
            CRITICAL REQUIREMENTS:
            - 점수는 반드시 1점 이상이어야 함 (0점 금지)
            - "모의/가짜/테스트" 등의 단어 사용 절대 금지
            - 실제 로그 데이터의 기술적 내용만 분석
            - JSON 형식 외 추가 텍스트 금지
            """);
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
        return buildEnhancedEvaluationPrompt(code, missionType, missionId, null, null, null, null, null, null, null);
    }
    
    /**
     * 실제 실행 데이터를 포함한 향상된 평가 프롬프트 생성
     */
    private String buildEnhancedEvaluationPromptWithRealData(MissionCompletedEvent event, String s3Data) {
        StringBuilder systemPrompt = buildRealDataSystemPrompt();
        StringBuilder userPrompt = new StringBuilder();
        
        appendMissionInformation(userPrompt, event);
        appendSubmittedCode(userPrompt, event);
        appendRealExecutionDataForEvent(userPrompt, event);
        appendS3FallbackData(userPrompt, s3Data);
        appendLearningObjectiveEvaluationRequirements(userPrompt);
        
        return systemPrompt.toString() + "\n\n" + userPrompt.toString();
    }
    
    private StringBuilder buildRealDataSystemPrompt() {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("""
            당신은 경험이 풍부한 DevOps 전문가입니다. 학습자의 실습 결과를 평가하고 건설적인 피드백을 제공하는 것이 목표입니다.
            
            CRITICAL INSTRUCTIONS:
            1. 제공된 실제 실행 데이터를 바탕으로 정확한 평가를 수행하세요.
            2. 모든 명령어 실행 결과와 리소스 사용량을 고려하세요.
            3. 점수는 실제 성과에 바탕해 객관적으로 부여하세요.
            4. 건설적이고 구체적인 개선사항을 제시하세요.
            
            EVALUATION CRITERIA (1-5 점수):
            - correctness (1~5): 명령어 성공/실패 비율과 목표 달성도
            - efficiency (1~5): 리소스 사용 효율성과 실행 시간
            - quality (1~5): 작업 품질과 DevOps 모범사례 준수
            """);
        return systemPrompt;
    }
    
    private void appendMissionInformation(StringBuilder userPrompt, MissionCompletedEvent event) {
        userPrompt.append("[MISSION INFORMATION]\n");
        userPrompt.append(String.format("Mission: %s\n", event.getMissionTitle()));
        userPrompt.append(String.format("Type: %s\n", event.getMissionType()));
        
        if (event.getEvaluationCriteria() != null && !event.getEvaluationCriteria().trim().isEmpty()) {
            userPrompt.append("\n[학습 목표 및 평가 기준]\n");
            userPrompt.append("이 미션의 학습 목표를 반드시 평가해주세요:\n");
            userPrompt.append(parseEvaluationCriteria(event.getEvaluationCriteria()));
            userPrompt.append("\n");
        }
        
        if (event.getMissionGuide() != null && !event.getMissionGuide().trim().isEmpty()) {
            userPrompt.append("[미션 가이드]\n");
            String guide = event.getMissionGuide();
            if (guide.length() > 500) {
                guide = guide.substring(0, 500) + "...";
            }
            userPrompt.append(guide);
            userPrompt.append("\n\n");
        }
    }
    
    private void appendSubmittedCode(StringBuilder userPrompt, MissionCompletedEvent event) {
        userPrompt.append("\n[SUBMITTED CODE]\n");
        userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
        userPrompt.append(event.getCode() != null ? event.getCode() : "No code submitted");
        userPrompt.append("\n```\n\n");
    }
    
    private void appendRealExecutionDataForEvent(StringBuilder userPrompt, MissionCompletedEvent event) {
        MissionCompletedEvent.RealExecutionData realData = event.getRealExecutionData();
        if (realData != null) {
            userPrompt.append("[REAL EXECUTION DATA]\n");
            
            appendRealExecutionStatistics(userPrompt, realData);
            appendRealCommandHistory(userPrompt, realData);
            appendRealFailedCommands(userPrompt, realData);
            appendRealResourceUsage(userPrompt, realData);
            appendRealWorkspaceFiles(userPrompt, realData);
            
            userPrompt.append("IMPORTANT: 위의 실제 실행 데이터를 기반으로 구체적이고 객관적인 평가를 수행하세요.\n");
            userPrompt.append("각 명령어의 성공/실패, 실행 시간, 리소스 사용량 등을 종합적으로 분석하여 점수를 부여하세요.\n");
            userPrompt.append("특히 학습 목표별로 실제 명령어 실행 결과를 매칭하여 달성도를 평가하세요.\n\n");
        }
    }
    
    private void appendRealExecutionStatistics(StringBuilder userPrompt, MissionCompletedEvent.RealExecutionData realData) {
        if (realData.getStatistics() != null) {
            var stats = realData.getStatistics();
            userPrompt.append("## Execution Statistics:\n");
            userPrompt.append(String.format("- Total Commands: %d\n", stats.getTotalCommands()));
            userPrompt.append(String.format("- Successful Commands: %d\n", stats.getSuccessfulCommands()));
            userPrompt.append(String.format("- Failed Commands: %d\n", stats.getFailedCommands()));
            userPrompt.append(String.format("- Success Rate: %.2f%%\n", stats.getSuccessRate()));
            userPrompt.append(String.format("- Total Execution Time: %d ms\n\n", stats.getTotalExecutionTimeMs()));
        }
    }
    
    private void appendRealCommandHistory(StringBuilder userPrompt, MissionCompletedEvent.RealExecutionData realData) {
        if (realData.getCommandHistory() != null && !realData.getCommandHistory().isEmpty()) {
            userPrompt.append("## Command Execution History:\n");
            userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
            
            int count = 0;
            for (var cmd : realData.getCommandHistory()) {
                count++;
                userPrompt.append(String.format("[%d] %s\n", count, cmd.getExecutedAt()));
                userPrompt.append(String.format(EvaluationConstants.COMMAND_FORMAT, cmd.getCommand()));
                userPrompt.append(String.format("Working Dir: %s\n", cmd.getWorkingDirectory()));
                userPrompt.append(String.format(EvaluationConstants.EXIT_CODE_FORMAT, cmd.getExitCode() != null ? cmd.getExitCode() : -1));
                userPrompt.append(String.format("Duration: %d ms\n", cmd.getDurationMs() != null ? cmd.getDurationMs() : 0));
                
                if (cmd.getOutput() != null && !cmd.getOutput().trim().isEmpty()) {
                    String output = cmd.getOutput().length() > 200 ? 
                        cmd.getOutput().substring(0, 200) + "..." : cmd.getOutput();
                    userPrompt.append(String.format("Output: %s\n", output));
                }
                userPrompt.append("---\n");
                
                if (count >= 20) {
                    userPrompt.append(String.format("... (%d more commands)\n", realData.getCommandHistory().size() - 20));
                    break;
                }
            }
            userPrompt.append("```\n\n");
        }
    }
    
    private void appendRealFailedCommands(StringBuilder userPrompt, MissionCompletedEvent.RealExecutionData realData) {
        if (realData.getFailedCommands() != null && !realData.getFailedCommands().isEmpty()) {
            userPrompt.append("## Failed Commands Analysis:\n");
            userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
            
            for (var failedCmd : realData.getFailedCommands()) {
                userPrompt.append(String.format(EvaluationConstants.COMMAND_FORMAT, failedCmd.getCommand()));
                userPrompt.append(String.format(EvaluationConstants.EXIT_CODE_FORMAT, failedCmd.getExitCode()));
                userPrompt.append(String.format("Error Output: %s\n", 
                    failedCmd.getOutput() != null ? failedCmd.getOutput().substring(0, Math.min(150, failedCmd.getOutput().length())) : "None"));
                userPrompt.append("---\n");
            }
            userPrompt.append("```\n\n");
        }
    }
    
    private void appendRealResourceUsage(StringBuilder userPrompt, MissionCompletedEvent.RealExecutionData realData) {
        if (realData.getResourceUsage() != null) {
            var resource = realData.getResourceUsage();
            userPrompt.append("## Resource Usage:\n");
            userPrompt.append(String.format("- Average CPU: %.2f%%\n", resource.getAverageCpuUsage() != null ? resource.getAverageCpuUsage() : 0.0));
            userPrompt.append(String.format("- Max CPU: %.2f%%\n", resource.getMaxCpuUsage() != null ? resource.getMaxCpuUsage() : 0.0));
            userPrompt.append(String.format("- Average Memory: %.2f MB\n", resource.getAverageMemoryUsage() != null ? resource.getAverageMemoryUsage() : 0.0));
            userPrompt.append(String.format("- Max Memory: %.2f MB\n\n", resource.getMaxMemoryUsage() != null ? resource.getMaxMemoryUsage() : 0.0));
        }
    }
    
    private void appendRealWorkspaceFiles(StringBuilder userPrompt, MissionCompletedEvent.RealExecutionData realData) {
        if (realData.getWorkspaceFiles() != null && !realData.getWorkspaceFiles().isEmpty()) {
            userPrompt.append("## Important Workspace Files:\n");
            for (var fileEntry : realData.getWorkspaceFiles().entrySet()) {
                userPrompt.append(String.format("### %s:\n", fileEntry.getKey()));
                userPrompt.append(EvaluationConstants.CODE_SNIPPET_MARKDOWN);
                userPrompt.append(fileEntry.getValue());
                userPrompt.append("\n```\n\n");
            }
        }
    }
    
    private void appendS3FallbackData(StringBuilder userPrompt, String s3Data) {
        if (s3Data != null && !s3Data.trim().isEmpty()) {
            userPrompt.append("[ADDITIONAL S3 DATA (FALLBACK)]\n");
            userPrompt.append("```json\n");
            userPrompt.append(s3Data);
            userPrompt.append("\n```\n\n");
        }
    }
    
    private void appendLearningObjectiveEvaluationRequirements(StringBuilder userPrompt) {
        userPrompt.append("""
            [학습 목표 평가 필수]
            위에 제시된 학습 목표(evaluationCriteria)를 기준으로 각 목표별 달성도를 평가하세요.
            실제 실행된 명령어와 결과물을 분석하여 각 학습 목표가 얼마나 달성되었는지 판단하세요.
            
            [EVALUATION REQUIREMENTS]
            다음 형식으로 JSON 응답을 제공하세요:
            {
              "overall_score": 전체 점수 (15점 만점),
              "learning_objectives_evaluation": [
                {
                  "objective": "학습 목표 명칭",
                  "achievement_rate": 0-100,
                  "evidence": "달성 근거 (실제 명령어 또는 결과물)",
                  "feedback": "구체적인 피드백"
                }
              ],
              "overall_objective_achievement": 0-100,
              "scores": {
                "correctness": {
                  "score": 1-5,
                  "feedback": "상세 피드백"
                },
                "efficiency": {
                  "score": 1-5,
                  "feedback": "효율성 피드백"
                },
                "quality": {
                  "score": 1-5,
                  "feedback": "품질 피드백"
                }
              },
              "feedback": "종합 피드백",
              "detailed_analysis": "상세 분석"
            }
            """);
        
    }
    
    private String callGeminiApi(String prompt) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.info("Calling Gemini API - attempt {}/{}", attempt, maxRetryAttempts);
                String response = attemptGeminiApiCall(prompt);
                if (response != null) {
                    return response;
                }
                
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("Gemini API timeout/connection error on attempt {}/{}: {}", attempt, maxRetryAttempts, e.getMessage());
                
            } catch (HttpServerErrorException e) {
                lastException = e;
                log.warn("Gemini API server error on attempt {}/{}: {} - {}", attempt, maxRetryAttempts, e.getStatusCode(), e.getMessage());
                
            } catch (HttpClientErrorException e) {
                log.error("Gemini API client error (not retrying): {} - {}", e.getStatusCode(), e.getMessage());
                throw new GeminiApiException("Gemini API client error: " + e.getStatusCode() + " - " + e.getMessage(), e);
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini API error on attempt {}/{}: {}", attempt, maxRetryAttempts, e.getMessage());
            }
            
            if (attempt < maxRetryAttempts) {
                waitBeforeRetry(attempt);
            }
        }
        
        log.error("All Gemini API attempts failed after {} tries", maxRetryAttempts);
        throw new GeminiApiException("Failed to call Gemini API after " + maxRetryAttempts + " attempts", lastException);
    }
    
    private String attemptGeminiApiCall(String prompt) {
        HttpEntity<Map<String, Object>> request = buildGeminiRequest(prompt);
        String url = geminiApiUrl + "?key=" + geminiApiKey;
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            log.info("Gemini API call successful");
            String responseBody = response.getBody();
            log.debug("===== GEMINI API 원본 응답 시작 =====");
            log.debug("Response body: {}", responseBody);
            log.debug("===== GEMINI API 원본 응답 끝 =====");
            return responseBody;
        } else {
            throw new GeminiApiException("Gemini API call failed with status: " + response.getStatusCode());
        }
    }
    
    private HttpEntity<Map<String, Object>> buildGeminiRequest(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> requestBody = createGeminiRequestBody(prompt);
        return new HttpEntity<>(requestBody, headers);
    }
    
    private Map<String, Object> createGeminiRequestBody(String prompt) {
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
        
        return requestBody;
    }
    
    private void waitBeforeRetry(int attempt) {
        try {
            long delayMs = retryDelaySeconds * 1000L * attempt;
            log.info("Waiting {}ms before retry...", delayMs);
            
            try {
                java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            Thread.sleep(delayMs);
                            return null;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new GeminiApiException("Async delay interrupted", e);
                        }
                    })
                    .get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new GeminiApiException("Async delay failed", e);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new GeminiApiException("Retry interrupted", ie);
        }
    }
    
    private EvaluationResultDTO parseGeminiResponse(String geminiResponse) {
        if (geminiResponse == null || geminiResponse.trim().isEmpty()) {
            log.error("Gemini response is null or empty");
            return createFallbackResult("Empty API response");
        }
        
        try {
            log.debug("===== GEMINI 응답 파싱 시작 =====");
            log.debug("Full Gemini response: {}", geminiResponse);
            log.debug("===== GEMINI 응답 파싱 시작 끝 =====");
            
            JsonNode responseJson = objectMapper.readTree(geminiResponse);
            String textContent = extractTextContentFromResponse(responseJson);
            if (textContent == null) {
                return createFallbackResult("Failed to extract text content");
            }
            
            String jsonContent = extractAndValidateJsonContent(textContent);
            if (jsonContent == null) {
                return createFallbackResult("Failed to extract JSON content");
            }
            
            JsonNode parsedJson = parseJsonContent(jsonContent);
            if (parsedJson == null) {
                return createFallbackResult("Failed to parse JSON content");
            }
            
            return parseEvaluationResult(parsedJson, jsonContent);
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("JSON processing error parsing Gemini response: {}", geminiResponse.substring(0, Math.min(500, geminiResponse.length())), e);
            return createFallbackResult("JSON parsing error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error parsing Gemini response: {}", geminiResponse.substring(0, Math.min(500, geminiResponse.length())), e);
            return createFallbackResult("Parsing error: " + e.getMessage());
        }
    }
    
    private String extractTextContentFromResponse(JsonNode responseJson) {
        if (!responseJson.has("candidates")) {
            log.error("Gemini response missing 'candidates' field");
            return null;
        }
        
        JsonNode candidates = responseJson.path("candidates");
        if (!candidates.isArray() || candidates.size() == 0) {
            log.error("Gemini response has no candidates");
            return null;
        }
        
        JsonNode firstCandidate = candidates.get(0);
        if (!firstCandidate.has("content")) {
            log.error("First candidate missing 'content' field");
            return null;
        }
        
        JsonNode content = firstCandidate.path("content");
        if (!content.has("parts")) {
            log.error("Content missing 'parts' field");
            return null;
        }
        
        JsonNode parts = content.path("parts");
        if (!parts.isArray() || parts.size() == 0) {
            log.error("Content has no parts");
            return null;
        }
        
        String textContent = parts.get(0).path("text").asText();
        if (textContent.isEmpty()) {
            log.error("Text content is empty");
            return null;
        }
        
        return textContent;
    }
    
    private String extractAndValidateJsonContent(String textContent) {
        log.debug("===== 텍스트에서 JSON 추출 시작 =====");
        log.debug("Original text content: {}", textContent);
        String jsonContent = extractJsonFromResponse(textContent);
        log.debug("Extracted JSON content: {}", jsonContent);
        log.debug("===== 텍스트에서 JSON 추출 완료 =====");
        
        if (jsonContent.isEmpty()) {
            log.error("No JSON content found in response");
            return null;
        }
        
        return jsonContent;
    }
    
    private JsonNode parseJsonContent(String jsonContent) {
        try {
            return objectMapper.readTree(jsonContent);
        } catch (com.fasterxml.jackson.core.JsonParseException jsonEx) {
            log.error("JSON parse error in extracted content: {}", jsonContent.substring(0, Math.min(200, jsonContent.length())), jsonEx);
            return null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
            log.error("JSON processing error in extracted content: {}", jsonContent.substring(0, Math.min(200, jsonContent.length())), jsonEx);
            return null;
        } catch (Exception jsonEx) {
            log.error("Unexpected error parsing JSON content: {}", jsonContent.substring(0, Math.min(200, jsonContent.length())), jsonEx);
            return null;
        }
    }
    
    private EvaluationResultDTO parseEvaluationResult(JsonNode parsedJson, String jsonContent) {
        log.debug("===== JSON 형식 확인 =====");
        log.debug("Has total_score field: {}", parsedJson.has("total_score"));
        log.debug("Has best_practice_score field: {}", parsedJson.has("best_practice_score"));
        log.debug("Has reliability_score field: {}", parsedJson.has("reliability_score"));
        log.debug("JSON field names: {}", parsedJson.fieldNames());
        
        try {
            if (parsedJson.has("total_score")) {
                log.info("✓ Parsing DevOps evaluation response format");
                log.debug("DevOps JSON content: {}", jsonContent);
                return parseDevOpsEvaluationResponse(parsedJson);
            } else {
                log.info("✗ Parsing legacy evaluation response format");
                log.debug("Legacy JSON content: {}", jsonContent);
                return objectMapper.readValue(jsonContent, EvaluationResultDTO.class);
            }
        } catch (Exception e) {
            log.error("Error parsing evaluation result: {}", e.getMessage(), e);
            return createFallbackResult("Evaluation result parsing error: " + e.getMessage());
        }
    }
    
    /**
     * 새로운 DevOps 채점관 형식의 응답을 기존 EvaluationResultDTO로 변환
     * 확장된 JSON 구조를 파싱하여 핵심 명령어 분석, 체크리스트 평가, 리소스 메트릭을 포함
     */
    private EvaluationResultDTO parseDevOpsEvaluationResponse(JsonNode devOpsResponse) {
        log.debug("===== DevOps 응답 파싱 시작 =====");
        EvaluationResultDTO result = new EvaluationResultDTO();
        
        setBasicScores(devOpsResponse, result);
        setSecurityRiskLevel(devOpsResponse, result);
        setEfficiencyGrade(devOpsResponse, result);
        setEvaluationObjects(devOpsResponse, result);
        setOverallFeedback(devOpsResponse, result);
        buildDetailedFeedback(devOpsResponse, result);
        
        logFinalResults(result);
        return result;
    }
    
    private void setBasicScores(JsonNode devOpsResponse, EvaluationResultDTO result) {
        int totalScore = devOpsResponse.path("total_score").asInt();
        log.debug("Total score from JSON: {}", totalScore);
        result.setOverallScore(totalScore);
        
        int bestPracticeScore = devOpsResponse.path("best_practice_score").asInt();
        int reliabilityScore = devOpsResponse.path("reliability_score").asInt();
        log.debug("Best practice score from JSON: {}", bestPracticeScore);
        log.debug("Reliability score from JSON: {}", reliabilityScore);
        result.setBestPracticeScore(bestPracticeScore);
        result.setReliabilityScore(reliabilityScore);
    }
    
    private void setSecurityRiskLevel(JsonNode devOpsResponse, EvaluationResultDTO result) {
        int securityScore = devOpsResponse.path("security_score").asInt();
        log.debug("Security score from JSON: {}", securityScore);
        
        String riskLevel;
        if (securityScore >= 90) {
            riskLevel = "Low";
        } else if (securityScore >= 70) {
            riskLevel = "Medium";
        } else {
            riskLevel = "High";
        }
        
        result.setSecurityRiskLevel(riskLevel);
        log.debug("Security risk level set to: {}", riskLevel);
    }
    
    private void setEfficiencyGrade(JsonNode devOpsResponse, EvaluationResultDTO result) {
        int efficiencyScore = devOpsResponse.path("efficiency_score").asInt();
        log.debug("Efficiency score from JSON: {}", efficiencyScore);
        
        String grade;
        if (efficiencyScore >= 90) {
            grade = "A";
        } else if (efficiencyScore >= 80) {
            grade = "B";
        } else if (efficiencyScore >= 70) {
            grade = "C";
        } else if (efficiencyScore >= 60) {
            grade = "D";
        } else {
            grade = "F";
        }
        
        result.setEfficiencyGrade(grade);
        log.debug("Efficiency grade set to: {}", grade);
    }
    
    private void setEvaluationObjects(JsonNode devOpsResponse, EvaluationResultDTO result) {
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        codeQuality.setScore(devOpsResponse.path("quality_score").asInt());
        codeQuality.setFeedback(devOpsResponse.path("quality_feedback").asText());
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        security.setScore(devOpsResponse.path("security_score").asInt());
        security.setFeedback(devOpsResponse.path("security_feedback").asText());
        security.setRiskLevel(result.getSecurityRiskLevel());
        security.setVulnerabilities("보안 취약점 분석 완료");
        security.setRecommendations("보안 모범 사례 준수 권장");
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        style.setScore(devOpsResponse.path("style_score").asInt());
        style.setFeedback(devOpsResponse.path("style_feedback").asText());
        style.setStyleIssues("코드 스타일 및 구조 분석 완료");
        style.setImprovements("가독성과 유지보수성 향상 권장");
        style.setCategory("DevOps Configuration");
        result.setStyle(style);
    }
    
    private void setOverallFeedback(JsonNode devOpsResponse, EvaluationResultDTO result) {
        String overallFeedback = devOpsResponse.path("overall_feedback").asText();
        result.setFeedback(overallFeedback);
    }
    
    private void buildDetailedFeedback(JsonNode devOpsResponse, EvaluationResultDTO result) {
        StringBuilder detailedFeedback = new StringBuilder();
        
        appendOverallFeedbackSection(devOpsResponse, detailedFeedback);
        appendDetailedEvaluationSections(devOpsResponse, result, detailedFeedback);
        appendStrengthsAndImprovements(devOpsResponse, detailedFeedback);
        
        result.setDetailedAnalysis(detailedFeedback.toString());
    }
    
    private void appendOverallFeedbackSection(JsonNode devOpsResponse, StringBuilder detailedFeedback) {
        detailedFeedback.append("=== 종합 평가 ===\n");
        detailedFeedback.append(devOpsResponse.path("overall_feedback").asText()).append("\n\n");
        detailedFeedback.append("=== 세부 평가 ===\n");
    }
    
    private void appendDetailedEvaluationSections(JsonNode devOpsResponse, EvaluationResultDTO result, StringBuilder detailedFeedback) {
        appendEvaluationSection(devOpsResponse, detailedFeedback, "correctness", "📋 정확성 평가");
        appendEvaluationSection(devOpsResponse, detailedFeedback, "efficiency", "⚡ 효율성 평가");
        
        String qualityFeedback = devOpsResponse.path("quality_feedback").asText();
        if (!qualityFeedback.isEmpty()) {
            detailedFeedback.append("🎯 품질 평가: ").append(result.getCodeQuality().getScore()).append("점\n");
            detailedFeedback.append(qualityFeedback).append("\n\n");
        }
        
        String securityFeedback = devOpsResponse.path("security_feedback").asText();
        if (!securityFeedback.isEmpty()) {
            detailedFeedback.append("🔒 보안 평가: ").append(result.getSecurity().getScore()).append("점\n");
            detailedFeedback.append(securityFeedback).append("\n\n");
        }
        
        String styleFeedback = devOpsResponse.path("style_feedback").asText();
        if (!styleFeedback.isEmpty()) {
            detailedFeedback.append("🎨 스타일 평가: ").append(result.getStyle().getScore()).append("점\n");
            detailedFeedback.append(styleFeedback).append("\n\n");
        }
    }
    
    private void appendEvaluationSection(JsonNode devOpsResponse, StringBuilder detailedFeedback, String sectionName, String sectionTitle) {
        String feedback = devOpsResponse.path(sectionName + "_feedback").asText();
        if (!feedback.isEmpty()) {
            detailedFeedback.append(sectionTitle).append(": ").append(devOpsResponse.path(sectionName + "_score").asInt()).append("점\n");
            detailedFeedback.append(feedback).append("\n\n");
        }
    }
    
    private void appendStrengthsAndImprovements(JsonNode devOpsResponse, StringBuilder detailedFeedback) {
        appendJsonArraySection(devOpsResponse, detailedFeedback, "strengths", "=== 강점 ===", "✅ ");
        appendJsonArraySection(devOpsResponse, detailedFeedback, "improvements", "=== 개선점 ===", "🔧 ");
        appendJsonArraySection(devOpsResponse, detailedFeedback, "next_steps", "=== 다음 단계 추천 ===", "🚀 ");
    }
    
    private void appendJsonArraySection(JsonNode devOpsResponse, StringBuilder detailedFeedback, String fieldName, String sectionTitle, String bulletPoint) {
        JsonNode arrayNode = devOpsResponse.path(fieldName);
        if (arrayNode.isArray() && arrayNode.size() > 0) {
            detailedFeedback.append(sectionTitle).append("\n");
            for (JsonNode item : arrayNode) {
                detailedFeedback.append(bulletPoint).append(item.asText()).append("\n");
            }
            detailedFeedback.append("\n");
        }
    }
    
    private void logFinalResults(EvaluationResultDTO result) {
        log.debug("===== DevOps 응답 파싱 완료 =====");
        log.debug("Final result - Overall Score: {}", result.getOverallScore());
        log.debug("Final result - Best Practice Score: {}", result.getBestPracticeScore());
        log.debug("Final result - Reliability Score: {}", result.getReliabilityScore());
        log.debug("Final result - Security Risk Level: {}", result.getSecurityRiskLevel());
        log.debug("Final result - Efficiency Grade: {}", result.getEfficiencyGrade());
        log.debug("===== DevOps 응답 파싱 완료 끝 =====");
    }
    
    private EvaluationResultDTO parseOldDevOpsEvaluationResponse(JsonNode devOpsResponse) {
        EvaluationResultDTO result = new EvaluationResultDTO();
        
        setBasicScoreAndFeedback(devOpsResponse, result);
        parseLearningObjectives(devOpsResponse, result);
        buildOldDevOpsDetailedAnalysis(devOpsResponse, result);
        setOldDevOpsEvaluationScores(devOpsResponse, result);
        
        return result;
    }
    
    private void setBasicScoreAndFeedback(JsonNode devOpsResponse, EvaluationResultDTO result) {
        int totalScore = devOpsResponse.path("total_score").asInt();
        int overallScore = (int) Math.round(totalScore * 100.0 / 15.0);
        result.setOverallScore(overallScore);
        result.setFeedback(devOpsResponse.path("feedback").asText());
    }
    
    private void parseLearningObjectives(JsonNode devOpsResponse, EvaluationResultDTO result) {
        JsonNode learningObjectivesEval = devOpsResponse.path("learning_objectives_evaluation");
        if (learningObjectivesEval.isArray() && learningObjectivesEval.size() > 0) {
            List<EvaluationResultDTO.LearningObjectiveResult> objectives = new ArrayList<>();
            Map<String, Integer> objectiveScores = new HashMap<>();
            Map<String, String> objectiveFeedback = new HashMap<>();
            
            for (JsonNode objNode : learningObjectivesEval) {
                EvaluationResultDTO.LearningObjectiveResult objResult = createLearningObjectiveResult(objNode);
                objectives.add(objResult);
                objectiveScores.put(objResult.getObjective(), objResult.getAchievementRate());
                objectiveFeedback.put(objResult.getObjective(), objResult.getFeedback());
            }
            
            result.setLearningObjectivesEvaluation(objectives);
            result.setLearningObjectiveScores(objectiveScores);
            result.setObjectiveFeedback(objectiveFeedback);
        }
        
        int overallObjectiveAchievement = devOpsResponse.path("overall_objective_achievement").asInt(0);
        result.setOverallObjectiveAchievement(overallObjectiveAchievement);
    }
    
    private EvaluationResultDTO.LearningObjectiveResult createLearningObjectiveResult(JsonNode objNode) {
        EvaluationResultDTO.LearningObjectiveResult objResult = new EvaluationResultDTO.LearningObjectiveResult();
        objResult.setObjective(objNode.path("objective").asText());
        objResult.setAchievementRate(objNode.path("achievement_rate").asInt());
        objResult.setEvidence(objNode.path("evidence").asText());
        objResult.setFeedback(objNode.path("feedback").asText());
        return objResult;
    }
    
    private void buildOldDevOpsDetailedAnalysis(JsonNode devOpsResponse, EvaluationResultDTO result) {
        StringBuilder detailedAnalysis = new StringBuilder();
        
        appendLearningObjectivesAnalysis(result, detailedAnalysis);
        appendCoreCommandsAnalysis(devOpsResponse, detailedAnalysis);
        appendChecklistEvaluationAnalysis(devOpsResponse, detailedAnalysis);
        appendResourceMetricsAnalysis(devOpsResponse, detailedAnalysis);
        appendImprovementSections(devOpsResponse, detailedAnalysis);
        appendEvidenceSection(devOpsResponse, detailedAnalysis);
        
        result.setDetailedAnalysis(detailedAnalysis.toString());
    }
    
    private void appendLearningObjectivesAnalysis(EvaluationResultDTO result, StringBuilder detailedAnalysis) {
        if (result.getLearningObjectivesEvaluation() != null && !result.getLearningObjectivesEvaluation().isEmpty()) {
            detailedAnalysis.append("=== 학습 목표 달성도 분석 ===\n");
            detailedAnalysis.append(String.format("전체 달성률: %d%%\n\n", result.getOverallObjectiveAchievement()));
            
            for (EvaluationResultDTO.LearningObjectiveResult obj : result.getLearningObjectivesEvaluation()) {
                detailedAnalysis.append(String.format("🎯 %s\n", obj.getObjective()));
                detailedAnalysis.append(String.format("  • 달성도: %d%%\n", obj.getAchievementRate()));
                detailedAnalysis.append(String.format("  • 근거: %s\n", obj.getEvidence()));
                detailedAnalysis.append(String.format("  • 피드백: %s\n\n", obj.getFeedback()));
            }
            detailedAnalysis.append("\n");
        }
    }
    
    private void appendCoreCommandsAnalysis(JsonNode devOpsResponse, StringBuilder detailedAnalysis) {
        JsonNode coreCommandsAnalysis = devOpsResponse.path("core_commands_analysis");
        if (!coreCommandsAnalysis.isMissingNode()) {
            detailedAnalysis.append("=== 핵심 명령어 사용 분석 ===\n");
            
            appendKeyCommandsUsed(coreCommandsAnalysis, detailedAnalysis);
            appendToolProficiency(coreCommandsAnalysis, detailedAnalysis);
            appendCommandEfficiencyAndSequence(coreCommandsAnalysis, detailedAnalysis);
            
            detailedAnalysis.append("\n");
        }
    }
    
    private void appendKeyCommandsUsed(JsonNode coreCommandsAnalysis, StringBuilder detailedAnalysis) {
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
    }
    
    private void appendToolProficiency(JsonNode coreCommandsAnalysis, StringBuilder detailedAnalysis) {
        appendToolScore(coreCommandsAnalysis, detailedAnalysis, "kubectl_usage", "kubectl 숙련도");
        appendToolScore(coreCommandsAnalysis, detailedAnalysis, "docker_usage", "docker 숙련도");
        appendToolScore(coreCommandsAnalysis, detailedAnalysis, "other_tools", "기타 도구 사용");
    }
    
    private void appendToolScore(JsonNode coreCommandsAnalysis, StringBuilder detailedAnalysis, String toolName, String toolDisplayName) {
        JsonNode toolUsage = coreCommandsAnalysis.path(toolName);
        if (!toolUsage.isMissingNode()) {
            detailedAnalysis.append(String.format("• %s: %d/5 - %s\n", 
                toolDisplayName, 
                toolUsage.path("score").asInt(), 
                toolUsage.path("details").asText()));
        }
    }
    
    private void appendCommandEfficiencyAndSequence(JsonNode coreCommandsAnalysis, StringBuilder detailedAnalysis) {
        String commandEfficiency = coreCommandsAnalysis.path("command_efficiency").asText();
        if (!commandEfficiency.isEmpty()) {
            detailedAnalysis.append("• 명령어 효율성: ").append(commandEfficiency).append("\n");
        }
        
        String commandSequence = coreCommandsAnalysis.path("command_sequence").asText();
        if (!commandSequence.isEmpty()) {
            detailedAnalysis.append("• 명령어 순서: ").append(commandSequence).append("\n");
        }
    }
    
    private void appendChecklistEvaluationAnalysis(JsonNode devOpsResponse, StringBuilder detailedAnalysis) {
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
    }
    
    private void appendResourceMetricsAnalysis(JsonNode devOpsResponse, StringBuilder detailedAnalysis) {
        JsonNode resourceMetrics = devOpsResponse.path("resource_metrics");
        if (!resourceMetrics.isMissingNode()) {
            detailedAnalysis.append("=== 시스템 리소스 사용 분석 ===\n");
            
            appendResourceOverallGrade(resourceMetrics, detailedAnalysis);
            appendResourceEfficiencyScores(resourceMetrics, detailedAnalysis);
            appendResourceHighlights(resourceMetrics, detailedAnalysis);
            appendOptimizationSuggestions(resourceMetrics, detailedAnalysis);
            
            detailedAnalysis.append("\n");
        }
    }
    
    private void appendResourceOverallGrade(JsonNode resourceMetrics, StringBuilder detailedAnalysis) {
        String overallGrade = resourceMetrics.path("overall_resource_grade").asText();
        if (!overallGrade.isEmpty()) {
            detailedAnalysis.append("• 종합 리소스 효율성: ").append(overallGrade).append(" 등급\n");
        }
    }
    
    private void appendResourceEfficiencyScores(JsonNode resourceMetrics, StringBuilder detailedAnalysis) {
        int cpuScore = resourceMetrics.path("cpu_efficiency_score").asInt();
        int memoryScore = resourceMetrics.path("memory_efficiency_score").asInt();
        int networkScore = resourceMetrics.path("network_efficiency_score").asInt();
        int diskScore = resourceMetrics.path("disk_io_score").asInt();
        
        detailedAnalysis.append(String.format("• CPU 효율성: %d/5\n", cpuScore));
        detailedAnalysis.append(String.format("• 메모리 효율성: %d/5\n", memoryScore));
        detailedAnalysis.append(String.format("• 네트워크 효율성: %d/5\n", networkScore));
        detailedAnalysis.append(String.format("• 디스크 I/O 효율성: %d/5\n", diskScore));
    }
    
    private void appendResourceHighlights(JsonNode resourceMetrics, StringBuilder detailedAnalysis) {
        JsonNode resourceHighlights = resourceMetrics.path("resource_highlights");
        if (resourceHighlights.isArray() && resourceHighlights.size() > 0) {
            detailedAnalysis.append("• 주요 특징:\n");
            for (JsonNode highlight : resourceHighlights) {
                detailedAnalysis.append("  - ").append(highlight.asText()).append("\n");
            }
        }
    }
    
    private void appendOptimizationSuggestions(JsonNode resourceMetrics, StringBuilder detailedAnalysis) {
        JsonNode optimizationSuggestions = resourceMetrics.path("optimization_suggestions");
        if (optimizationSuggestions.isArray() && optimizationSuggestions.size() > 0) {
            detailedAnalysis.append("• 최적화 제안:\n");
            for (JsonNode suggestion : optimizationSuggestions) {
                detailedAnalysis.append("  - ").append(suggestion.asText()).append("\n");
            }
        }
    }
    
    private void appendImprovementSections(JsonNode devOpsResponse, StringBuilder detailedAnalysis) {
        JsonNode improvementsLegacy = devOpsResponse.path("improvements");
        if (improvementsLegacy.isArray() && improvementsLegacy.size() > 0) {
            detailedAnalysis.append("=== 전반적 개선 제안 ===\n");
            for (JsonNode improvement : improvementsLegacy) {
                detailedAnalysis.append("• ").append(improvement.asText()).append("\n");
            }
            detailedAnalysis.append("\n");
        }
        
        JsonNode missedSteps = devOpsResponse.path("missed_steps");
        if (missedSteps.isArray() && missedSteps.size() > 0) {
            detailedAnalysis.append("=== 미충족 단계 ===\n");
            for (JsonNode step : missedSteps) {
                detailedAnalysis.append("• ").append(step.asText()).append("\n");
            }
            detailedAnalysis.append("\n");
        }
    }
    
    private void appendEvidenceSection(JsonNode devOpsResponse, StringBuilder detailedAnalysis) {
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
    }
    
    private void setOldDevOpsEvaluationScores(JsonNode devOpsResponse, EvaluationResultDTO result) {
        EvaluationResultDTO.CodeQualityScore codeQuality = createCodeQualityScore(devOpsResponse);
        result.setCodeQuality(codeQuality);
        
        EvaluationResultDTO.SecurityScore security = createSecurityScore(devOpsResponse);
        result.setSecurity(security);
        
        EvaluationResultDTO.StyleScore style = createStyleScore(devOpsResponse);
        result.setStyle(style);
    }
    
    private EvaluationResultDTO.CodeQualityScore createCodeQualityScore(JsonNode devOpsResponse) {
        EvaluationResultDTO.CodeQualityScore codeQuality = new EvaluationResultDTO.CodeQualityScore();
        JsonNode correctness = devOpsResponse.path("correctness");
        if (!correctness.isMissingNode()) {
            int score = (int) Math.round(correctness.path("score").asInt() * 100.0 / 5.0);
            codeQuality.setScore(score);
            codeQuality.setFeedback(correctness.path("reason").asText());
            codeQuality.setSuggestions("목표/체크리스트 충족도와 명령 시퀀스의 정확성을 기반으로 평가됨");
        }
        return codeQuality;
    }
    
    private EvaluationResultDTO.SecurityScore createSecurityScore(JsonNode devOpsResponse) {
        EvaluationResultDTO.SecurityScore security = new EvaluationResultDTO.SecurityScore();
        JsonNode efficiency = devOpsResponse.path("efficiency");
        if (!efficiency.isMissingNode()) {
            int score = (int) Math.round(efficiency.path("score").asInt() * 100.0 / 5.0);
            security.setScore(score);
            security.setFeedback(efficiency.path("reason").asText());
            security.setVulnerabilities("불필요한 재시도나 과도한 리소스 사용 없음");
            security.setRecommendations("효율적인 DevOps 실행 관행 적용");
        }
        return security;
    }
    
    private EvaluationResultDTO.StyleScore createStyleScore(JsonNode devOpsResponse) {
        EvaluationResultDTO.StyleScore style = new EvaluationResultDTO.StyleScore();
        JsonNode quality = devOpsResponse.path("quality");
        if (!quality.isMissingNode()) {
            int score = (int) Math.round(quality.path("score").asInt() * 100.0 / 5.0);
            style.setScore(score);
            style.setFeedback(quality.path("reason").asText());
            style.setStyleIssues("코드 구조와 가독성 관련");
            style.setImprovements("DevOps 모범 사례 준수 권장");
        }
        return style;
    }
    
    private String extractJsonFromResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        // 다양한 JSON 형식 처리
        String[] patterns = {
            // 마크다운 코드 블록
            "```json\\s*(.*)\\s*```",
            "```\\s*(.*)\\s*```",
            // 일반적인 JSON
            "(\\{.*\\})"
        };
        
        for (String pattern : patterns) {
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher m = p.matcher(content);
                if (m.find()) {
                    String extracted = m.group(1).trim();
                    if (!extracted.isEmpty() && extracted.startsWith("{")) {
                        log.debug("Extracted JSON using pattern: {}", pattern);
                        return extracted;
                    }
                }
            } catch (java.util.regex.PatternSyntaxException e) {
                log.debug("Invalid regex pattern {}: {}", pattern, e.getMessage());
            } catch (Exception e) {
                log.debug("Unexpected error extracting JSON with pattern {}: {}", pattern, e.getMessage());
            }
        }
        
        // 패턴 매칭 실패 시 기본 방식 사용
        int jsonStart = content.indexOf("{");
        int jsonEnd = content.lastIndexOf("}") + 1;
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return content.substring(jsonStart, jsonEnd);
        }
        
        log.warn("No JSON structure found in content: {}", content.substring(0, Math.min(200, content.length())));
        return "";
    }
    
    private EvaluationResultDTO createFallbackResult() {
        return createFallbackResult("Unknown error");
    }
    
    private EvaluationResultDTO createFallbackResult(String errorReason) {
        log.warn("Creating fallback result due to: {}", errorReason);
        
        // AI 평가 중 오류 발생 시 대체 결과
        EvaluationResultDTO result = new EvaluationResultDTO();
        result.setOverallScore(50);
        result.setFeedback(String.format("AI 평가 중 오류가 발생했습니다 (%s). 자동 평가가 완료되지 못했지만, 제출하신 코드는 저장되었습니다. 담당자가 수동으로 검토 후 별도 안내드릴 예정입니다.", errorReason));
        result.setDetailedAnalysis(String.format("시스템 오류로 인해 상세 분석을 완료하지 못했습니다 (오류: %s). 기술 팀에서 이슈를 확인 중이며, 빠른 시일 내에 정확한 평가 결과를 제공하겠습니다.", errorReason));
        
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
    
    /**
     * 평가 기준 JSON을 파싱하여 읽기 쉬운 형태로 변환
     */
    private String parseEvaluationCriteria(String evaluationCriteria) {
        if (evaluationCriteria == null || evaluationCriteria.trim().isEmpty()) {
            return "평가 기준이 없습니다.";
        }
        
        try {
            // JSON 형태인 경우 파싱 시도
            JsonNode criteriaNode = objectMapper.readTree(evaluationCriteria);
            StringBuilder parsed = new StringBuilder();
            
            // criteria 배열이 있는 경우
            if (criteriaNode.has("criteria") && criteriaNode.get("criteria").isArray()) {
                JsonNode criteriaArray = criteriaNode.get("criteria");
                for (int i = 0; i < criteriaArray.size(); i++) {
                    JsonNode criterion = criteriaArray.get(i);
                    parsed.append(String.format("%d. ", i + 1));
                    
                    if (criterion.isTextual()) {
                        parsed.append(criterion.asText());
                    } else if (criterion.has("objective")) {
                        parsed.append(criterion.get("objective").asText());
                        if (criterion.has("description")) {
                            parsed.append(" - ").append(criterion.get("description").asText());
                        }
                    } else {
                        parsed.append(criterion.toString());
                    }
                    parsed.append("\n");
                }
            }
            // objectives 배열이 있는 경우
            else if (criteriaNode.has("objectives") && criteriaNode.get("objectives").isArray()) {
                JsonNode objectivesArray = criteriaNode.get("objectives");
                for (int i = 0; i < objectivesArray.size(); i++) {
                    JsonNode objective = objectivesArray.get(i);
                    parsed.append(String.format("%d. ", i + 1));
                    
                    if (objective.isTextual()) {
                        parsed.append(objective.asText());
                    } else if (objective.has("title")) {
                        parsed.append(objective.get("title").asText());
                        if (objective.has("details")) {
                            parsed.append(" - ").append(objective.get("details").asText());
                        }
                    } else {
                        parsed.append(objective.toString());
                    }
                    parsed.append("\n");
                }
            }
            // 단순 텍스트 배열인 경우
            else if (criteriaNode.isArray()) {
                for (int i = 0; i < criteriaNode.size(); i++) {
                    parsed.append(String.format("%d. %s\n", i + 1, criteriaNode.get(i).asText()));
                }
            }
            // 그 외의 경우 원본 반환
            else {
                return evaluationCriteria;
            }
            
            return parsed.toString();
            
        } catch (Exception e) {
            // JSON 파싱 실패 시 원본 텍스트 반환
            log.debug("평가 기준 JSON 파싱 실패, 원본 텍스트 사용: {}", e.getMessage());
            return evaluationCriteria;
        }
    }
}