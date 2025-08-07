package ac.su.kdt.beaievaluationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 및 데이터베이스 설정
 * 
 * Spring Data JPA의 고급 기능들을 활성화하고 Repository 계층을 구성합니다.
 * 엔티티의 생성/수정 시간 자동 관리와 Repository 스캔 설정을 담당합니다.
 * 
 * 활성화 기능:
 * - JPA Auditing: @CreatedDate, @LastModifiedDate 자동 설정
 * - Repository 스캔: 지정된 패키지에서 Repository 인터페이스 자동 감지
 * 
 * Auditing 지원 어노테이션:
 * - @CreatedDate: 엔티티 생성 시각 자동 설정
 * - @LastModifiedDate: 엔티티 수정 시각 자동 갱신
 * - @EntityListeners: AuditingEntityListener 적용 필요
 * 
 * Repository 스캔 패키지:
 * - ac.su.kdt.beaievaluationservice.repository
 * - AIEvaluationRepository, EvaluationSummaryRepository, EvaluationHistoryRepository
 * 
 * 데이터베이스 테이블 설계:
 * - ai_evaluation: 메인 평가 데이터
 * - evaluation_summary: 대시보드 최적화 요약 데이터  
 * - evaluation_history: 상태 변경 감사 이력
 * 
 * @author AI Evaluation Service
 * @since 1.0.0
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "ac.su.kdt.beaievaluationservice.repository")
public class JpaConfig {
}