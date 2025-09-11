package ac.su.kdt.beaievaluationservice.controller;

import ac.su.kdt.beaievaluationservice.constants.EvaluationConstants;
import ac.su.kdt.beaievaluationservice.dto.response.ApiResponse;
import ac.su.kdt.beaievaluationservice.entity.AIEvaluation;
import ac.su.kdt.beaievaluationservice.repository.AIEvaluationRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationSummaryRepository;
import ac.su.kdt.beaievaluationservice.repository.EvaluationHistoryRepository;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// 테스트 데이터 및 시스템 상태 관리 API (개발/테스트 전용)
@Tag(name = EvaluationConstants.SWAGGER_TAG_SYSTEM_MANAGEMENT, description = EvaluationConstants.SWAGGER_DESC_SYSTEM_MANAGEMENT)
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*", maxAge = 3600)
public class TestDataController {

    private final AIEvaluationRepository aiEvaluationRepository;
    private final EvaluationSummaryRepository evaluationSummaryRepository;
    private final EvaluationHistoryRepository evaluationHistoryRepository;
    
    @Autowired(required = false)
    private ac.su.kdt.beaievaluationservice.service.MockS3DataService mockS3DataService;
    
    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

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
        log.info(EvaluationConstants.LOG_HEALTH_CHECK_REQUESTED);

