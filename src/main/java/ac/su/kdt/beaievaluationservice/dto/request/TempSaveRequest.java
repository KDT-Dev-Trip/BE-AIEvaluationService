package ac.su.kdt.beaievaluationservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

// 임시 저장 요청 DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TempSaveRequest {
    
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;
    
    @NotBlank(message = "미션 ID는 필수입니다") 
    private String missionId;
    
    @NotBlank(message = "미션 시도 ID는 필수입니다")
    private String missionAttemptId;
    
    private String missionType;
    private String missionTitle;
    
    @NotBlank(message = "임시 저장할 코드는 필수입니다")
    private String tempCode;
    
    @Min(value = 1, message = "저장 횟수는 1 이상이어야 합니다")
    private Integer saveCount = 1;
    
    private String saveReason = "manual_save"; // "manual_save", "auto_save"
}