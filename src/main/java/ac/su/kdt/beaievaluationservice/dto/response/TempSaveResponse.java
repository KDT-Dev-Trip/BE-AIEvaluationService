package ac.su.kdt.beaievaluationservice.dto.response;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

// 임시 저장 응답 DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TempSaveResponse {
    
    private Long id;
    private String userId;
    private String missionId;
    private String missionAttemptId;
    private String missionType;
    private String missionTitle;
    private Integer saveCount;
    private String saveStatus;
    private Boolean isFinalCompleted;
    private Integer tempCodeLength; // 보안상 코드 전체는 반환하지 않고 길이만
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}