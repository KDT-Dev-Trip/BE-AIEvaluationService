package ac.su.kdt.beaievaluationservice.client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

// 시계열 메트릭 데이터 포인트
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricDataPoint {
    private Instant timestamp;
    private double value;
}