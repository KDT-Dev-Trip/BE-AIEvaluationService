package ac.su.kdt.beaievaluationservice.testutil;

import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.entity.EvaluationHistory;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.dto.EvaluationResultDTO;

import java.time.LocalDateTime;

// 테스트 데이터 생성을 위한 빌더 패턴을 사용하여
// 다양한 테스트 케이스에 필요한 객체들을 쉽게 생성할 수 있도록 도와줍
public class TestDataBuilder {

    public static class MissionCompletedEventBuilder {
        private String eventType = "MISSION_COMPLETED";
        private String userId = "test-user-123";
        private String missionId = "test-mission-456";
        private String missionAttemptId = "test-attempt-789";
        private String missionType = "Docker Container";
        private String code = "FROM ubuntu:20.04\nRUN apt-get update\nEXPOSE 8080";
        private String missionTitle = "Docker 컨테이너 생성 실습";
        private LocalDateTime completedAt = LocalDateTime.now();

        public MissionCompletedEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public MissionCompletedEventBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public MissionCompletedEventBuilder missionId(String missionId) {
            this.missionId = missionId;
            return this;
        }

        public MissionCompletedEventBuilder missionAttemptId(String missionAttemptId) {
            this.missionAttemptId = missionAttemptId;
            return this;
        }

        public MissionCompletedEventBuilder missionType(String missionType) {
            this.missionType = missionType;
            return this;
        }

        public MissionCompletedEventBuilder code(String code) {
            this.code = code;
            return this;
        }

        public MissionCompletedEventBuilder missionTitle(String missionTitle) {
            this.missionTitle = missionTitle;
            return this;
        }

        public MissionCompletedEventBuilder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public MissionCompletedEvent build() {
            MissionCompletedEvent event = new MissionCompletedEvent();
            event.setEventType(eventType);
            event.setUserId(userId);
            event.setMissionId(missionId);
            event.setMissionAttemptId(missionAttemptId);
            event.setMissionType(missionType);
            event.setCode(code);
            event.setMissionTitle(missionTitle);
            event.setCompletedAt(completedAt);
            return event;
        }
    }

    public static class AIEvaluationBuilder {
        private Long id = 1L;
        private String missionAttemptId = "test-attempt-789";
        private AIEvaluation.EvaluationStatus status = AIEvaluation.EvaluationStatus.PENDING;
        private String evaluationResult = null;
        private String aiModelVersion = "gemini-1.5-pro";
        private String errorMessage = null;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public AIEvaluationBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public AIEvaluationBuilder missionAttemptId(String missionAttemptId) {
            this.missionAttemptId = missionAttemptId;
            return this;
        }

        public AIEvaluationBuilder status(AIEvaluation.EvaluationStatus status) {
            this.status = status;
            return this;
        }

        public AIEvaluationBuilder evaluationResult(String evaluationResult) {
            this.evaluationResult = evaluationResult;
            return this;
        }

        public AIEvaluationBuilder aiModelVersion(String aiModelVersion) {
            this.aiModelVersion = aiModelVersion;
            return this;
        }

        public AIEvaluationBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AIEvaluationBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public AIEvaluationBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public AIEvaluation build() {
            AIEvaluation evaluation = new AIEvaluation();
            evaluation.setId(id);
            evaluation.setMissionAttemptId(missionAttemptId);
            evaluation.setStatus(status);
            evaluation.setEvaluationResult(evaluationResult);
            evaluation.setAiModelVersion(aiModelVersion);
            evaluation.setErrorMessage(errorMessage);
            evaluation.setCreatedAt(createdAt);
            evaluation.setUpdatedAt(updatedAt);
            return evaluation;
        }
    }

    public static class EvaluationResultDTOBuilder {
        private Integer overallScore = 85;
        private String feedback = "Overall good code quality with room for improvement";
        private String detailedAnalysis = "Detailed analysis of the submitted code...";
        private EvaluationResultDTO.CodeQualityScore codeQuality = createDefaultCodeQuality();
        private EvaluationResultDTO.SecurityScore security = createDefaultSecurity();
        private EvaluationResultDTO.StyleScore style = createDefaultStyle();

        public EvaluationResultDTOBuilder overallScore(Integer overallScore) {
            this.overallScore = overallScore;
            return this;
        }

        public EvaluationResultDTOBuilder feedback(String feedback) {
            this.feedback = feedback;
            return this;
        }

