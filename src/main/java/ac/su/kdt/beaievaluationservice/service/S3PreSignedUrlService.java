package ac.su.kdt.beaievaluationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.net.URL;

/**
 * S3 Pre-signed URL 동적 발급 서비스
 * Pre-signed URL은 RDS에 저장하지 않고 필요할 때마다 발급받음 (5분 만료)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true", matchIfMissing = false)
public class S3PreSignedUrlService {
    
    @Value("${aws.s3.region:ap-northeast-2}")
    private String awsRegion;
    
    @Value("${aws.s3.access-key}")
    private String awsAccessKey;
    
    @Value("${aws.s3.secret-key}")
    private String awsSecretKey;
    
    private static final Duration PRE_SIGNED_URL_EXPIRATION = Duration.ofMinutes(5); // 5분 만료
    
    /**
     * S3 버킷과 객체 키를 기반으로 Pre-signed URL을 동적으로 발급
     * 
     * @param bucketName S3 버킷명
     * @param objectKey S3 객체 키
     * @return Pre-signed URL (5분 만료)
     */
    public String generatePreSignedUrl(String bucketName, String objectKey) {
        log.info("Generating pre-signed URL for bucket: {}, key: {}", bucketName, objectKey);
        
        try {
            // 실제 AWS SDK를 사용한 Pre-signed URL 생성 로직
            // 현재는 목업 구현으로 대체 (실제 환경에서는 AWS SDK 사용)
            String preSignedUrl = generateMockPreSignedUrl(bucketName, objectKey);
            
            log.info("Successfully generated pre-signed URL for bucket: {}, key: {}", bucketName, objectKey);
            return preSignedUrl;
            
        } catch (Exception e) {
            log.error("Failed to generate pre-signed URL for bucket: {}, key: {}", bucketName, objectKey, e);
            throw new RuntimeException("Failed to generate pre-signed URL", e);
        }
    }
    
    /**
     * S3 저장소 URL에서 버킷명과 객체 키를 추출하여 Pre-signed URL 생성
     * 
     * @param s3StorageUrl S3 저장소 URL (예: s3://bucket-name/path/to/object)
     * @return Pre-signed URL
     */
    public String generatePreSignedUrlFromStorageUrl(String s3StorageUrl) {
        if (s3StorageUrl == null || !s3StorageUrl.startsWith("s3://")) {
            throw new IllegalArgumentException("Invalid S3 storage URL format: " + s3StorageUrl);
        }
        
        try {
            // s3://bucket-name/path/to/object 형태에서 버킷명과 객체 키 추출
            String withoutProtocol = s3StorageUrl.substring(5); // "s3://" 제거
            int firstSlashIndex = withoutProtocol.indexOf('/');
            
            if (firstSlashIndex == -1) {
                throw new IllegalArgumentException("Invalid S3 URL format - missing object key: " + s3StorageUrl);
            }
            
            String bucketName = withoutProtocol.substring(0, firstSlashIndex);
            String objectKey = withoutProtocol.substring(firstSlashIndex + 1);
            
            return generatePreSignedUrl(bucketName, objectKey);
            
        } catch (Exception e) {
            log.error("Failed to parse S3 storage URL: {}", s3StorageUrl, e);
            throw new RuntimeException("Failed to generate pre-signed URL from storage URL", e);
        }
    }
    
    /**
     * 목업 Pre-signed URL 생성 (실제 환경에서는 AWS SDK 사용)
     * 실제 구현에서는 AWS SDK의 S3Presigner를 사용하여 구현
     */
    private String generateMockPreSignedUrl(String bucketName, String objectKey) {
        // 현재 시간 기반 토큰 생성 (5분 후 만료)
        long expirationTime = Instant.now().plus(PRE_SIGNED_URL_EXPIRATION).getEpochSecond();
        String token = generateSignatureToken(bucketName, objectKey, expirationTime);
        
        return String.format(
            "https://%s.s3.%s.amazonaws.com/%s?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=%s&X-Amz-Date=%s&X-Amz-Expires=300&X-Amz-Signature=%s", 
            bucketName, 
            awsRegion, 
            objectKey,
            awsAccessKey,
            Instant.now().toString().replaceAll("[^0-9]", "").substring(0, 8) + "T" + 
            Instant.now().toString().replaceAll("[^0-9]", "").substring(8, 14) + "Z",
            token
        );
    }
    
    /**
     * 서명 토큰 생성 (실제로는 AWS V4 서명 알고리즘 사용)
     */
    private String generateSignatureToken(String bucketName, String objectKey, long expirationTime) {
        // 실제 구현에서는 AWS V4 서명 알고리즘 사용
        // 여기서는 단순한 해시 기반 토큰 생성
        String data = bucketName + objectKey + expirationTime + awsSecretKey;
        return Integer.toHexString(data.hashCode()).substring(0, 32);
    }
    
    /**
     * Pre-signed URL이 만료되었는지 확인
     * (실제 환경에서는 URL 파라미터에서 만료 시간 추출하여 확인)
     */
    public boolean isPreSignedUrlExpired(String preSignedUrl) {
        // 목업 구현: 실제로는 URL에서 X-Amz-Expires 파라미터 파싱
        return false; // 항상 유효한 것으로 가정
    }
}