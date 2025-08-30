package ac.su.kdt.beaievaluationservice.controller;

import ac.su.kdt.beaievaluationservice.dto.request.EvaluationRequest;
import ac.su.kdt.beaievaluationservice.dto.response.ApiResponse;
import ac.su.kdt.beaievaluationservice.dto.response.*;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.EvaluationSummary;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.service.EvaluationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// AI 평가 REST API 컨트롤러 (테스트/개발용)
@Tag(name = "AI 평가", description = "AI를 통한 코드 평가 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final AIEvaluationRepository aiEvaluationRepository;
    private final EvaluationSummaryRepository evaluationSummaryRepository;
    private final ObjectMapper objectMapper;

    /**
     * AI 평가 요청 (미션 완료)
     */
    @Operation(
        summary = "AI 평가 시작",
        description = "사용자가 미션을 완료한 후 작성한 코드에 대해 AI 평가를 시작합니다. " +
                     "평가는 비동기로 처리되며, 완료까지 약 10-30초 소요됩니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "202", 
            description = "AI 평가 시작 성공 (비동기 처리)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 요청 (Validation 실패)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", 
            description = "중복 평가 요청 (이미 평가가 진행 중이거나 완료됨)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<EvaluationResponse>> startEvaluation(@Valid @RequestBody EvaluationRequest request) {
        log.info("Evaluation request received for missionAttemptId: {}, userId: {}", 
                request.getMissionAttemptId(), request.getUserId());

        try {
            // 중복 평가 체크
            if (aiEvaluationRepository.existsByMissionAttemptId(request.getMissionAttemptId())) {
                return ResponseEntity.status(409)
                        .body(ApiResponse.failure("이미 평가가 진행 중이거나 완료된 미션입니다."));
            }

            // Request를 Event로 변환
            MissionCompletedEvent event = convertToEvent(request);
            
            // 평가 시작 (동기 처리)
            evaluationService.processEvaluation(event);
            
            // 즉시 응답 (평가는 비동기로 진행)
            EvaluationResponse response = EvaluationResponse.builder()
                    .missionAttemptId(request.getMissionAttemptId())
                    .userId(request.getUserId())
                    .status("PROCESSING")
                    .aiModelVersion("gemini-2.0-flash-exp")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            return ResponseEntity.accepted()
                    .body(ApiResponse.success("AI 평가가 시작되었습니다. 완료까지 약 10-30초 소요됩니다.", response));

        } catch (Exception e) {
            log.error("Failed to start evaluation for missionAttemptId: {}", request.getMissionAttemptId(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("AI 평가 시작 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * AI 평가 결과 조회
     */
    @Operation(
        summary = "AI 평가 결과 조회",
        description = "특정 미션 시도에 대한 AI 평가 결과를 조회합니다. " +
                     "평가가 진행 중이면 PROCESSING 상태, 완료되면 상세한 평가 결과를 반환합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "평가 결과 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "평가 결과를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/{missionAttemptId}")
    public ResponseEntity<ApiResponse<EvaluationResponse>> getEvaluationResult(
            @Parameter(description = "미션 시도 ID", required = true)
            @PathVariable String missionAttemptId) {
        log.info("Get evaluation result for missionAttemptId: {}", missionAttemptId);

        try {
            Optional<AIEvaluation> evaluation = aiEvaluationRepository.findByMissionAttemptId(missionAttemptId);
            
            if (evaluation.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.failure("해당 미션 시도의 평가를 찾을 수 없습니다."));
            }

            AIEvaluation aiEvaluation = evaluation.get();
            EvaluationResponse response = convertToResponse(aiEvaluation);
            
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Failed to get evaluation result for missionAttemptId: {}", missionAttemptId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("평가 결과 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 사용자별 평가 이력 조회
     */
    @Operation(
        summary = "사용자별 평가 이력 조회",
        description = "특정 사용자의 모든 AI 평가 이력을 최신순으로 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "평가 이력 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/user/{userId}/history")
    public ResponseEntity<ApiResponse<List<EvaluationResponse>>> getEvaluationHistory(
            @Parameter(description = "사용자 ID", required = true)
            @PathVariable String userId) {
        log.info("Get evaluation history for userId: {}", userId);

        try {
            List<EvaluationSummary> summaries = evaluationSummaryRepository.findByUserIdOrderByCreatedAtDesc(Long.parseLong(userId));
            
            List<EvaluationResponse> responses = summaries.stream()
                    .map(this::convertSummaryToResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("평가 이력 %d건을 조회했습니다.", responses.size()), 
                    responses));

        } catch (Exception e) {
            log.error("Failed to get evaluation history for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("평가 이력 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 평가 상태 조회 (진행 중인 평가들)
     */
    @Operation(
        summary = "상태별 평가 목록 조회",
        description = "특정 상태(PROCESSING, COMPLETED, FAILED)의 평가들을 조회합니다. " +
                     "시스템 모니터링이나 디버깅에 유용합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "상태별 평가 목록 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 상태값 (PROCESSING, COMPLETED, FAILED만 가능)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<EvaluationResponse>>> getEvaluationsByStatus(
            @Parameter(description = "평가 상태 (PROCESSING, COMPLETED, FAILED)", required = true)
            @PathVariable String status) {
        log.info("Get evaluations by status: {}", status);

        try {
            AIEvaluation.EvaluationStatus evaluationStatus = AIEvaluation.EvaluationStatus.valueOf(status.toUpperCase());
            List<AIEvaluation> evaluations = aiEvaluationRepository.findByStatus(evaluationStatus);
            
            List<EvaluationResponse> responses = evaluations.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("상태 '%s'인 평가 %d건을 조회했습니다.", status, responses.size()), 
                    responses));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.failure("유효하지 않은 상태입니다. (PENDING, PROCESSING, COMPLETED, FAILED)"));
        } catch (Exception e) {
            log.error("Failed to get evaluations by status: {}", status, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("상태별 평가 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 즉시 평가 실행 (동기 방식)
     */
    @Operation(
        summary = "즉시 AI 평가 실행",
        description = "요청과 동시에 바로 AI 평가를 실행하고 결과를 반환합니다. " +
                     "테스트나 즉시 결과가 필요한 경우에 사용합니다. (동기 처리, 10-30초 소요)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "AI 평가 완료 및 결과 반환",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 요청 (Validation 실패)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "AI 평가 실행 중 오류 발생",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<EvaluationResponse>> evaluateImmediately(@Valid @RequestBody EvaluationRequest request) {
        log.info("Immediate evaluation request for missionAttemptId: {}, userId: {}", 
                request.getMissionAttemptId(), request.getUserId());

        try {
            // Request를 Event로 변환
            MissionCompletedEvent event = convertToEvent(request);
            
            // 동기적으로 평가 실행
            evaluationService.processEvaluation(event);
            
            // 결과 조회
            Optional<AIEvaluation> evaluation = aiEvaluationRepository.findByMissionAttemptId(request.getMissionAttemptId());
            
            if (evaluation.isEmpty()) {
                return ResponseEntity.status(500)
                        .body(ApiResponse.failure("평가 실행 후 결과를 찾을 수 없습니다."));
            }
            
            AIEvaluation eval = evaluation.get();
            EvaluationResponse response = convertToResponse(eval);
            
            // 평가 실패나 유효하지 않은 결과 체크
            if (eval.getStatus() == AIEvaluation.EvaluationStatus.FAILED) {
                return ResponseEntity.status(422) // Unprocessable Entity
                        .body(ApiResponse.failure("AI 평가에 실패했습니다: " + eval.getErrorMessage(), response));
            }
            
            // 점수가 0이고 피드백이 명확히 실패를 나타내는 경우에만 실패로 간주
            if (response.getOverallScore() != null && response.getOverallScore() == 0 && 
                response.getFeedback() != null && 
                (response.getFeedback().contains("평가할 수 없습니다") || 
                 response.getFeedback().contains("데이터가 부족합니다") ||
                 response.getFeedback().contains("분석이 불가능합니다"))) {
                return ResponseEntity.status(400) // Bad Request
                        .body(ApiResponse.failure("AI가 제공된 데이터를 평가하기에 부적절하다고 판단했습니다.", response));
            }
            
            return ResponseEntity.ok()
                    .body(ApiResponse.success("AI 평가가 완료되었습니다.", response));

        } catch (Exception e) {
            log.error("Failed to execute immediate evaluation for missionAttemptId: {}", request.getMissionAttemptId(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("AI 평가 실행 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 미션별 S3 데이터 조회 정보 (디버깅용)
     */
    @Operation(
        summary = "미션의 S3 데이터 조회 정보",
        description = "특정 미션 시도에 대한 S3 저장소 주소와 Pre-signed URL 정보를 조회합니다. " +
                     "디버깅이나 데이터 검증에 유용합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "S3 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", 
            description = "미션을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/{missionAttemptId}/s3-info")
    public ResponseEntity<ApiResponse<Object>> getS3Info(
            @Parameter(description = "미션 시도 ID", required = true)
            @PathVariable String missionAttemptId) {
        log.info("Get S3 info for missionAttemptId: {}", missionAttemptId);

        try {
            Optional<AIEvaluation> evaluation = aiEvaluationRepository.findByMissionAttemptId(missionAttemptId);
            
            if (evaluation.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.failure("해당 미션 시도의 평가를 찾을 수 없습니다."));
            }
            
            // 평가 결과에서 S3 관련 정보 추출 (MissionCompletedEvent에서 전달된 정보들)
            var s3Info = new Object() {
                public final String missionAttemptId = evaluation.get().getMissionAttemptId();
                public final String evaluationStatus = evaluation.get().getStatus().name();
                public final String aiModelVersion = evaluation.get().getAiModelVersion();
                public final String note = "S3 저장소 주소와 Pre-signed URL은 평가 프로세스에서 사용되며, " +
                                          "실제 정보는 MissionCompletedEvent를 통해 전달됩니다.";
            };
            
            return ResponseEntity.ok(ApiResponse.success("S3 정보를 조회했습니다.", s3Info));

        } catch (Exception e) {
            log.error("Failed to get S3 info for missionAttemptId: {}", missionAttemptId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("S3 정보 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 전체 평가 통계 조회 (관리자용)
     */
    @Operation(
        summary = "전체 평가 통계 조회",
        description = "시스템 전체의 AI 평가 통계를 조회합니다. " +
                     "총 평가 수, 상태별 개수, 평균 점수, 처리 시간 등의 정보를 제공합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "평가 통계 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getEvaluationStats() {
        log.info("Get evaluation statistics");

        try {
            long totalEvaluations = aiEvaluationRepository.count();
            long completedEvaluations = aiEvaluationRepository.countByStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            long failedEvaluations = aiEvaluationRepository.countByStatus(AIEvaluation.EvaluationStatus.FAILED);
            long processingEvaluations = aiEvaluationRepository.countByStatus(AIEvaluation.EvaluationStatus.PROCESSING);
            
            var stats = new Object() {
                public final long total = totalEvaluations;
                public final long completed = completedEvaluations;
                public final long failed = failedEvaluations;
                public final long processing = processingEvaluations;
                public final double successRate = totalEvaluations > 0 ? 
                    (double) completedEvaluations / totalEvaluations * 100 : 0.0;
            };
            
            return ResponseEntity.ok(ApiResponse.success("평가 통계를 조회했습니다.", stats));

        } catch (Exception e) {
            log.error("Failed to get evaluation statistics", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("평가 통계 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    /**
     * 사용자 평가 대시보드 전체 데이터 조회
     */
    @Operation(
        summary = "사용자 평가 대시보드",
        description = "사용자의 전체 평가 대시보드 데이터를 조회합니다. " +
                     "평가 요약, 최근 평가 결과, 성과 분석, 학습 진행도 등을 포함합니다."
    )
    @GetMapping("/user/{userId}/dashboard")
    public ResponseEntity<ApiResponse<UserEvaluationDashboardDTO>> getUserEvaluationDashboard(
            @Parameter(description = "사용자 ID", required = true)
            @PathVariable String userId) {
        log.info("Get user evaluation dashboard for userId: {}", userId);

        try {
            Long userIdLong = Long.parseLong(userId);
            
            // 기본 통계 조회
            Long totalEvaluations = evaluationSummaryRepository.countTotalEvaluationsByUserId(userIdLong);
            Long completedEvaluations = evaluationSummaryRepository.countCompletedEvaluationsByUserId(userIdLong);
            Integer totalStamps = evaluationSummaryRepository.getTotalStampsByUserId(userIdLong);
            
            // 평가 요약
            EvaluationSummaryDTO evaluationSummary = EvaluationSummaryDTO.builder()
                    .totalEvaluations(totalEvaluations)
                    .completedEvaluations(completedEvaluations)
                    .totalStampsEarned(totalStamps)
                    .build();
            
            // 최근 평가 결과 (가장 최근 평가 1개)
            List<EvaluationSummary> recentSummaries = evaluationSummaryRepository.findByUserIdOrderByCreatedAtDesc(userIdLong);
            RecentEvaluationResultDTO recentEvaluation = null;
            if (!recentSummaries.isEmpty()) {
                EvaluationSummary recent = recentSummaries.get(0);
                recentEvaluation = buildRecentEvaluationResult(recent);
            }
            
            // 성과 분석
            PerformanceMetricsDTO performanceMetrics = buildPerformanceMetrics(userIdLong);
            
            // 학습 진행도
            LearningProgressDTO learningProgress = buildLearningProgress(userIdLong);
            
            // 최근 활동 (최근 10개 평가 이력)
            List<UserEvaluationDashboardDTO.EvaluationHistoryItemDTO> recentActivity = recentSummaries.stream()
                    .limit(10)
                    .map(summary -> UserEvaluationDashboardDTO.EvaluationHistoryItemDTO.builder()
                            .score(summary.getOverallScore())
                            .missionName(summary.getMissionTitle())
                            .evaluationDate(summary.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            
            // 전체 학습 통계
            UserEvaluationDashboardDTO.OverallLearningStatsDTO overallStats = buildOverallLearningStats(userIdLong);
            
            UserEvaluationDashboardDTO dashboard = UserEvaluationDashboardDTO.builder()
                    .evaluationSummary(evaluationSummary)
                    .recentEvaluation(recentEvaluation)
                    .performanceMetrics(performanceMetrics)
                    .learningProgress(learningProgress)
                    .recentActivity(recentActivity)
                    .overallStats(overallStats)
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success("사용자 대시보드 데이터를 조회했습니다.", dashboard));

        } catch (NumberFormatException e) {
            return ResponseEntity.status(400)
                    .body(ApiResponse.failure("잘못된 사용자 ID 형식입니다."));
        } catch (Exception e) {
            log.error("Failed to get user evaluation dashboard for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("대시보드 데이터 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // Helper methods
    private MissionCompletedEvent convertToEvent(EvaluationRequest request) {
        log.info("=== convertToEvent DEBUG ===");
        log.info("Request userId: {}", request.getUserId());
        log.info("Request missionId: {}", request.getMissionId());
        log.info("Request missionAttemptId: {}", request.getMissionAttemptId());
        
        MissionCompletedEvent event = new MissionCompletedEvent();
        event.setEventType("MISSION_COMPLETED");
        event.setUserId(request.getUserId());
        event.setMissionId(request.getMissionId());
        event.setMissionAttemptId(request.getMissionAttemptId());
        event.setMissionType(request.getMissionType());
        event.setMissionTitle(request.getMissionTitle());
        event.setCode(request.getCode());
        event.setCompletedAt(LocalDateTime.now());
        
        log.info("Event after setting - userId: {}, missionId: {}, missionAttemptId: {}", 
                event.getUserId(), event.getMissionId(), event.getMissionAttemptId());
        
        // === S3 통합 필드들 ===
        // MissionObjective와 Checklist는 제거됨 - evaluationCriteria로 대체
        event.setEvaluationCriteria(request.getMissionObjective());
        event.setMissionGuide("미션 가이드 없음");
        event.setS3StorageUrl(request.getS3StorageUrl());
        event.setS3PreSignedUrl(request.getS3PreSignedUrl());
        event.setStatistics(request.getStatistics());
        
        return event;
    }

    private EvaluationResponse convertToResponse(AIEvaluation evaluation) {
        // EvaluationSummary가 있는 경우 우선 사용 (더 완전한 정보)
        Optional<EvaluationSummary> summaryOpt = evaluationSummaryRepository.findByMissionAttemptId(evaluation.getMissionAttemptId());
        if (summaryOpt.isPresent()) {
            return convertSummaryToResponse(summaryOpt.get());
        }
        
        // EvaluationSummary가 없는 경우 AIEvaluation 정보만 사용
        EvaluationResponse.EvaluationResponseBuilder builder = EvaluationResponse.builder()
                .evaluationId(evaluation.getId())
                .missionAttemptId(evaluation.getMissionAttemptId())
                .status(evaluation.getStatus().name())
                .aiModelVersion(evaluation.getAiModelVersion())
                .missionTitle(evaluation.getMissionTitle())
                .missionType(evaluation.getMissionType())
                .processingTimeMs(evaluation.getProcessingTimeMs())
                .createdAt(evaluation.getCreatedAt())
                .updatedAt(evaluation.getUpdatedAt())
                .errorMessage(evaluation.getErrorMessage());

        // 평가 결과가 있는 경우 파싱하여 추가 (새로운 DevOps 채점관 형식 지원)
        if (evaluation.getEvaluationResult() != null && !evaluation.getEvaluationResult().isEmpty()) {
            try {
                JsonNode resultJson = objectMapper.readTree(evaluation.getEvaluationResult());
                
                // 새로운 형식 확인 (correctness, efficiency, quality 기반)
                if (resultJson.has("total_score")) {
                    // 새로운 DevOps 채점관 형식
                    int totalScore = resultJson.path("total_score").asInt();
                    // 15점 만점을 100점 만점으로 변환 (총점 * 100 / 15)
                    int convertedScore = (int) Math.round(totalScore * 100.0 / 15.0);
                    
                    builder.overallScore(convertedScore)
                           .feedback(resultJson.path("feedback").asText());
                    
                    // 개별 점수들 (5점 만점을 100점 만점으로 변환)
                    JsonNode correctness = resultJson.path("correctness");
                    if (!correctness.isMissingNode()) {
                        int score = (int) Math.round(correctness.path("score").asInt() * 100.0 / 5.0);
                        builder.codeQualityScore(score);
                    }
                    
                    JsonNode efficiency = resultJson.path("efficiency");
                    if (!efficiency.isMissingNode()) {
                        int score = (int) Math.round(efficiency.path("score").asInt() * 100.0 / 5.0);
                        builder.securityScore(score); // efficiency를 security 점수로 매핑
                    }
                    
                    JsonNode quality = resultJson.path("quality");
                    if (!quality.isMissingNode()) {
                        int score = (int) Math.round(quality.path("score").asInt() * 100.0 / 5.0);
                        builder.styleScore(score); // quality를 style 점수로 매핑
                    }
                    
                } else {
                    // 기존 형식 (호환성 유지)
                    builder.overallScore(resultJson.path("overallScore").asInt())
                           .feedback(resultJson.path("feedback").asText());
                           
                    // 세부 점수들 파싱
                    JsonNode codeQuality = resultJson.path("codeQuality");
                    if (!codeQuality.isMissingNode()) {
                        builder.codeQualityScore(codeQuality.path("score").asInt());
                    }
                    
                    JsonNode security = resultJson.path("security");
                    if (!security.isMissingNode()) {
                        builder.securityScore(security.path("score").asInt());
                    }
                    
                    JsonNode style = resultJson.path("style");
                    if (!style.isMissingNode()) {
                        builder.styleScore(style.path("score").asInt());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse evaluation result JSON for id: {}", evaluation.getId(), e);
            }
        }

        return builder.build();
    }

    private EvaluationResponse convertSummaryToResponse(EvaluationSummary summary) {
        return EvaluationResponse.builder()
                .evaluationId(summary.getAiEvaluation().getId())
                .missionAttemptId(summary.getMissionAttemptId())
                .userId(String.valueOf(summary.getUserId()))
                .status(summary.getStatus().name())
                .aiModelVersion(summary.getAiEvaluation().getAiModelVersion())
                // 기본 평가 점수들
                .overallScore(summary.getOverallScore())
                .codeQualityScore(summary.getCodeQualityScore())
                .securityScore(summary.getSecurityScore())
                .styleScore(summary.getStyleScore())
                .feedback(summary.getFeedbackSummary())
                // 추가 평가 지표들 (이전에 누락되었던 필드들)
                .securityRiskLevel(summary.getSecurityRiskLevel())
                .efficiencyGrade(summary.getEfficiencyGrade())
                .bestPracticeScore(summary.getBestPracticeScore())
                .reliabilityScore(summary.getReliabilityScore())
                // 명령어 통계
                .totalCommandCount(summary.getCommandsExecuted())
                .significantCommandCount(summary.getSignificantCommands())
                .errorCommandCount(summary.getErrorCommands())
                // 미션 메타데이터
                .missionTitle(summary.getMissionTitle())
                .missionType(summary.getMissionType())
                .missionDifficulty(summary.getMissionDifficulty())
                // 처리 시간 및 타임스탬프
                .processingTimeMs(summary.getEvaluationDurationMs())
                .createdAt(summary.getCreatedAt())
                .updatedAt(summary.getUpdatedAt())
                .build();
    }
    
    private RecentEvaluationResultDTO buildRecentEvaluationResult(EvaluationSummary summary) {
        return RecentEvaluationResultDTO.builder()
                .overallScore(summary.getOverallScore())
                .correctnessScore(summary.getCorrectnessScore())
                .efficiencyScore(summary.getEfficiencyScore())
                .qualityScore(summary.getQualityScore())
                .totalCommandCount(summary.getCommandsExecuted())
                .significantCommandCount(summary.getSignificantCommands())
                .errorCommandCount(summary.getErrorCommands())
                .evaluationStartTime(summary.getCreatedAt())
                .evaluationEndTime(summary.getUpdatedAt())
                .securityRiskLevel(summary.getSecurityRiskLevel())
                .categoryPerformances(List.of()) // JSON 파싱 후 추가 구현 필요
                .build();
    }
    
    private PerformanceMetricsDTO buildPerformanceMetrics(Long userId) {
        List<Object[]> missionTypeData = evaluationSummaryRepository.getMissionTypePerformanceByUserId(userId);
        List<PerformanceMetricsDTO.MissionTypePerformanceDTO> missionTypePerformances = missionTypeData.stream()
                .map(data -> PerformanceMetricsDTO.MissionTypePerformanceDTO.builder()
                        .missionType((String) data[0])
                        .totalAttempts(((Long) data[1]).intValue())
                        .averageScore((Double) data[2])
                        .averageSuccessRate((Double) data[3] * 100)
                        .build())
                .collect(Collectors.toList());
        
        List<Object[]> individualMissionData = evaluationSummaryRepository.getIndividualMissionPerformanceByUserId(userId);
        List<PerformanceMetricsDTO.IndividualMissionPerformanceDTO> individualMissionPerformances = individualMissionData.stream()
                .map(data -> PerformanceMetricsDTO.IndividualMissionPerformanceDTO.builder()
                        .missionId((String) data[0])
                        .missionTitle((String) data[1])
                        .attemptCount(((Long) data[2]).intValue())
                        // averageScore field removed from DTO
                        .successRate((Double) data[4] * 100)
                        .difficulty((String) data[5])
                        .averageCompletionTime(data[6] != null ? ((Long) data[6]).doubleValue() / (1000 * 60) : 0.0) // ms to minutes
                        .build())
                .collect(Collectors.toList());
        
        return PerformanceMetricsDTO.builder()
                .missionTypePerformances(missionTypePerformances)
                .individualMissionPerformances(individualMissionPerformances)
                .build();
    }
    
    private LearningProgressDTO buildLearningProgress(Long userId) {
        Long completedCount = evaluationSummaryRepository.countCompletedEvaluationsByUserId(userId);
        Double averageScore = evaluationSummaryRepository.getAverageScoreByUserId(userId);
        Integer totalStamps = evaluationSummaryRepository.getTotalStampsByUserId(userId);
        List<Integer> recentTrend = evaluationSummaryRepository.getRecentScoreTrendByUserId(userId);
        
        List<Object[]> difficultyData = evaluationSummaryRepository.getDifficultyPerformanceByUserId(userId);
        List<LearningProgressDTO.DifficultyPerformanceDTO> difficultyPerformances = difficultyData.stream()
                .map(data -> LearningProgressDTO.DifficultyPerformanceDTO.builder()
                        .difficulty((String) data[0])
                        .completedCount(((Long) data[1]).intValue())
                        .averageScore((Double) data[2])
                        .successRate((Double) data[3] * 100)
                        .build())
                .collect(Collectors.toList());
        
        return LearningProgressDTO.builder()
                .completedMissions(completedCount.intValue())
                .averageScore(averageScore != null ? averageScore : 0.0)
                .totalStamps(totalStamps)
                .recentScoreTrend(recentTrend)
                .difficultyPerformances(difficultyPerformances)
                .build();
    }
    
    private UserEvaluationDashboardDTO.OverallLearningStatsDTO buildOverallLearningStats(Long userId) {
        List<Object[]> statsData = evaluationSummaryRepository.getOverallLearningStatsByUserId(userId);
        if (statsData.isEmpty()) {
            return UserEvaluationDashboardDTO.OverallLearningStatsDTO.builder()
                    .highestScore(0)
                    .averageScore(0.0)
                    .lowestScore(0)
                    .build();
        }
        
        Object[] stats = statsData.get(0);
        return UserEvaluationDashboardDTO.OverallLearningStatsDTO.builder()
                .highestScore(stats[0] != null ? (Integer) stats[0] : 0)
                .averageScore(stats[1] != null ? (Double) stats[1] : 0.0)
                .lowestScore(stats[2] != null ? (Integer) stats[2] : 0)
                .build();
    }

}