        public EvaluationResultDTOBuilder detailedAnalysis(String detailedAnalysis) {
            this.detailedAnalysis = detailedAnalysis;
            return this;
        }

        public EvaluationResultDTOBuilder codeQuality(EvaluationResultDTO.CodeQualityScore codeQuality) {
            this.codeQuality = codeQuality;
            return this;
        }

        public EvaluationResultDTOBuilder security(EvaluationResultDTO.SecurityScore security) {
            this.security = security;
            return this;
        }

        public EvaluationResultDTOBuilder style(EvaluationResultDTO.StyleScore style) {
            this.style = style;
            return this;
        }

        public EvaluationResultDTO build() {
            EvaluationResultDTO result = new EvaluationResultDTO();
            result.setOverallScore(overallScore);
            result.setFeedback(feedback);
            result.setDetailedAnalysis(detailedAnalysis);
            result.setCodeQuality(codeQuality);
            result.setSecurity(security);
            result.setStyle(style);
            return result;
        }

        private static EvaluationResultDTO.CodeQualityScore createDefaultCodeQuality() {
            return new EvaluationResultDTO.CodeQualityScore(80,
                "Code structure is well organized", 
                "Add more comments for better readability");
        }

        private static EvaluationResultDTO.SecurityScore createDefaultSecurity() {
            return new EvaluationResultDTO.SecurityScore(90,
                "No major security vulnerabilities detected", 
                "None found", 
                "Continue following security best practices");
        }

        private static EvaluationResultDTO.StyleScore createDefaultStyle() {
            return new EvaluationResultDTO.StyleScore(85,
                "Consistent coding style throughout", 
                "Minor indentation inconsistencies", 
                "Use consistent indentation and spacing");
        }
    }

    public static class EvaluationSummaryBuilder {
        private Long id = 1L;
        private String userId = "test-user-123";
        private String missionId = "test-mission-456";
        private String missionAttemptId = "test-attempt-789";
        private String missionTitle = "Docker 컨테이너 생성 실습";
        private String missionType = "Docker Container";
        private Integer overallScore = 85;
        private Integer codeQualityScore = 80;
        private Integer securityScore = 90;
        private Integer styleScore = 85;
        private AIEvaluation.EvaluationStatus status = AIEvaluation.EvaluationStatus.COMPLETED;
        private String feedbackSummary = "Overall good performance";
        private Long evaluationDurationMs = 15000L;
        private AIEvaluation aiEvaluation = null;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public EvaluationSummaryBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public EvaluationSummaryBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public EvaluationSummaryBuilder missionId(String missionId) {
            this.missionId = missionId;
            return this;
        }

        public EvaluationSummaryBuilder missionAttemptId(String missionAttemptId) {
            this.missionAttemptId = missionAttemptId;
            return this;
        }

        public EvaluationSummaryBuilder aiEvaluation(AIEvaluation aiEvaluation) {
            this.aiEvaluation = aiEvaluation;
            return this;
        }

        public EvaluationSummary build() {
            EvaluationSummary summary = new EvaluationSummary();
            summary.setId(id);
            summary.setUserId(userId);
            summary.setMissionId(missionId);
            summary.setMissionAttemptId(missionAttemptId);
            summary.setMissionTitle(missionTitle);
            summary.setMissionType(missionType);
            summary.setOverallScore(overallScore);
            summary.setCodeQualityScore(codeQualityScore);
            summary.setSecurityScore(securityScore);
            summary.setStyleScore(styleScore);
            summary.setStatus(status);
            summary.setFeedbackSummary(feedbackSummary);
            summary.setEvaluationDurationMs(evaluationDurationMs);
            summary.setAiEvaluation(aiEvaluation);
            summary.setCreatedAt(createdAt);
            summary.setUpdatedAt(updatedAt);
            return summary;
        }
    }

    // Static factory methods for convenience
    public static MissionCompletedEventBuilder missionCompletedEvent() {
        return new MissionCompletedEventBuilder();
    }

    public static AIEvaluationBuilder aiEvaluation() {
        return new AIEvaluationBuilder();
    }

    public static EvaluationResultDTOBuilder evaluationResult() {
        return new EvaluationResultDTOBuilder();
    }

    public static EvaluationSummaryBuilder evaluationSummary() {
        return new EvaluationSummaryBuilder();
    }
}