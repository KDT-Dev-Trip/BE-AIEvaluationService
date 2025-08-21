-- AI 평가 시스템 데이터베이스 스키마 (미션 서비스 연동)
-- DevTrip Mission Management Service와 긴밀하게 연동된 AI 평가 스키마

-- ============================================================================
-- 미션 서비스 참조 테이블 (읽기 전용 뷰 또는 참조용)
-- ============================================================================

-- 미션 정보 참조 (실제 테이블은 미션 서비스에 있음)
-- CREATE OR REPLACE VIEW v_mission_reference AS
-- SELECT id, title, description, difficulty, category, evaluation_criteria 
-- FROM mission_service.mission WHERE status = 'ACTIVE';

-- ============================================================================
-- AI 평가 시스템 핵심 테이블
-- ============================================================================

-- 1. AI_EVALUATION 테이블 (메인 평가 테이블) - 미션 서비스와 연동
CREATE TABLE ai_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'AI 평가 고유 식별자',
    mission_attempt_id VARCHAR(36) NOT NULL UNIQUE COMMENT '미션 시도 ID (mission_attempt.id 참조)',
    user_id BIGINT NOT NULL COMMENT '평가 대상 사용자 ID',
    mission_id BIGINT NOT NULL COMMENT '미션 ID (mission.id 참조)',
    evaluation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (evaluation_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')) 
        COMMENT '평가 상태',
    evaluation_result JSON COMMENT '평가 결과 (JSON 형태)',
    ai_model_version VARCHAR(50) DEFAULT 'gemini-2.0-flash-exp' COMMENT 'AI 모델 버전',
    error_message TEXT COMMENT '평가 실패 시 오류 메시지',
    
    -- 미션 서비스에서 전달받은 추가 정보
    mission_title VARCHAR(200) COMMENT '미션 제목 (캐싱)',
    mission_type VARCHAR(50) COMMENT '미션 타입/카테고리',
    submitted_code TEXT COMMENT '제출된 코드',
    s3_log_path VARCHAR(500) COMMENT 'S3 명령어 로그 경로',
    s3_workspace_path VARCHAR(500) COMMENT 'S3 워크스페이스 경로',
    
    -- 평가 메타데이터
    evaluation_trigger VARCHAR(30) DEFAULT 'MISSION_COMPLETION' 
        COMMENT '평가 트리거 (MISSION_COMPLETION, MANUAL_REQUEST, RETRY)',
    processing_node VARCHAR(100) COMMENT '처리 노드 정보',
    processing_time_ms BIGINT COMMENT '처리 시간 (밀리초)',
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '평가 요청 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정 시간'
);

