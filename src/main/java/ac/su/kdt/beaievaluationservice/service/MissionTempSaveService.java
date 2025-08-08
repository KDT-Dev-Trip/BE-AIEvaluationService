package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.MissionTempSave;
import ac.su.kdt.beaievaluationservice.kafka.event.MissionTempSaveEvent;
import ac.su.kdt.beaievaluationservice.repository.MissionTempSaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// 미션 임시 저장 서비스
// 사용자의 미션 진행 상황을 임시 저장하고 관리하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionTempSaveService {
    
    private final MissionTempSaveRepository missionTempSaveRepository;

    /**
     * 미션 임시 저장 처리
     * 기존 저장이 있으면 업데이트, 없으면 새로 생성
     */
    @Transactional
    public void processTempSave(MissionTempSaveEvent event) {
        String missionAttemptId = event.getMissionAttemptId();
        
        log.info("Processing temp save for missionAttemptId: {}, saveCount: {}", 
                missionAttemptId, event.getSaveCount());

        try {
            // 기존 임시 저장 데이터 조회
            Optional<MissionTempSave> existingTempSave = 
                missionTempSaveRepository.findByMissionAttemptId(missionAttemptId);

            if (existingTempSave.isPresent()) {
                // 기존 데이터 업데이트
                updateExistingTempSave(existingTempSave.get(), event);
            } else {
                // 새로운 임시 저장 데이터 생성
                createNewTempSave(event);
            }

            log.info("Successfully processed temp save for missionAttemptId: {}", missionAttemptId);

        } catch (Exception e) {
            log.error("Failed to process temp save for missionAttemptId: {}", missionAttemptId, e);
            throw new RuntimeException("임시 저장 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 기존 임시 저장 데이터 업데이트
     */
    private void updateExistingTempSave(MissionTempSave existingTempSave, MissionTempSaveEvent event) {
        // 이미 최종 완료된 경우 업데이트하지 않음
        if (existingTempSave.getIsFinalCompleted()) {
            log.warn("Attempted to update already completed mission: {}", event.getMissionAttemptId());
            return;
        }

        // 임시 저장 데이터 업데이트
        existingTempSave.setTempCode(event.getTempCode());
        existingTempSave.setSaveCount(event.getSaveCount());
        existingTempSave.setUpdatedAt(LocalDateTime.now());
        
        missionTempSaveRepository.save(existingTempSave);
        
        log.debug("Updated existing temp save for missionAttemptId: {}, saveCount: {}", 
                event.getMissionAttemptId(), event.getSaveCount());
    }

    /**
     * 새로운 임시 저장 데이터 생성
     */
    private void createNewTempSave(MissionTempSaveEvent event) {
        MissionTempSave tempSave = new MissionTempSave();
        tempSave.setUserId(event.getUserId());
        tempSave.setMissionId(event.getMissionId());
        tempSave.setMissionAttemptId(event.getMissionAttemptId());
        tempSave.setMissionType(event.getMissionType());
        tempSave.setMissionTitle(event.getMissionTitle());
        tempSave.setTempCode(event.getTempCode());
        tempSave.setSaveCount(event.getSaveCount());
        tempSave.setSaveStatus(MissionTempSave.SaveStatus.TEMP_SAVED);
        tempSave.setIsFinalCompleted(false);

        missionTempSaveRepository.save(tempSave);
        
        log.debug("Created new temp save for missionAttemptId: {}, saveCount: {}", 
                event.getMissionAttemptId(), event.getSaveCount());
    }

    /**
     * 임시 저장 데이터 조회
     */
    @Transactional(readOnly = true)
    public Optional<MissionTempSave> getTempSave(String missionAttemptId) {
        return missionTempSaveRepository.findByMissionAttemptId(missionAttemptId);
    }

    /**
     * 사용자의 미완료 임시 저장 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MissionTempSave> getIncompleteTempSaves(String userId) {
        return missionTempSaveRepository.findIncompleteByUserId(userId);
    }

    /**
     * 최종 완료 처리
     * 임시 저장 데이터를 최종 완료 상태로 변경
     */
    @Transactional
    public void markAsCompleted(String missionAttemptId) {
        Optional<MissionTempSave> tempSave = missionTempSaveRepository.findByMissionAttemptId(missionAttemptId);
        
        if (tempSave.isPresent()) {
            MissionTempSave save = tempSave.get();
            save.setIsFinalCompleted(true);
            save.setSaveStatus(MissionTempSave.SaveStatus.FINAL_COMPLETED);
            missionTempSaveRepository.save(save);
            
            log.info("Marked temp save as completed for missionAttemptId: {}", missionAttemptId);
        } else {
            log.warn("No temp save found to mark as completed for missionAttemptId: {}", missionAttemptId);
        }
    }

    /**
     * 임시 저장 데이터 삭제 (선택적 기능)
     */
    @Transactional
    public void deleteTempSave(String missionAttemptId) {
        missionTempSaveRepository.findByMissionAttemptId(missionAttemptId)
            .ifPresent(tempSave -> {
                missionTempSaveRepository.delete(tempSave);
                log.info("Deleted temp save for missionAttemptId: {}", missionAttemptId);
            });
    }

    /**
     * 임시 저장 통계 조회
     */
    @Transactional(readOnly = true)
    public TempSaveStats getTempSaveStats(String userId) {
        List<MissionTempSave> userTempSaves = missionTempSaveRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        
        long totalSaves = userTempSaves.size();
        long completedSaves = userTempSaves.stream().filter(MissionTempSave::getIsFinalCompleted).count();
        long incompleteSaves = totalSaves - completedSaves;

        return new TempSaveStats(totalSaves, completedSaves, incompleteSaves);
    }

    // 임시 저장 통계 DTO
    public static class TempSaveStats {
        public final long totalSaves;
        public final long completedSaves;
        public final long incompleteSaves;

        public TempSaveStats(long totalSaves, long completedSaves, long incompleteSaves) {
            this.totalSaves = totalSaves;
            this.completedSaves = completedSaves;
            this.incompleteSaves = incompleteSaves;
        }
    }
}