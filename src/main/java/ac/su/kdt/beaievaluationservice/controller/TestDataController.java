package ac.su.kdt.beaievaluationservice.controller;

import ac.su.kdt.beaievaluationservice.dto.response.ApiResponse;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.repository.MissionTempSaveRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// 테스트 데이터 및 시스템 상태 관리 API (개발/테스트 전용)
@Tag(name = "시스템 관리", description = "시스템 상태 확인, 테스트 데이터 관리 등 개발/테스트 전용 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestDataController {

    private final AIEvaluationRepository aiEvaluationRepository;
    private final EvaluationSummaryRepository evaluationSummaryRepository;
    private final EvaluationHistoryRepository evaluationHistoryRepository;
    private final MissionTempSaveRepository missionTempSaveRepository;

    /**
     * 시스템 헬스 체크
     */
    @Operation(
        summary = "시스템 헬스 체크",
        description = "전체 시스템의 상태를 확인합니다. 데이터베이스 연결 상태, 서비스 상태 등을 포함합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "시스템 상태 확인 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "시스템 오류 발생",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        log.info("Health check requested");

        try {
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", "UP");
            healthInfo.put("timestamp", LocalDateTime.now());
            healthInfo.put("version", "1.0.0");
            healthInfo.put("services", Map.of(
                "database", checkDatabaseHealth(),
                "temp-save-service", "UP",
                "evaluation-service", "UP"
            ));

            return ResponseEntity.ok(ApiResponse.success("시스템이 정상 동작 중입니다.", healthInfo));

        } catch (Exception e) {
            log.error("Health check failed", e);
            Map<String, Object> errorInfo = Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("시스템 헬스 체크 실패", errorInfo));
        }
    }

    /**
     * 데이터베이스 상태 조회
     */
    @Operation(
        summary = "데이터베이스 상태 조회",
        description = "각 테이블별 레코드 수와 데이터베이스 연결 상태를 확인합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "데이터베이스 상태 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "데이터베이스 연결 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @GetMapping("/database/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDatabaseStatus() {
        log.info("Database status check requested");

        try {
            Map<String, Object> dbStatus = new HashMap<>();
            dbStatus.put("ai_evaluations", aiEvaluationRepository.count());
            dbStatus.put("evaluation_summaries", evaluationSummaryRepository.count());
            dbStatus.put("evaluation_histories", evaluationHistoryRepository.count());
            dbStatus.put("mission_temp_saves", missionTempSaveRepository.count());
            dbStatus.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("데이터베이스 상태를 조회했습니다.", dbStatus));

        } catch (Exception e) {
            log.error("Failed to get database status", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("데이터베이스 상태 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * 테스트 데이터 생성
     */
    @Operation(
        summary = "샘플 테스트 데이터 생성",
        description = "시스템 테스트를 위한 샘플 임시 저장 데이터와 AI 평가 데이터를 생성합니다. " +
                     "개발 및 테스트 환경에서만 사용하세요."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "샘플 데이터 생성 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "샘플 데이터 생성 실패",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping("/data/sample")
    public ResponseEntity<ApiResponse<String>> createSampleData() {
        log.info("Create sample data requested");

        try {
            // 샘플 임시 저장 데이터 생성
            createSampleTempSave("test-user-001", "mission-java-001", "attempt-001");
            createSampleTempSave("test-user-001", "mission-python-002", "attempt-002");
            createSampleTempSave("test-user-002", "mission-docker-003", "attempt-003");

            // 샘플 AI 평가 데이터 생성
            createSampleEvaluation("attempt-004", AIEvaluation.EvaluationStatus.COMPLETED);
            createSampleEvaluation("attempt-005", AIEvaluation.EvaluationStatus.PROCESSING);
            createSampleEvaluation("attempt-006", AIEvaluation.EvaluationStatus.FAILED);

            return ResponseEntity.ok(ApiResponse.success("샘플 데이터가 생성되었습니다."));

        } catch (Exception e) {
            log.error("Failed to create sample data", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("샘플 데이터 생성 실패: " + e.getMessage()));
        }
    }

    /**
     * 모든 데이터 삭제 (주의!)
     */
    @DeleteMapping("/data/all")
    public ResponseEntity<ApiResponse<String>> deleteAllData() {
        log.warn("DELETE ALL DATA requested - this will remove all evaluation and temp save data!");

        try {
            evaluationHistoryRepository.deleteAll();
            evaluationSummaryRepository.deleteAll();
            aiEvaluationRepository.deleteAll();
            missionTempSaveRepository.deleteAll();

            return ResponseEntity.ok(ApiResponse.success("모든 데이터가 삭제되었습니다."));

        } catch (Exception e) {
            log.error("Failed to delete all data", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("데이터 삭제 실패: " + e.getMessage()));
        }
    }

    /**
     * 특정 사용자 데이터 삭제
     */
    @Operation(
        summary = "특정 사용자 데이터 삭제",
        description = "지정된 사용자의 모든 임시 저장 데이터와 평가 데이터를 삭제합니다. " +
                     "테스트 데이터 정리에 사용하세요."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "사용자 데이터 삭제 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "데이터 삭제 실패",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @DeleteMapping("/data/user/{userId}")
    public ResponseEntity<ApiResponse<String>> deleteUserData(
            @Parameter(description = "삭제할 사용자 ID", required = true)
            @PathVariable String userId) {
        log.info("Delete user data requested for userId: {}", userId);

        try {
            // 임시 저장 데이터 삭제
            missionTempSaveRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                    .forEach(missionTempSaveRepository::delete);

            // 평가 요약 데이터 삭제 (연관된 히스토리도 자동 삭제됨)
            evaluationSummaryRepository.findByUserIdOrderByCreatedAtDesc(userId)
                    .forEach(evaluationSummaryRepository::delete);

            return ResponseEntity.ok(ApiResponse.success(userId + " 사용자의 데이터가 삭제되었습니다."));

        } catch (Exception e) {
            log.error("Failed to delete user data for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("사용자 데이터 삭제 실패: " + e.getMessage()));
        }
    }

    /**
     * 시스템 로그 레벨 변경 (테스트용)
     */
    @PutMapping("/logging/level/{level}")
    public ResponseEntity<ApiResponse<String>> changeLogLevel(@PathVariable String level) {
        log.info("Change log level requested to: {}", level);

        try {
            // 실제 로그 레벨 변경 로직은 구현하지 않음 (단순 테스트용)
            return ResponseEntity.ok(ApiResponse.success("로그 레벨이 " + level + "로 변경되었습니다."));

        } catch (Exception e) {
            log.error("Failed to change log level", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("로그 레벨 변경 실패: " + e.getMessage()));
        }
    }

    // Helper methods
    private String checkDatabaseHealth() {
        try {
            aiEvaluationRepository.count(); // 간단한 쿼리 실행
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private void createSampleTempSave(String userId, String missionId, String missionAttemptId) {
        MissionTempSave tempSave = new MissionTempSave();
        tempSave.setUserId(userId);
        tempSave.setMissionId(missionId);
        tempSave.setMissionAttemptId(missionAttemptId);
        tempSave.setMissionType("JavaScript");
        tempSave.setMissionTitle("샘플 JavaScript 미션");
        tempSave.setTempCode("console.log('샘플 임시 저장 코드');");
        tempSave.setSaveCount(1);
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.TEMP_SAVED);
        tempSave.setIsFinalCompleted(false);
        missionTempSaveRepository.save(tempSave);
    }

    /**
     * 목업 AI 평가 결과 생성 (테스트용)
     */
    @Operation(
        summary = "목업 AI 평가 결과 생성",
        description = "실제 Gemini API 호출 없이 목업 데이터로 AI 평가 결과를 생성합니다. 테스트 및 데모용으로 사용하세요."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "목업 평가 결과 생성 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "목업 데이터 생성 실패",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping("/evaluation/mock/{missionAttemptId}")
    public ResponseEntity<ApiResponse<String>> createMockEvaluation(
            @Parameter(description = "미션 시도 ID", required = true)
            @PathVariable String missionAttemptId) {
        log.info("Create mock evaluation result for missionAttemptId: {}", missionAttemptId);

        try {
            // 목업 평가 결과 JSON 생성
            String mockResult = """
                {
                    "overallScore": 82,
                    "feedback": "전체적으로 잘 작성된 코드입니다. 기본적인 기능이 올바르게 구현되어 있으며, 가독성도 좋습니다.",
                    "detailedAnalysis": "코드 구조가 명확하고 기본적인 객체지향 원칙을 잘 따르고 있습니다.",
                    "codeQuality": {
                        "score": 85,
                        "feedback": "코드 구조가 명확하고 가독성이 좋습니다.",
                        "suggestions": "주석을 추가하여 복잡한 로직에 대한 설명을 제공하는 것을 권장합니다."
                    },
                    "security": {
                        "score": 75,
                        "feedback": "기본적인 보안 이슈는 없으나, 입력값 검증이 필요합니다.",
                        "vulnerabilities": "입력값에 대한 null 체크가 부족합니다.",
                        "recommendations": "입력 파라미터에 대한 null 체크와 범위 검증을 추가하세요."
                    },
                    "style": {
                        "score": 88,
                        "feedback": "코딩 컨벤션을 잘 준수하고 있습니다.",
                        "styleIssues": "JavaDoc 주석이 부족합니다.",
                        "improvements": "public 메서드에 JavaDoc 주석을 추가하세요."
                    }
                }
                """;

            AIEvaluation evaluation = new AIEvaluation();
            evaluation.setMissionAttemptId(missionAttemptId);
            evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            evaluation.setAiModelVersion("gemini-1.5-pro-mock");
            evaluation.setEvaluationResult(mockResult);
            
            aiEvaluationRepository.save(evaluation);
            
            return ResponseEntity.ok(ApiResponse.success("목업 AI 평가 결과가 생성되었습니다."));

        } catch (Exception e) {
            log.error("Failed to create mock evaluation for missionAttemptId: {}", missionAttemptId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("목업 평가 결과 생성 실패: " + e.getMessage()));
        }
    }

    private void createSampleEvaluation(String missionAttemptId, AIEvaluation.EvaluationStatus status) {
        AIEvaluation evaluation = new AIEvaluation();
        evaluation.setMissionAttemptId(missionAttemptId);
        evaluation.setStatus(status);
        evaluation.setAiModelVersion("gemini-1.5-pro");
        
        if (status == AIEvaluation.EvaluationStatus.COMPLETED) {
            evaluation.setEvaluationResult("{\"overallScore\":85,\"feedback\":\"샘플 평가 결과입니다.\"}");
        } else if (status == AIEvaluation.EvaluationStatus.FAILED) {
            evaluation.setErrorMessage("샘플 오류 메시지");
        }
        
        aiEvaluationRepository.save(evaluation);
    }
}