-- 2. EVALUATION_HISTORY 테이블 (평가 상태 변경 이력)
CREATE TABLE evaluation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ai_evaluation_id BIGINT NOT NULL,
    previous_status VARCHAR(20) CHECK (previous_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    new_status VARCHAR(20) NOT NULL CHECK (new_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    change_reason VARCHAR(500),
    processing_node VARCHAR(100),
    error_details TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 외래키 제약조건
    CONSTRAINT fk_evaluation_history_ai_evaluation 
        FOREIGN KEY (ai_evaluation_id) REFERENCES ai_evaluation(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
);

-- 3. EVALUATION_SUMMARY 테이블 (대시보드 및 통계용 요약 정보) - 미션 서비스 연동
CREATE TABLE evaluation_summary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '평가 요약 고유 식별자',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    mission_id BIGINT NOT NULL COMMENT '미션 ID (mission.id 참조)',
    mission_attempt_id VARCHAR(36) NOT NULL UNIQUE COMMENT '미션 시도 ID',
    mission_title VARCHAR(200) COMMENT '미션 제목',
    mission_type VARCHAR(50) COMMENT '미션 타입/카테고리',
    mission_difficulty VARCHAR(20) COMMENT '미션 난이도 (BEGINNER, INTERMEDIATE, ADVANCED)',
    
    -- AI 평가 점수들 (DevOps 채점관 기준)
    overall_score INT CHECK (overall_score >= 0 AND overall_score <= 100) COMMENT '종합 점수',
    correctness_score INT CHECK (correctness_score >= 0 AND correctness_score <= 100) COMMENT '정확성 점수',
    efficiency_score INT CHECK (efficiency_score >= 0 AND efficiency_score <= 100) COMMENT '효율성 점수',
    quality_score INT CHECK (quality_score >= 0 AND quality_score <= 100) COMMENT '코드 품질 점수',
    
    -- 호환성을 위한 기존 필드들 (매핑)
    code_quality_score INT GENERATED ALWAYS AS (correctness_score) STORED COMMENT '코드 품질 점수 (correctness 매핑)',
    security_score INT GENERATED ALWAYS AS (efficiency_score) STORED COMMENT '보안 점수 (efficiency 매핑)', 
    style_score INT GENERATED ALWAYS AS (quality_score) STORED COMMENT '스타일 점수 (quality 매핑)',
    
    evaluation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (evaluation_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')) 
        COMMENT '평가 상태',
    feedback_summary TEXT COMMENT '피드백 요약',
    feedback_details JSON COMMENT '상세 피드백 (JSON 형태)',
    
    -- 미션 수행 통계
    commands_executed INT DEFAULT 0 COMMENT '실행된 명령어 수',
    significant_commands INT DEFAULT 0 COMMENT '중요 명령어 수',
    error_commands INT DEFAULT 0 COMMENT '에러 발생 명령어 수',
    total_execution_time_ms BIGINT DEFAULT 0 COMMENT '총 실행 시간',
    
    -- 평가 메타데이터
    evaluation_duration_ms BIGINT COMMENT 'AI 평가 소요 시간',
    ai_evaluation_id BIGINT COMMENT 'AI 평가 테이블 참조',
    stamps_earned INT DEFAULT 0 COMMENT '획득한 스탬프 수',
    points_awarded INT DEFAULT 0 COMMENT '부여된 포인트',
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    
    -- 외래키 제약조건
    CONSTRAINT fk_evaluation_summary_ai_evaluation 
        FOREIGN KEY (ai_evaluation_id) REFERENCES ai_evaluation(id) 
        ON DELETE SET NULL ON UPDATE CASCADE,
        
    -- 비즈니스 규칙 제약조건
    CONSTRAINT chk_user_id_positive CHECK (user_id > 0),
    CONSTRAINT chk_mission_id_positive CHECK (mission_id > 0),
    CONSTRAINT chk_mission_attempt_id_not_empty CHECK (mission_attempt_id != ''),
    CONSTRAINT chk_commands_non_negative CHECK (commands_executed >= 0 AND significant_commands >= 0 AND error_commands >= 0)
);

-- 4. COMMAND_ANALYSIS 테이블 (명령어 분석 결과) - 미션 서비스 command_logs 연동
CREATE TABLE command_analysis (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '명령어 분석 고유 식별자',
    mission_attempt_id VARCHAR(36) NOT NULL COMMENT '미션 시도 ID',
    command_log_id VARCHAR(36) COMMENT '명령어 로그 ID (command_logs.id 참조)',
    ai_evaluation_id BIGINT NOT NULL COMMENT 'AI 평가 참조',
    
    -- 명령어 분석 결과
    command TEXT NOT NULL COMMENT '분석된 명령어',
    command_category VARCHAR(50) COMMENT '명령어 카테고리 (docker, kubectl, git 등)',
    correctness_assessment VARCHAR(20) COMMENT '정확성 평가 (CORRECT, INCORRECT, PARTIAL)',
    efficiency_rating INT CHECK (efficiency_rating >= 1 AND efficiency_rating <= 5) COMMENT '효율성 등급 (1-5)',
    best_practice_score INT CHECK (best_practice_score >= 0 AND best_practice_score <= 10) COMMENT '모범 사례 점수',
    security_risk_level VARCHAR(20) DEFAULT 'LOW' 
        CHECK (security_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')) COMMENT '보안 위험도',
    
    -- AI 분석 상세
    ai_feedback TEXT COMMENT 'AI 피드백',
    improvement_suggestions JSON COMMENT '개선 제안사항 (JSON)',
    alternative_commands JSON COMMENT '대체 명령어 제안 (JSON)',
    
    -- 메타데이터
    analysis_confidence DECIMAL(3,2) DEFAULT 0.85 
        CHECK (analysis_confidence >= 0.0 AND analysis_confidence <= 1.0) COMMENT '분석 신뢰도',
    processing_time_ms INT COMMENT '분석 처리 시간',
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '분석 시간',
    
    -- 외래키 제약조건
    CONSTRAINT fk_command_analysis_ai_evaluation 
        FOREIGN KEY (ai_evaluation_id) REFERENCES ai_evaluation(id) 
        ON DELETE CASCADE ON UPDATE CASCADE
);

-- 5. MISSION_S3_STORAGE 테이블 (S3 저장소 정보) - 미션 서비스와 연동
CREATE TABLE mission_s3_storage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'S3 저장소 정보 식별자',
    mission_attempt_id VARCHAR(36) NOT NULL UNIQUE COMMENT '미션 시도 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    mission_id BIGINT NOT NULL COMMENT '미션 ID',
    
    -- S3 저장소 경로들
    s3_storage_url VARCHAR(500) NOT NULL COMMENT 'S3 저장소 기본 URL',
    command_logs_path VARCHAR(500) COMMENT '명령어 로그 S3 경로',
    workspace_snapshot_path VARCHAR(500) COMMENT '워크스페이스 스냅샷 S3 경로',
    submission_files_path VARCHAR(500) COMMENT '제출 파일들 S3 경로',
    
    -- S3 메타데이터
    bucket_name VARCHAR(255) NOT NULL COMMENT 'S3 버킷 이름',
    object_key_prefix VARCHAR(500) NOT NULL COMMENT 'S3 객체 키 프리픽스',
    total_size_bytes BIGINT DEFAULT 0 COMMENT '총 저장 크기',
    file_count INT DEFAULT 0 COMMENT '파일 개수',
    last_sync_at TIMESTAMP COMMENT '마지막 동기화 시간',
    
    -- 접근 권한
    access_level VARCHAR(20) DEFAULT 'PRIVATE' 
        CHECK (access_level IN ('PRIVATE', 'TEAM', 'PUBLIC')) COMMENT '접근 권한 수준',
    expiry_date TIMESTAMP COMMENT '만료일 (NULL이면 영구)',
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    
    -- 비즈니스 규칙 제약조건
    CONSTRAINT chk_s3_mission_attempt_id_not_empty CHECK (mission_attempt_id != ''),
    CONSTRAINT chk_s3_user_id_positive CHECK (user_id > 0),
    CONSTRAINT chk_s3_mission_id_positive CHECK (mission_id > 0),
    CONSTRAINT chk_s3_storage_url_not_empty CHECK (s3_storage_url != ''),
    CONSTRAINT chk_bucket_name_not_empty CHECK (bucket_name != ''),
    CONSTRAINT chk_object_key_prefix_not_empty CHECK (object_key_prefix != ''),
    CONSTRAINT chk_file_metrics_non_negative CHECK (total_size_bytes >= 0 AND file_count >= 0)
);

-- ============================================================================
-- 성능 최적화를 위한 인덱스 (미션 서비스 연동 고려)
-- ============================================================================

-- AI_EVALUATION 테이블 인덱스
CREATE UNIQUE INDEX idx_mission_attempt_id ON ai_evaluation(mission_attempt_id);
CREATE INDEX idx_user_mission_evaluation ON ai_evaluation(user_id, mission_id);
CREATE INDEX idx_evaluation_status ON ai_evaluation(evaluation_status);
CREATE INDEX idx_status_created_at ON ai_evaluation(evaluation_status, created_at);
CREATE INDEX idx_mission_type ON ai_evaluation(mission_type);
CREATE INDEX idx_processing_node ON ai_evaluation(processing_node);
CREATE INDEX idx_created_at ON ai_evaluation(created_at);
CREATE INDEX idx_updated_at ON ai_evaluation(updated_at);

-- EVALUATION_HISTORY 테이블 인덱스
CREATE INDEX idx_ai_evaluation_id ON evaluation_history(ai_evaluation_id);
CREATE INDEX idx_status_change ON evaluation_history(previous_status, new_status);
CREATE INDEX idx_history_created_at ON evaluation_history(created_at);
CREATE INDEX idx_execution_time ON evaluation_history(execution_time_ms);

-- EVALUATION_SUMMARY 테이블 인덱스
CREATE INDEX idx_user_id ON evaluation_summary(user_id);
CREATE INDEX idx_mission_id ON evaluation_summary(mission_id);
CREATE INDEX idx_user_mission ON evaluation_summary(user_id, mission_id);
CREATE INDEX idx_mission_difficulty ON evaluation_summary(mission_difficulty);
CREATE INDEX idx_overall_score ON evaluation_summary(overall_score);
CREATE INDEX idx_correctness_score ON evaluation_summary(correctness_score);
CREATE INDEX idx_summary_created_at ON evaluation_summary(created_at);
CREATE UNIQUE INDEX idx_summary_mission_attempt_id ON evaluation_summary(mission_attempt_id);
CREATE INDEX idx_commands_stats ON evaluation_summary(commands_executed, significant_commands, error_commands);

-- COMMAND_ANALYSIS 테이블 인덱스
CREATE INDEX idx_command_mission_attempt ON command_analysis(mission_attempt_id);
CREATE INDEX idx_command_evaluation ON command_analysis(ai_evaluation_id);
CREATE INDEX idx_command_category ON command_analysis(command_category);
CREATE INDEX idx_correctness_assessment ON command_analysis(correctness_assessment);
CREATE INDEX idx_security_risk ON command_analysis(security_risk_level);
CREATE INDEX idx_analysis_confidence ON command_analysis(analysis_confidence);
CREATE INDEX idx_command_created_at ON command_analysis(created_at);

-- MISSION_S3_STORAGE 테이블 인덱스
CREATE UNIQUE INDEX idx_s3_mission_attempt_id ON mission_s3_storage(mission_attempt_id);
CREATE INDEX idx_s3_user_id ON mission_s3_storage(user_id);
CREATE INDEX idx_s3_mission_id ON mission_s3_storage(mission_id);
CREATE INDEX idx_s3_bucket_prefix ON mission_s3_storage(bucket_name, object_key_prefix);
CREATE INDEX idx_s3_access_level ON mission_s3_storage(access_level);
CREATE INDEX idx_s3_expiry_date ON mission_s3_storage(expiry_date);
CREATE INDEX idx_s3_created_at ON mission_s3_storage(created_at);

-- 6. 트리거 (감사 추적 및 데이터 일관성)
DELIMITER $$

-- AI_EVALUATION 상태 변경 시 자동으로 EVALUATION_HISTORY에 기록하는 트리거
CREATE TRIGGER tr_ai_evaluation_status_change 
    AFTER UPDATE ON ai_evaluation
    FOR EACH ROW
BEGIN
    IF OLD.evaluation_status != NEW.evaluation_status THEN
        INSERT INTO evaluation_history (
            ai_evaluation_id,
            previous_status,
            new_status,
            change_reason,
            execution_time_ms
        ) VALUES (
            NEW.id,
            OLD.evaluation_status,
            NEW.evaluation_status,
            CASE 
                WHEN NEW.evaluation_status = 'COMPLETED' THEN 'Evaluation completed successfully'
                WHEN NEW.evaluation_status = 'FAILED' THEN CONCAT('Evaluation failed: ', COALESCE(NEW.error_message, 'Unknown error'))
                ELSE 'Status changed'
            END,
            TIMESTAMPDIFF(MICROSECOND, OLD.updated_at, NEW.updated_at) / 1000
        );
    END IF;
END$$

-- EVALUATION_SUMMARY 데이터 일관성 확인 트리거
CREATE TRIGGER tr_evaluation_summary_validation
    BEFORE INSERT ON evaluation_summary
    FOR EACH ROW
BEGIN
    -- mission_attempt_id가 ai_evaluation에 존재하는지 확인
    IF NOT EXISTS (SELECT 1 FROM ai_evaluation WHERE mission_attempt_id = NEW.mission_attempt_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'mission_attempt_id must exist in ai_evaluation table';
    END IF;
END$$

DELIMITER ;

-- ============================================================================
-- 뷰 (자주 사용되는 조회 쿼리 최적화) - 미션 서비스 연동
-- ============================================================================

-- 완료된 평가 결과 조회용 뷰 (미션 서비스 데이터 포함)
CREATE VIEW v_completed_evaluations AS
SELECT 
    ae.id as evaluation_id,
    ae.mission_attempt_id,
    ae.user_id,
    ae.mission_id,
    ae.mission_title,
    ae.mission_type,
    es.mission_difficulty,
    
    -- DevOps 채점관 점수들
    es.overall_score,
    es.correctness_score,
    es.efficiency_score,
    es.quality_score,
    
    -- 호환성 점수들
    es.code_quality_score,
    es.security_score,
    es.style_score,
    
    -- 미션 수행 통계
    es.commands_executed,
    es.significant_commands,
    es.error_commands,
    es.total_execution_time_ms,
    
    -- AI 평가 메타데이터
    ae.ai_model_version,
    ae.evaluation_trigger,
    ae.processing_node,
    ae.created_at as evaluation_started_at,
    ae.updated_at as evaluation_completed_at,
    es.evaluation_duration_ms,
    ae.processing_time_ms,
    
    -- 피드백
    SUBSTRING(es.feedback_summary, 1, 200) as feedback_preview,
    es.stamps_earned,
    es.points_awarded
FROM ai_evaluation ae
JOIN evaluation_summary es ON ae.id = es.ai_evaluation_id
WHERE ae.evaluation_status = 'COMPLETED';

-- 사용자별 평가 통계 뷰 (미션 타입별 분석 포함)
CREATE VIEW v_user_evaluation_stats AS
SELECT 
    user_id,
    COUNT(*) as total_evaluations,
    COUNT(CASE WHEN evaluation_status = 'COMPLETED' THEN 1 END) as completed_evaluations,
    COUNT(CASE WHEN evaluation_status = 'FAILED' THEN 1 END) as failed_evaluations,
    COUNT(CASE WHEN evaluation_status = 'PROCESSING' THEN 1 END) as processing_evaluations,
    
    -- 점수 통계 (DevOps 채점관 기준)
    ROUND(AVG(CASE WHEN overall_score IS NOT NULL THEN overall_score END), 2) as avg_overall_score,
    ROUND(AVG(CASE WHEN correctness_score IS NOT NULL THEN correctness_score END), 2) as avg_correctness_score,
    ROUND(AVG(CASE WHEN efficiency_score IS NOT NULL THEN efficiency_score END), 2) as avg_efficiency_score,
    ROUND(AVG(CASE WHEN quality_score IS NOT NULL THEN quality_score END), 2) as avg_quality_score,
    
    -- 미션 수행 통계
    SUM(commands_executed) as total_commands,
    SUM(significant_commands) as total_significant_commands,
    SUM(error_commands) as total_error_commands,
    ROUND(AVG(CASE WHEN total_execution_time_ms > 0 THEN total_execution_time_ms END), 0) as avg_execution_time_ms,
    
    -- 성과 지표
    SUM(stamps_earned) as total_stamps,
    SUM(points_awarded) as total_points,
    
    -- 시간 관련
    ROUND(AVG(CASE WHEN evaluation_duration_ms IS NOT NULL THEN evaluation_duration_ms END), 0) as avg_evaluation_duration_ms,
    MAX(created_at) as last_evaluation_date,
    MIN(created_at) as first_evaluation_date
FROM evaluation_summary
GROUP BY user_id;

-- 미션별 평가 통계 뷰
CREATE VIEW v_mission_evaluation_stats AS
SELECT 
    mission_id,
    mission_type,
    mission_difficulty,
    COUNT(*) as total_attempts,
    COUNT(CASE WHEN evaluation_status = 'COMPLETED' THEN 1 END) as completed_attempts,
    COUNT(CASE WHEN evaluation_status = 'FAILED' THEN 1 END) as failed_attempts,
    
    -- 평균 점수들
    ROUND(AVG(CASE WHEN overall_score IS NOT NULL THEN overall_score END), 2) as avg_overall_score,
    ROUND(AVG(CASE WHEN correctness_score IS NOT NULL THEN correctness_score END), 2) as avg_correctness_score,
    ROUND(AVG(CASE WHEN efficiency_score IS NOT NULL THEN efficiency_score END), 2) as avg_efficiency_score,
    ROUND(AVG(CASE WHEN quality_score IS NOT NULL THEN quality_score END), 2) as avg_quality_score,
    
    -- 성공률
    ROUND(COUNT(CASE WHEN evaluation_status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate,
    
    -- 명령어 통계
    ROUND(AVG(commands_executed), 1) as avg_commands_per_attempt,
    ROUND(AVG(significant_commands), 1) as avg_significant_commands,
    ROUND(AVG(error_commands), 1) as avg_error_commands,
    
    MAX(created_at) as last_attempt_date,
    COUNT(DISTINCT user_id) as unique_users
FROM evaluation_summary
GROUP BY mission_id, mission_type, mission_difficulty;

-- 일별 평가 통계 뷰 (향상된 분석)
CREATE VIEW v_daily_evaluation_stats AS
SELECT 
    DATE(created_at) as evaluation_date,
    COUNT(*) as total_evaluations,
    COUNT(CASE WHEN evaluation_status = 'COMPLETED' THEN 1 END) as completed_count,
    COUNT(CASE WHEN evaluation_status = 'FAILED' THEN 1 END) as failed_count,
    COUNT(CASE WHEN evaluation_status = 'PROCESSING' THEN 1 END) as processing_count,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT mission_id) as unique_missions,
    
    -- 점수 통계
    ROUND(AVG(CASE WHEN overall_score IS NOT NULL THEN overall_score END), 2) as avg_overall_score,
    ROUND(AVG(CASE WHEN correctness_score IS NOT NULL THEN correctness_score END), 2) as avg_correctness_score,
    ROUND(AVG(CASE WHEN efficiency_score IS NOT NULL THEN efficiency_score END), 2) as avg_efficiency_score,
    ROUND(AVG(CASE WHEN quality_score IS NOT NULL THEN quality_score END), 2) as avg_quality_score,
    
    -- 성과 지표
    SUM(stamps_earned) as total_stamps_earned,
    SUM(points_awarded) as total_points_awarded,
    
    -- 성능 지표
    ROUND(AVG(CASE WHEN evaluation_duration_ms IS NOT NULL THEN evaluation_duration_ms END), 0) as avg_evaluation_duration_ms,
    ROUND(AVG(commands_executed), 1) as avg_commands_per_evaluation
FROM evaluation_summary
GROUP BY DATE(created_at)
ORDER BY evaluation_date DESC;

-- ============================================================================
-- 저장 프로시저 (복잡한 비즈니스 로직) - 미션 서비스 연동
-- ============================================================================
DELIMITER $$

-- 사용자의 미션별 최고 점수 조회 (DevOps 채점관 점수 포함)
CREATE PROCEDURE sp_get_user_best_scores(IN p_user_id BIGINT)
BEGIN
    SELECT 
        es.mission_id,
        es.mission_type,
        es.mission_difficulty,
        MAX(es.overall_score) as best_overall_score,
        MAX(es.correctness_score) as best_correctness_score,
        MAX(es.efficiency_score) as best_efficiency_score,
        MAX(es.quality_score) as best_quality_score,
        
        -- 호환성 점수들
        MAX(es.code_quality_score) as best_code_quality_score,
        MAX(es.security_score) as best_security_score,
        MAX(es.style_score) as best_style_score,
        
        COUNT(*) as attempt_count,
        SUM(es.stamps_earned) as total_stamps,
        SUM(es.points_awarded) as total_points,
        MAX(es.created_at) as last_attempt_date,
        MIN(es.created_at) as first_attempt_date,
        
        -- 미션 수행 효율성
        AVG(es.commands_executed) as avg_commands,
        AVG(es.evaluation_duration_ms) as avg_evaluation_time_ms
    FROM evaluation_summary es
    WHERE es.user_id = p_user_id 
      AND es.evaluation_status = 'COMPLETED'
      AND es.overall_score IS NOT NULL
    GROUP BY es.mission_id, es.mission_type, es.mission_difficulty
    ORDER BY best_overall_score DESC;
END$$

-- 미션 타입별 평가 성과 분석
CREATE PROCEDURE sp_analyze_mission_performance(IN p_mission_type VARCHAR(50), IN p_days INT)
BEGIN
    SELECT 
        DATE(es.created_at) as evaluation_date,
        es.mission_difficulty,
        COUNT(*) as total_attempts,
        COUNT(CASE WHEN es.evaluation_status = 'COMPLETED' THEN 1 END) as successful_attempts,
        ROUND(COUNT(CASE WHEN es.evaluation_status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate,
        
        -- 평균 점수들
        ROUND(AVG(CASE WHEN es.overall_score IS NOT NULL THEN es.overall_score END), 2) as avg_overall_score,
        ROUND(AVG(CASE WHEN es.correctness_score IS NOT NULL THEN es.correctness_score END), 2) as avg_correctness,
        ROUND(AVG(CASE WHEN es.efficiency_score IS NOT NULL THEN es.efficiency_score END), 2) as avg_efficiency,
        ROUND(AVG(CASE WHEN es.quality_score IS NOT NULL THEN es.quality_score END), 2) as avg_quality,
        
        -- 미션 수행 통계
        AVG(es.commands_executed) as avg_commands,
        AVG(es.significant_commands) as avg_significant_commands,
        AVG(es.error_commands) as avg_errors,
        AVG(es.total_execution_time_ms) as avg_execution_time_ms,
        
        COUNT(DISTINCT es.user_id) as unique_users
    FROM evaluation_summary es
    WHERE (p_mission_type IS NULL OR es.mission_type = p_mission_type)
      AND es.created_at >= DATE_SUB(CURDATE(), INTERVAL p_days DAY)
    GROUP BY DATE(es.created_at), es.mission_difficulty
    ORDER BY evaluation_date DESC, es.mission_difficulty;
END$$

-- 평가 실패 원인 분석 (향상된 버전)
CREATE PROCEDURE sp_analyze_evaluation_failures(IN p_days INT)
BEGIN
    SELECT 
        DATE(ae.created_at) as failure_date,
        ae.mission_type,
        COUNT(*) as failure_count,
        
        -- 에러 유형 분류
        GROUP_CONCAT(DISTINCT 
            CASE 
                WHEN ae.error_message LIKE '%timeout%' OR ae.error_message LIKE '%시간%' THEN 'TIMEOUT'
                WHEN ae.error_message LIKE '%network%' OR ae.error_message LIKE '%연결%' THEN 'NETWORK'
                WHEN ae.error_message LIKE '%parsing%' OR ae.error_message LIKE '%JSON%' THEN 'PARSING'
                WHEN ae.error_message LIKE '%API%' OR ae.error_message LIKE '%Gemini%' THEN 'API_ERROR'
                WHEN ae.error_message LIKE '%S3%' THEN 'S3_ACCESS'
                WHEN ae.error_message LIKE '%database%' OR ae.error_message LIKE '%DB%' THEN 'DATABASE'
                ELSE 'OTHER'
            END
        ) as error_types,
        
        AVG(TIMESTAMPDIFF(SECOND, ae.created_at, ae.updated_at)) as avg_failure_time_seconds,
        AVG(ae.processing_time_ms) as avg_processing_time_ms,
        
        -- 처리 노드별 분석
        GROUP_CONCAT(DISTINCT ae.processing_node) as affected_nodes,
        
        -- AI 모델 버전별 실패율
        GROUP_CONCAT(DISTINCT ae.ai_model_version) as ai_model_versions
    FROM ai_evaluation ae
    WHERE ae.evaluation_status = 'FAILED'
      AND ae.created_at >= DATE_SUB(CURDATE(), INTERVAL p_days DAY)
    GROUP BY DATE(ae.created_at), ae.mission_type
    ORDER BY failure_date DESC, failure_count DESC;
END$$

-- 명령어 분석 통계 (보안 위험도 포함)
CREATE PROCEDURE sp_analyze_command_security(IN p_mission_attempt_id VARCHAR(36))
BEGIN
    SELECT 
        ca.command_category,
        ca.security_risk_level,
        COUNT(*) as command_count,
        AVG(ca.efficiency_rating) as avg_efficiency,
        AVG(ca.best_practice_score) as avg_best_practice,
        AVG(ca.analysis_confidence) as avg_confidence,
        
        -- 위험 명령어 예시
        GROUP_CONCAT(
            CASE WHEN ca.security_risk_level IN ('HIGH', 'CRITICAL') 
            THEN SUBSTRING(ca.command, 1, 50) END 
            SEPARATOR '; '
        ) as risky_commands_sample
    FROM command_analysis ca
    WHERE (p_mission_attempt_id IS NULL OR ca.mission_attempt_id = p_mission_attempt_id)
    GROUP BY ca.command_category, ca.security_risk_level
    ORDER BY ca.command_category, 
             FIELD(ca.security_risk_level, 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
END$$

-- 사용자 진행도 및 학습 곡선 분석
CREATE PROCEDURE sp_analyze_user_progress(IN p_user_id BIGINT, IN p_limit INT)
BEGIN
    SELECT 
        es.created_at,
        es.mission_id,
        es.mission_type,
        es.mission_difficulty,
        es.overall_score,
        es.correctness_score,
        es.efficiency_score,
        es.quality_score,
        
        -- 누적 통계
        SUM(es.stamps_earned) OVER (ORDER BY es.created_at) as cumulative_stamps,
        SUM(es.points_awarded) OVER (ORDER BY es.created_at) as cumulative_points,
        
        -- 이동 평균 (최근 5회)
        AVG(es.overall_score) OVER (ORDER BY es.created_at ROWS 4 PRECEDING) as moving_avg_score,
        
        -- 개선도 계산
        es.overall_score - LAG(es.overall_score, 1) OVER (ORDER BY es.created_at) as score_improvement,
        
        -- 미션 수행 효율성 변화
        es.commands_executed,
        es.error_commands,
        ROUND(es.error_commands * 100.0 / NULLIF(es.commands_executed, 0), 2) as error_rate
        
    FROM evaluation_summary es
    WHERE es.user_id = p_user_id 
      AND es.evaluation_status = 'COMPLETED'
    ORDER BY es.created_at DESC
    LIMIT p_limit;
END$$

DELIMITER ;

-- 9. 데이터 정합성 검증 쿼리
-- 고아 레코드 확인
CREATE VIEW v_data_integrity_check AS
SELECT 
    'evaluation_summary orphans' as check_type,
    COUNT(*) as issue_count,
    'Records in evaluation_summary without corresponding ai_evaluation' as description
FROM evaluation_summary es
LEFT JOIN ai_evaluation ae ON es.ai_evaluation_id = ae.id
WHERE ae.id IS NULL

UNION ALL

SELECT 
    'mission_attempt_id mismatch' as check_type,
    COUNT(*) as issue_count,
    'Records where mission_attempt_id differs between tables' as description
FROM evaluation_summary es
JOIN ai_evaluation ae ON es.ai_evaluation_id = ae.id
WHERE es.mission_attempt_id != ae.mission_attempt_id

UNION ALL

SELECT 
    'status inconsistency' as check_type,
    COUNT(*) as issue_count,
    'Records where evaluation_status differs between tables' as description
FROM evaluation_summary es
JOIN ai_evaluation ae ON es.ai_evaluation_id = ae.id
WHERE es.evaluation_status != ae.evaluation_status;

-- ============================================================================
-- 샘플 데이터 (테스트용) - 미션 서비스 연동 형태
-- ============================================================================

-- AI 평가 샘플 데이터
INSERT INTO ai_evaluation (
    mission_attempt_id, user_id, mission_id, evaluation_status, 
    mission_title, mission_type, submitted_code, 
    s3_log_path, s3_workspace_path, evaluation_trigger,
    ai_model_version
) VALUES
('550e8400-e29b-41d4-a716-446655440001', 1001, 1, 'COMPLETED', 
 'Docker 컨테이너 기초', 'DOCKER', 
 'FROM ubuntu:22.04\nRUN apt-get update\nEXPOSE 8080\nCMD ["echo", "Hello DevTrip"]',
 's3://devtrip-logs/user1001/mission001/commands.json',
 's3://devtrip-logs/user1001/mission001/workspace.tar.gz',
 'MISSION_COMPLETION', 'gemini-2.0-flash-exp'),

('550e8400-e29b-41d4-a716-446655440002', 1002, 2, 'PENDING', 
 'Kubernetes 배포 실습', 'KUBERNETES', 
 'kubectl create deployment nginx --image=nginx:latest\nkubectl expose deployment nginx --port=80',
 's3://devtrip-logs/user1002/mission002/commands.json',
 's3://devtrip-logs/user1002/mission002/workspace.tar.gz',
 'MISSION_COMPLETION', 'gemini-2.0-flash-exp'),

('550e8400-e29b-41d4-a716-446655440003', 1003, 3, 'FAILED', 
 'CI/CD 파이프라인 구축', 'CI_CD', 
 'version: 3\nservices:\n  app:\n    build: .\n    ports:\n      - "3000:3000"',
 's3://devtrip-logs/user1003/mission003/commands.json',
 's3://devtrip-logs/user1003/mission003/workspace.tar.gz',
 'MISSION_COMPLETION', 'gemini-2.0-flash-exp');

-- 평가 요약 샘플 데이터
INSERT INTO evaluation_summary (
    user_id, mission_id, mission_attempt_id, mission_title, mission_type, mission_difficulty,
    overall_score, correctness_score, efficiency_score, quality_score,
    evaluation_status, feedback_summary, feedback_details,
    commands_executed, significant_commands, error_commands, total_execution_time_ms,
    evaluation_duration_ms, stamps_earned, points_awarded, ai_evaluation_id
) VALUES
(1001, 1, '550e8400-e29b-41d4-a716-446655440001', 'Docker 컨테이너 기초', 'DOCKER', 'BEGINNER',
 85, 90, 80, 85, 'COMPLETED', 
 'Docker 컨테이너 생성과 기본 설정을 잘 이해하고 있습니다. 보안 설정을 추가하면 더 좋을 것 같습니다.',
 '{"strengths": ["올바른 베이스 이미지 선택", "적절한 포트 노출"], "improvements": ["보안 강화", "멀티스테이지 빌드 고려"]}',
 12, 8, 1, 45000, 2500, 1, 100, 1),

(1002, 2, '550e8400-e29b-41d4-a716-446655440002', 'Kubernetes 배포 실습', 'KUBERNETES', 'INTERMEDIATE',
 0, 0, 0, 0, 'PENDING', NULL, NULL,
 0, 0, 0, 0, NULL, 0, 0, 2);

-- S3 저장소 정보 샘플 데이터
INSERT INTO mission_s3_storage (
    mission_attempt_id, user_id, mission_id, s3_storage_url,
    command_logs_path, workspace_snapshot_path, submission_files_path,
    bucket_name, object_key_prefix, total_size_bytes, file_count,
    access_level, last_sync_at
) VALUES
('550e8400-e29b-41d4-a716-446655440001', 1001, 1, 
 's3://devtrip-logs/user1001/mission001/',
 's3://devtrip-logs/user1001/mission001/commands.json',
 's3://devtrip-logs/user1001/mission001/workspace.tar.gz',
 's3://devtrip-logs/user1001/mission001/submissions/',
 'devtrip-logs', 'user1001/mission001/', 2048576, 15,
 'PRIVATE', CURRENT_TIMESTAMP),

('550e8400-e29b-41d4-a716-446655440002', 1002, 2,
 's3://devtrip-logs/user1002/mission002/',
 's3://devtrip-logs/user1002/mission002/commands.json',
 's3://devtrip-logs/user1002/mission002/workspace.tar.gz',
 's3://devtrip-logs/user1002/mission002/submissions/',
 'devtrip-logs', 'user1002/mission002/', 1536000, 12,
 'PRIVATE', CURRENT_TIMESTAMP);

-- 명령어 분석 샘플 데이터
INSERT INTO command_analysis (
    mission_attempt_id, command_log_id, ai_evaluation_id,
    command, command_category, correctness_assessment, 
    efficiency_rating, best_practice_score, security_risk_level,
    ai_feedback, improvement_suggestions, alternative_commands,
    analysis_confidence
) VALUES
('550e8400-e29b-41d4-a716-446655440001', 'cmd-001', 1,
 'docker build -t myapp .', 'docker', 'CORRECT',
 4, 8, 'LOW',
 '올바른 Docker 빌드 명령어입니다. 태그명이 적절합니다.',
 '["멀티스테이지 빌드 고려", "빌드 컨텍스트 최적화"]',
 '["docker build -t myapp:v1.0 .", "docker build --no-cache -t myapp ."]',
 0.92),

('550e8400-e29b-41d4-a716-446655440001', 'cmd-002', 1,
 'docker run -d -p 8080:8080 myapp', 'docker', 'CORRECT',
 4, 7, 'MEDIUM',
 '컨테이너 실행 명령어가 적절합니다. 보안 설정을 추가하면 좋겠습니다.',
 '["사용자 권한 제한", "리소스 제한 추가"]',
 '["docker run -d -p 8080:8080 --user 1000:1000 myapp", "docker run -d -p 8080:8080 --memory=512m myapp"]',
 0.89);