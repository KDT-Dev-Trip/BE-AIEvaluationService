package ac.su.kdt.beaievaluationservice.controller;

import ac.su.kdt.beaievaluationservice.dto.request.TempSaveRequest;
import ac.su.kdt.beaievaluationservice.dto.response.ApiResponse;
import ac.su.kdt.beaievaluationservice.dto.response.TempSaveResponse;
import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionTempSaveEvent;
import ac.su.kdt.beaievaluationservice.service.MissionTempSaveService;
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

// 임시 저장 REST API 컨트롤러 (테스트/개발용)
@Tag(name = "임시 저장", description = "미션 진행 중 코드 임시 저장 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/temp-save")
@RequiredArgsConstructor
public class TempSaveController {

    private final MissionTempSaveService missionTempSaveService;

    /**
     * 임시 저장 생성/업데이트
     */
    @Operation(
        summary = "임시 저장 생성/업데이트",
        description = "사용자가 미션 진행 중 작성한 코드를 임시 저장하거나 업데이트합니다. " +
                     "동일한 missionAttemptId로 요청하면 기존 데이터를 업데이트합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "임시 저장 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 요청 (Validation 실패)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TempSaveResponse>> saveTempCode(@Valid @RequestBody TempSaveRequest request) {
        log.info("Temp save request received for missionAttemptId: {}, saveCount: {}", 
                request.getMissionAttemptId(), request.getSaveCount());

        try {
            // Request를 Event로 변환
            MissionTempSaveEvent event = convertToEvent(request);
            
            // 임시 저장 처리
            missionTempSaveService.processTempSave(event);
            
            // 저장된 데이터 조회
            Optional<MissionTempSave> savedTempSave = missionTempSaveService.getTempSave(request.getMissionAttemptId());
            
            if (savedTempSave.isPresent()) {
                TempSaveResponse response = convertToResponse(savedTempSave.get());
                return ResponseEntity.ok(ApiResponse.success("임시 저장이 완료되었습니다.", response));
            } else {
                return ResponseEntity.ok(ApiResponse.failure("임시 저장 후 데이터 조회에 실패했습니다."));
            }

        } catch (Exception e) {
            log.error("Failed to save temp code for missionAttemptId: {}", request.getMissionAttemptId(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("임시 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 임시 저장 데이터 조회 (단건)
     */
    @GetMapping("/{missionAttemptId}")
    public ResponseEntity<ApiResponse<TempSaveResponse>> getTempSave(@PathVariable String missionAttemptId) {
        log.info("Get temp save request for missionAttemptId: {}", missionAttemptId);

        try {
            Optional<MissionTempSave> tempSave = missionTempSaveService.getTempSave(missionAttemptId);
            
            if (tempSave.isPresent()) {
                TempSaveResponse response = convertToResponse(tempSave.get());
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                return ResponseEntity.status(404)
                        .body(ApiResponse.failure("해당 미션 시도의 임시 저장 데이터를 찾을 수 없습니다."));
            }

        } catch (Exception e) {
            log.error("Failed to get temp save for missionAttemptId: {}", missionAttemptId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("임시 저장 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 사용자별 미완료 임시 저장 목록 조회
     */
    @GetMapping("/user/{userId}/incomplete")
    public ResponseEntity<ApiResponse<List<TempSaveResponse>>> getIncompleteTempSaves(@PathVariable String userId) {
        log.info("Get incomplete temp saves for userId: {}", userId);

        try {
            List<MissionTempSave> incompleteSaves = missionTempSaveService.getIncompleteTempSaves(userId);
            
            List<TempSaveResponse> responses = incompleteSaves.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(
                    String.format("미완료 임시 저장 %d개를 조회했습니다.", responses.size()), 
                    responses));

        } catch (Exception e) {
            log.error("Failed to get incomplete temp saves for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("미완료 임시 저장 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 사용자별 임시 저장 통계 조회
     */
    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<ApiResponse<MissionTempSaveService.TempSaveStats>> getTempSaveStats(@PathVariable String userId) {
        log.info("Get temp save stats for userId: {}", userId);

        try {
            MissionTempSaveService.TempSaveStats stats = missionTempSaveService.getTempSaveStats(userId);
            return ResponseEntity.ok(ApiResponse.success("임시 저장 통계를 조회했습니다.", stats));

        } catch (Exception e) {
            log.error("Failed to get temp save stats for userId: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("임시 저장 통계 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 임시 저장을 최종 완료 상태로 변경 (테스트용)
     */
    @PutMapping("/{missionAttemptId}/complete")
    public ResponseEntity<ApiResponse<String>> markAsCompleted(@PathVariable String missionAttemptId) {
        log.info("Mark temp save as completed for missionAttemptId: {}", missionAttemptId);

        try {
            missionTempSaveService.markAsCompleted(missionAttemptId);
            return ResponseEntity.ok(ApiResponse.success("임시 저장이 최종 완료 상태로 변경되었습니다."));

        } catch (Exception e) {
            log.error("Failed to mark temp save as completed for missionAttemptId: {}", missionAttemptId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("상태 변경 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 임시 저장 삭제 (테스트용)
     */
    @DeleteMapping("/{missionAttemptId}")
    public ResponseEntity<ApiResponse<String>> deleteTempSave(@PathVariable String missionAttemptId) {
        log.info("Delete temp save for missionAttemptId: {}", missionAttemptId);

        try {
            missionTempSaveService.deleteTempSave(missionAttemptId);
            return ResponseEntity.ok(ApiResponse.success("임시 저장 데이터가 삭제되었습니다."));

        } catch (Exception e) {
            log.error("Failed to delete temp save for missionAttemptId: {}", missionAttemptId, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.failure("임시 저장 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    // Helper methods
    private MissionTempSaveEvent convertToEvent(TempSaveRequest request) {
        MissionTempSaveEvent event = new MissionTempSaveEvent();
        event.setEventType("MISSION_TEMP_SAVE");
        event.setUserId(request.getUserId());
        event.setMissionId(request.getMissionId());
        event.setMissionAttemptId(request.getMissionAttemptId());
        event.setMissionType(request.getMissionType());
        event.setMissionTitle(request.getMissionTitle());
        event.setTempCode(request.getTempCode());
        event.setSaveCount(request.getSaveCount());
        event.setSaveReason(request.getSaveReason());
        event.setSavedAt(LocalDateTime.now());
        return event;
    }

    private TempSaveResponse convertToResponse(MissionTempSave tempSave) {
        return TempSaveResponse.builder()
                .id(tempSave.getId())
                .userId(tempSave.getUserId())
                .missionId(tempSave.getMissionId())
                .missionAttemptId(tempSave.getMissionAttemptId())
                .missionType(tempSave.getMissionType())
                .missionTitle(tempSave.getMissionTitle())
                .saveCount(tempSave.getSaveCount())
                .saveStatus(tempSave.getSaveStatus().name())
                .isFinalCompleted(tempSave.getIsFinalCompleted())
                .tempCodeLength(tempSave.getTempCode() != null ? tempSave.getTempCode().length() : 0)
                .createdAt(tempSave.getCreatedAt())
                .updatedAt(tempSave.getUpdatedAt())
                .build();
    }
}