package ac.su.kdt.beaievaluationservice.service;

import ac.su.kdt.beaievaluationservice.entity.MissionS3Storage;
import ac.su.kdt.beaievaluationservice.repository.MissionS3StorageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 미션 S3 저장소 관리 서비스
 * S3 저장소 주소는 RDS에 저장하고, Pre-signed URL은 필요할 때마다 발급받음
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionS3StorageService {
    
    private final MissionS3StorageRepository missionS3StorageRepository;
    
    @Autowired(required = false)
    private S3PreSignedUrlService s3PreSignedUrlService;
    
    /**
     * missionAttemptId로 S3 저장소 정보 조회
     * 
     * @param missionAttemptId 미션 시도 ID
     * @return S3 저장소 정보 (Optional)
     */
    public Optional<MissionS3Storage> getS3StorageInfo(String missionAttemptId) {
        log.debug("Retrieving S3 storage info for missionAttemptId: {}", missionAttemptId);
        return missionS3StorageRepository.findByMissionAttemptId(missionAttemptId);
    }
    
    /**
     * missionAttemptId로 S3 저장소 주소 조회 (핵심 메서드)
     * 
     * @param missionAttemptId 미션 시도 ID
     * @return S3 저장소 주소 (Optional)
     */
    public Optional<String> getS3StorageUrl(String missionAttemptId) {
        log.info("Querying S3 storage URL from RDS for missionAttemptId: {}", missionAttemptId);
        
        Optional<MissionS3Storage> storageInfo = missionS3StorageRepository.findByMissionAttemptId(missionAttemptId);
        
        if (storageInfo.isPresent()) {
            String s3StorageUrl = storageInfo.get().getS3StorageUrl();
            log.info("Found S3 storage URL in RDS for missionAttemptId: {}, URL: {}", missionAttemptId, s3StorageUrl);
            return Optional.of(s3StorageUrl);
        } else {
            log.warn("No S3 storage URL found in RDS for missionAttemptId: {}", missionAttemptId);
            return Optional.empty();
        }
    }
    
    /**
     * 필요시 Pre-signed URL 동적 발급 (5분 만료)
     * RDS에 저장하지 않고 매번 새로 발급
     * 
     * @param missionAttemptId 미션 시도 ID
     * @return Pre-signed URL (Optional)
     */
    public Optional<String> generatePreSignedUrl(String missionAttemptId) {
        log.info("Generating pre-signed URL for missionAttemptId: {}", missionAttemptId);
        
        if (s3PreSignedUrlService == null) {
            log.warn("S3PreSignedUrlService is not available - S3 functionality is disabled");
            return Optional.empty();
        }
        
        Optional<MissionS3Storage> storageInfo = missionS3StorageRepository.findByMissionAttemptId(missionAttemptId);
        
        if (storageInfo.isPresent()) {
            MissionS3Storage storage = storageInfo.get();
            
            try {
                // S3 버킷명과 객체 키를 사용하여 Pre-signed URL 발급
                String preSignedUrl = s3PreSignedUrlService.generatePreSignedUrl(
                    storage.getBucketName(), 
                    storage.getObjectKeyPrefix()
                );
                
                log.info("Successfully generated pre-signed URL for missionAttemptId: {}", missionAttemptId);
                return Optional.of(preSignedUrl);
                
            } catch (Exception e) {
                log.error("Failed to generate pre-signed URL for missionAttemptId: {}", missionAttemptId, e);
                return Optional.empty();
            }
        } else {
            log.warn("Cannot generate pre-signed URL - no S3 storage info found for missionAttemptId: {}", missionAttemptId);
            return Optional.empty();
        }
    }
    
    /**
     * S3 저장소 정보를 RDS에 저장 (S3 저장소 주소만 저장, Pre-signed URL은 저장하지 않음)
     * 
     * @param missionAttemptId 미션 시도 ID
     * @param userId 사용자 ID
     * @param missionId 미션 ID
     * @param s3StorageUrl S3 저장소 주소
     * @param bucketName S3 버킷명
     * @param objectKey S3 객체 키
     * @return 저장된 S3 저장소 정보
     */
    @Transactional
    public MissionS3Storage saveS3StorageInfo(String missionAttemptId, String userId, String missionId,
                                             String s3StorageUrl, String bucketName, String objectKey) {
        log.info("Saving S3 storage info to RDS for missionAttemptId: {}, URL: {}", missionAttemptId, s3StorageUrl);
        
        // 기존 정보가 있는지 확인
        Optional<MissionS3Storage> existingStorage = missionS3StorageRepository.findByMissionAttemptId(missionAttemptId);
        
        MissionS3Storage storage;
        if (existingStorage.isPresent()) {
            // 기존 정보 업데이트
            storage = existingStorage.get();
            storage.setS3StorageUrl(s3StorageUrl);
            storage.setBucketName(bucketName);
            storage.setObjectKeyPrefix(objectKey);
            log.info("Updated existing S3 storage info for missionAttemptId: {}", missionAttemptId);
        } else {
            // 새로운 정보 생성
            storage = new MissionS3Storage();
            storage.setMissionAttemptId(missionAttemptId);
            storage.setUserId(Long.parseLong(userId));
            storage.setMissionId(missionId);
            storage.setS3StorageUrl(s3StorageUrl);
            storage.setBucketName(bucketName);
            storage.setObjectKeyPrefix(objectKey);
            log.info("Created new S3 storage info for missionAttemptId: {}", missionAttemptId);
        }
        
        MissionS3Storage savedStorage = missionS3StorageRepository.save(storage);
        log.info("Successfully saved S3 storage info to RDS for missionAttemptId: {}", missionAttemptId);
        
        return savedStorage;
    }
    
    /**
     * S3 저장소 정보 존재 여부 확인
     */
    public boolean hasS3StorageInfo(String missionAttemptId) {
        return missionS3StorageRepository.existsByMissionAttemptId(missionAttemptId);
    }
    
    /**
     * S3 기능 사용 가능 여부 확인
     */
    public boolean isS3Enabled() {
        return s3PreSignedUrlService != null;
    }
}