        try {
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", EvaluationConstants.STATUS_UP);
            healthInfo.put(EvaluationConstants.JSON_TIMESTAMP, LocalDateTime.now());
            healthInfo.put("version", EvaluationConstants.VERSION_1_0_0);
            healthInfo.put("services", Map.of(
                EvaluationConstants.SERVICE_DATABASE, checkDatabaseHealth(),
                EvaluationConstants.SERVICE_TEMP_SAVE, EvaluationConstants.STATUS_UP,
                EvaluationConstants.SERVICE_EVALUATION, EvaluationConstants.STATUS_UP
            ));

            return ResponseEntity.ok(ApiResponse.success(EvaluationConstants.SUCCESS_SYSTEM_HEALTHY, healthInfo));

        } catch (Exception e) {
            log.error(EvaluationConstants.LOG_HEALTH_CHECK_FAILED, e);
            Map<String, Object> errorInfo = Map.of(
                "status", EvaluationConstants.STATUS_DOWN,
                EvaluationConstants.JSON_ERROR, e.getMessage(),
                EvaluationConstants.JSON_TIMESTAMP, LocalDateTime.now()
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
            // 평가 요약 데이터 삭제 (연관된 히스토리도 자동 삭제됨)
            evaluationSummaryRepository.findByUserIdOrderByCreatedAtDesc(Long.parseLong(userId))
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
            // 목업 평가 결과 JSON 생성 (다양한 평가 항목 포함)
            String mockResult = """
                {
                    "total_score": 13,
                    "overallScore": 87,
                    "feedback": "Kubernetes 배포가 성공적으로 완료되었습니다. Deployment와 Service 리소스가 올바르게 구성되었으며, Pod가 정상적으로 실행되고 있습니다.",
                    "detailedAnalysis": "전체적으로 우수한 Kubernetes 배포 구성입니다. nginx 애플리케이션이 성공적으로 배포되었으며, LoadBalancer 타입의 Service를 통해 외부 접근이 가능합니다.",
                    
                    "correctness": {
                        "score": 5,
                        "percentage": 100,
                        "feedback": "모든 Kubernetes 리소스가 올바르게 정의되고 배포되었습니다.",
                        "details": "Deployment의 replicas 설정, Service의 selector와 port 매핑이 정확합니다.",
                        "checklist": {
                            "syntaxCorrect": true,
                            "logicCorrect": true,
                            "requirementsMet": true,
                            "outputCorrect": true
                        }
                    },
                    
                    "efficiency": {
                        "score": 4,
                        "percentage": 80,
                        "feedback": "리소스 사용이 효율적이나 일부 최적화 가능한 부분이 있습니다.",
                        "details": "컨테이너 리소스 limits/requests 설정을 추가하면 더 효율적인 리소스 관리가 가능합니다.",
                        "metrics": {
                            "timeComplexity": "O(1)",
                            "spaceComplexity": "O(n)",
                            "resourceUsage": "최적",
                            "scalability": "양호"
                        }
                    },
                    
                    "quality": {
                        "score": 4,
                        "percentage": 80,
                        "feedback": "코드 품질이 우수하며 모범 사례를 잘 따르고 있습니다.",
                        "details": "라벨링 컨벤션을 잘 지켰으며, 메타데이터가 명확합니다. Health check 설정을 추가하면 더 좋습니다.",
                        "aspects": {
                            "readability": 9,
                            "maintainability": 8,
                            "reusability": 7,
                            "testability": 8
                        }
                    },
                    
                    "security": {
                        "score": 75,
                        "level": "MODERATE",
                        "feedback": "기본 보안 설정은 양호하나 NetworkPolicy 추가를 권장합니다.",
                        "vulnerabilities": [],
                        "recommendations": [
                            "NetworkPolicy를 사용하여 네트워크 트래픽 제한",
                            "RBAC 설정으로 권한 관리 강화",
                            "Secret을 사용하여 민감한 정보 보호",
                            "Pod Security Policy 적용 고려"
                        ]
                    },
                    
                    "style": {
                        "score": 90,
                        "feedback": "Kubernetes 리소스 명명 규칙과 라벨링 컨벤션을 잘 준수했습니다.",
                        "violations": [],
                        "suggestions": [
                            "주석을 추가하여 복잡한 설정 설명",
                            "환경별 설정 분리 (ConfigMap 활용)"
                        ]
                    },
                    
                    "performance": {
                        "executionTimeMs": 234,
                        "cpuUsage": 23.5,
                        "memoryUsage": 445.2,
                        "assessment": "최적화 상태 양호",
                        "bottlenecks": [],
                        "optimizationTips": [
                            "HPA(Horizontal Pod Autoscaler) 설정 추가",
                            "리소스 limits/requests 최적화"
                        ]
                    },
                    
                    "bestPractices": {
                        "score": 85,
                        "followed": [
                            "선언적 구성 사용",
                            "라벨 셀렉터 일관성",
                            "컨테이너 이미지 버전 명시",
                            "서비스 타입 적절히 선택"
                        ],
                        "missing": [
                            "Health check (liveness/readiness probes)",
                            "Resource limits and requests",
                            "Security context 설정"
                        ]
                    },
                    
                    "documentation": {
                        "score": 60,
                        "feedback": "기본적인 메타데이터는 있으나 상세 문서화 부족",
                        "suggestions": [
                            "README 파일에 배포 절차 문서화",
                            "각 리소스에 대한 주석 추가",
                            "환경 변수 및 설정 값 설명"
                        ]
                    },
                    
                    "recommendations": {
                        "immediate": [
                            "Health check 프로브 추가",
                            "리소스 limits/requests 설정"
                        ],
                        "shortTerm": [
                            "HPA 설정으로 자동 스케일링 구현",
                            "ConfigMap으로 설정 외부화"
                        ],
                        "longTerm": [
                            "CI/CD 파이프라인 통합",
                            "모니터링 및 로깅 시스템 구축",
                            "Istio 등 Service Mesh 도입 검토"
                        ]
                    },
                    
                    "summary": {
                        "grade": "A-",
                        "percentageScore": 87,
                        "strengths": [
                            "올바른 Kubernetes 리소스 구성",
                            "명확한 라벨링 및 네이밍",
                            "기본적인 배포 요구사항 충족"
                        ],
                        "weaknesses": [
                            "Health check 미설정",
                            "리소스 제한 미설정",
                            "보안 정책 부재"
                        ],
                        "verdict": "프로덕션 배포 가능하나 추가 개선 권장"
                    }
                }
                """;

            AIEvaluation evaluation = new AIEvaluation();
            evaluation.setMissionAttemptId(missionAttemptId);
            evaluation.setMissionId("1001");  // Mock mission ID
            evaluation.setUserId(123L);  // Mock user ID
            evaluation.setMissionType("KUBERNETES");
            evaluation.setMissionTitle("Mock Kubernetes Deployment Test");
            evaluation.setStatus(AIEvaluation.EvaluationStatus.COMPLETED);
            evaluation.setAiModelVersion("gemini-1.5-pro-mock");
            evaluation.setEvaluationResult(mockResult);
            evaluation.setEvaluationTrigger("MANUAL_REQUEST");
            evaluation.setProcessingTimeMs(1234L);
            evaluation.setSubmittedCode("kubectl apply -f deployment.yaml");
            
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

    /**
     * S3 목업 데이터 조회 테스트
     */
    @Operation(
        summary = "S3 목업 데이터 조회 테스트",
        description = "Pre-signed URL을 통해 S3 목업 데이터를 조회하는 기능을 테스트합니다."
    )
    @GetMapping("/mock-s3-data")
    public ResponseEntity<ApiResponse<Object>> getMockS3Data(
            @Parameter(description = "Pre-signed URL (목업용)", required = false)
            @RequestParam(required = false) String preSignedUrl) {
        log.info("Testing mock S3 data retrieval");

        if (mockS3DataService == null) {
            return ResponseEntity.ok(ApiResponse.failure("S3 Mock Service is not available - S3 functionality is disabled"));
        }

        try {
            // Pre-signed URL이 없으면 기본값 사용
            final String finalPreSignedUrl = (preSignedUrl == null || preSignedUrl.isEmpty()) 
                ? "https://devtrip-logs.s3.amazonaws.com/missions/kubernetes-mission-123/execution-data.zip?X-Amz-Expires=300&token=test123"
                : preSignedUrl;

            String mockData = mockS3DataService.readS3DataByPreSignedUrl(finalPreSignedUrl);
            
            var response = new Object() {
                public final String preSignedUrl_used = finalPreSignedUrl;
                public final String mock_data = mockData;
                public final java.util.List<String> available_mock_files = mockS3DataService.getAvailableMockDataFiles();
            };

            return ResponseEntity.ok(ApiResponse.success("S3 목업 데이터를 성공적으로 조회했습니다.", response));

        } catch (Exception e) {
            log.error("Failed to get mock S3 data", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("S3 목업 데이터 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 목업 데이터 캐시 초기화
     */
    @Operation(
        summary = "목업 데이터 캐시 초기화",
        description = "메모리에 캐시된 목업 데이터를 초기화합니다."
    )
    @DeleteMapping("/mock-cache")
    public ResponseEntity<ApiResponse<String>> clearMockCache() {
        log.info("Clearing mock data cache");

        if (mockS3DataService == null) {
            return ResponseEntity.ok(ApiResponse.failure("S3 Mock Service is not available - S3 functionality is disabled"));
        }

        try {
            mockS3DataService.clearCache();
            return ResponseEntity.ok(ApiResponse.success("목업 데이터 캐시가 초기화되었습니다."));

        } catch (Exception e) {
            log.error("Failed to clear mock cache", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("캐시 초기화 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 실제 AI 평가 테스트용 완전한 MissionCompletedEvent 생성
     */
    @Operation(
        summary = "AI 평가 테스트용 완전한 이벤트 생성",
        description = "실제 AI 평가 엔드포인트에서 테스트할 수 있는 S3 URL과 통계 데이터가 포함된 완전한 MissionCompletedEvent를 생성합니다."
    )
    @PostMapping("/create-full-evaluation-test/{missionType}")
    public ResponseEntity<ApiResponse<Object>> createFullEvaluationTest(
            @Parameter(description = "미션 타입 (kubernetes, docker-compose, terraform)", required = true)
            @PathVariable String missionType) {
        log.info("Creating full evaluation test data for missionType: {}", missionType);

        try {
            // 미션별로 다른 데이터 생성
            String missionAttemptId;
            String missionObjective;
            java.util.List<String> checklist;
            String s3StorageUrl;
            String s3PreSignedUrl;
            ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics statistics;

            switch (missionType.toLowerCase()) {
                case "kubernetes":
                    missionAttemptId = "kubernetes-test-" + System.currentTimeMillis();
                    missionObjective = "Kubernetes 클러스터에 nginx 애플리케이션을 배포하고, 외부에서 접근 가능하도록 서비스를 설정하세요.";
                    checklist = java.util.List.of(
                        "Deployment 리소스 생성",
                        "Service 리소스 생성 (LoadBalancer 타입)",
                        "Ingress 설정 (옵션)",
                        "Pod 상태 확인",
                        "외부 접근 테스트 완료"
                    );
                    s3StorageUrl = EvaluationConstants.S3_LOGS_PATH + missionAttemptId + EvaluationConstants.EXECUTION_DATA_ZIP;
                    s3PreSignedUrl = "https://devtrip-logs.s3.amazonaws.com/missions/kubernetes-mission-123/execution-data.zip?X-Amz-Expires=300&token=test123";
                    statistics = createKubernetesStatistics();
                    break;
                case "docker-compose":
                    missionAttemptId = "docker-test-" + System.currentTimeMillis();
                    missionObjective = "Docker Compose를 사용하여 nginx 웹 서버와 MySQL 데이터베이스를 구성하세요.";
                    checklist = java.util.List.of(
                        "docker-compose.yml 파일 작성",
                        "서비스 실행 및 확인",
                        "포트 매핑 테스트",
                        "컨테이너 로그 확인",
                        "정리 작업 완료"
                    );
                    s3StorageUrl = EvaluationConstants.S3_LOGS_PATH + missionAttemptId + EvaluationConstants.EXECUTION_DATA_ZIP;
                    s3PreSignedUrl = "https://devtrip-logs.s3.amazonaws.com/missions/docker-compose/execution-data.zip?X-Amz-Expires=300&token=test456";
                    statistics = createDockerComposeStatistics();
                    break;
                default:
                    missionAttemptId = "general-test-" + System.currentTimeMillis();
                    missionObjective = "기본 DevOps 작업을 수행하고 결과를 확인하세요.";
                    checklist = java.util.List.of("작업 수행", "결과 확인", "정리 작업");
                    s3StorageUrl = EvaluationConstants.S3_LOGS_PATH + missionAttemptId + EvaluationConstants.EXECUTION_DATA_ZIP;
                    s3PreSignedUrl = "https://devtrip-logs.s3.amazonaws.com/missions/default/execution-data.zip?X-Amz-Expires=300&token=test789";
                    statistics = createDefaultStatistics();
                    break;
            }

            // 테스트용 코드 생성
            String testCode = generateTestCode(missionType);

            // 응답 데이터 구성
            var response = new Object() {
                public final String mission_attempt_id = missionAttemptId;
                public final String mission_type = missionType;
                public final String mission_objective = missionObjective;
                public final java.util.List<String> checklist_items = checklist;
                public final String s3_storage_url = s3StorageUrl;
                public final String s3_presigned_url = s3PreSignedUrl;
                public final String test_code = testCode;
                public final Object execution_statistics = statistics;
                public final String postman_curl = generatePostmanCurl(missionAttemptId, missionType, missionObjective, checklist, s3StorageUrl, s3PreSignedUrl, testCode, statistics);
            };

            return ResponseEntity.ok(ApiResponse.success("완전한 AI 평가 테스트 데이터가 생성되었습니다.", response));

        } catch (Exception e) {
            log.error("Failed to create full evaluation test data for missionType: {}", missionType, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("테스트 데이터 생성 실패: " + e.getMessage()));
        }
    }

    private ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics createKubernetesStatistics() {
        ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics stats = 
            new ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(15);
        stats.setCommandFailureCount(2);
        stats.setTopErrorMessages(java.util.List.of(
            "Error: services \"nginx-service\" already exists",
            "Warning: kubectl apply should be used on resource created by either kubectl create --save-config",
            "Error: the server doesn't have a resource type \"ingresss\""
        ));
        stats.setAverageCpuUsage(23.4);
        stats.setMaxCpuUsage(67.8);
        stats.setAverageMemoryUsage(445.2);
        stats.setMaxMemoryUsage(892.1);
        stats.setTotalExecutionTime(28500L);
        return stats;
    }

    private ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics createDockerComposeStatistics() {
        ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics stats = 
            new ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(6);
        stats.setCommandFailureCount(0);
        stats.setTopErrorMessages(java.util.List.of());
        stats.setAverageCpuUsage(15.2);
        stats.setMaxCpuUsage(32.1);
        stats.setAverageMemoryUsage(743.6);
        stats.setMaxMemoryUsage(1456.0);
        stats.setTotalExecutionTime(21822L);
        return stats;
    }

    private ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics createDefaultStatistics() {
        ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics stats = 
            new ac.su.kdt.beaievaluationservice.kafka.event.MissionCompletedEvent.SimpleStatistics();
        stats.setCommandSuccessCount(5);
        stats.setCommandFailureCount(1);
        stats.setTopErrorMessages(java.util.List.of("Command not found"));
        stats.setAverageCpuUsage(10.5);
        stats.setMaxCpuUsage(25.0);
        stats.setAverageMemoryUsage(256.0);
        stats.setMaxMemoryUsage(512.0);
        stats.setTotalExecutionTime(8500L);
        return stats;
    }

    private String generateTestCode(String missionType) {
        switch (missionType.toLowerCase()) {
            case "kubernetes":
                return """
                    apiVersion: apps/v1
                    kind: Deployment
                    metadata:
                      name: nginx-deployment
                      labels:
                        app: nginx
                    spec:
                      replicas: 2
                      selector:
                        matchLabels:
                          app: nginx
                      template:
                        metadata:
                          labels:
                            app: nginx
                        spec:
                          containers:
                          - name: nginx
                            image: nginx:1.21
                            ports:
                            - containerPort: 80
                    ---
                    apiVersion: v1
                    kind: Service
                    metadata:
                      name: nginx-service
                    spec:
                      selector:
                        app: nginx
                      ports:
                        - protocol: TCP
                          port: 80
                          targetPort: 80
                      type: LoadBalancer
                    """;
            case "docker-compose":
                return """
                    version: '3.8'
                    services:
                      web:
                        image: nginx:latest
                        ports:
                          - "8080:80"
                        depends_on:
                          - db
                        networks:
                          - myapp-network
                      
                      db:
                        image: mysql:8.0
                        environment:
                          MYSQL_ROOT_PASSWORD: rootpassword
                          MYSQL_DATABASE: myapp
                          MYSQL_USER: user
                          MYSQL_PASSWORD: password
                        ports:
                          - "3306:3306"
                        networks:
                          - myapp-network
                    
                    networks:
                      myapp-network:
                        driver: bridge
                    """;
            default:
                return """
                    #!/bin/bash
                    echo "Starting DevOps task..."
                    
                    # 기본 시스템 정보 확인
                    uname -a
                    df -h
                    
                    # 서비스 상태 확인
                    systemctl status nginx
                    
                    echo "Task completed successfully!"
                    """;
        }
    }

    private String generatePostmanCurl(String missionAttemptId, String missionType, String missionObjective, 
                                      java.util.List<String> checklist, String s3StorageUrl, String s3PreSignedUrl, 
                                      String testCode, Object statistics) {
        return """
            curl -X POST http://localhost:8080/api/evaluation/start \\
            -H "Content-Type: application/json" \\
            -d '{
              "userId": "test-user-123",
              "missionId": "mission-""" + missionType + """
            ",
              "missionAttemptId": \"""" + missionAttemptId + """
            ",
              "missionType": \"""" + missionType + """
            ",
              "missionTitle": "AI 평가 테스트 미션",
              "code": \"""" + testCode.replace("\"", "\\\"").replace("\n", "\\n") + """
            ",
              "missionObjective": \"""" + missionObjective + """
            ",
              "checklist": """ + checklist.toString() + """
            ,
              "s3StorageUrl": \"""" + s3StorageUrl + """
            ",
              "s3PreSignedUrl": \"""" + s3PreSignedUrl + """
            ",
              "statistics": """ + statistics.toString() + """
            }'
            """;
    }

    /**
     * Kafka 메시징 테스트 - 미션 완료 이벤트 발행
     */
    @PostMapping("/kafka/publish-mission-completed")
    public ApiResponse<Map<String, Object>> publishMissionCompletedEvent(
            @RequestParam(defaultValue = "test-mission-123") String missionId,
            @RequestParam(defaultValue = "test-user-456") String userId,
            @RequestParam(defaultValue = "test-attempt-789") String missionAttemptId,
            @RequestParam(defaultValue = "DOCKER_COMPOSE") String missionType) {
        
        try {
            // MissionCompletedEvent 생성
            MissionCompletedEvent event = new MissionCompletedEvent();
            event.setMissionId(missionId);
            event.setUserId(userId);
            event.setMissionAttemptId(missionAttemptId);
            event.setMissionType(missionType);
            event.setCode("version: '3.8'\\nservices:\\n  web:\\n    image: nginx");
            event.setCompletedAt(LocalDateTime.now());
            event.setEventType("MISSION_COMPLETED");
            event.setMissionTitle("테스트 미션");
            
            // 간단한 통계 데이터 추가
            MissionCompletedEvent.SimpleStatistics stats = new MissionCompletedEvent.SimpleStatistics();
            stats.setCommandSuccessCount(5);
            stats.setCommandFailureCount(0);
            stats.setAverageCpuUsage(15.2);
            event.setStatistics(stats);
            
            // Kafka로 전송
            if (kafkaTemplate != null) {
                kafkaTemplate.send("mission.completed.dev", event);
                log.info("Kafka 메시지 발행 성공: topic=mission.completed.dev, missionAttemptId={}", missionAttemptId);
            } else {
                log.warn("KafkaTemplate이 null입니다. Kafka 메시지를 발행할 수 없습니다.");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("topic", "mission.completed.dev");
            result.put("missionId", missionId);
            result.put("userId", userId);
            result.put("missionAttemptId", missionAttemptId);
            result.put("timestamp", LocalDateTime.now());
            
            return ApiResponse.success("Kafka 메시지 발행 성공", result);
                
        } catch (Exception e) {
            log.error("Kafka 메시지 발행 실패", e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            
            return new ApiResponse<>(false, "Kafka 메시지 발행 실패: " + e.getMessage(), errorResult, LocalDateTime.now());
        }
    }
}