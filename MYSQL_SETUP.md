# MySQL 연동 설정 가이드

AI 평가 서비스의 MySQL 데이터베이스 연동 설정 방법을 안내합니다.

## 🗄️ 데이터베이스 스키마 변경사항

### 주요 개선점
- **미션 서비스 연동**: `user_id`, `mission_id`를 BIGINT로 변경
- **DevOps 채점관 점수 체계**: `correctness`, `efficiency`, `quality` 점수 도입
- **명령어 분석 기능**: `command_analysis` 테이블 추가
- **S3 통합 관리**: 다중 S3 경로 지원
- **성능 최적화**: 복합 인덱스 및 파티셔닝 지원

### 엔티티 변경사항
1. **AIEvaluation**: 미션 서비스 필드들 추가
2. **EvaluationSummary**: DevOps 채점 점수와 미션 통계 추가
3. **CommandAnalysis**: 새로운 엔티티 (명령어 분석 결과)
4. **MissionS3Storage**: S3 경로 관리 확장

## 🚀 빠른 시작

### 1. Docker Compose로 MySQL 환경 구성

```bash
# MySQL 개발 환경 시작
docker-compose -f docker-compose-mysql.yml up -d

# 로그 확인
docker-compose -f docker-compose-mysql.yml logs -f mysql-ai-eval
```

### 2. 환경 변수 설정

```bash
# .env 파일 생성
cp .env.example .env

# 환경 변수 편집
# MYSQL_HOST=localhost
# MYSQL_PORT=3307
# MYSQL_DATABASE=ai_evaluation_dev_db
# MYSQL_USERNAME=ai_eval_dev
# MYSQL_PASSWORD=dev_password
```

### 3. 애플리케이션 실행

```bash
# MySQL 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=dev,mysql'

# 또는 환경 변수로 설정
export SPRING_PROFILES_ACTIVE=dev,mysql
./gradlew bootRun
```

## 🔧 설정 옵션

### 프로필별 데이터베이스 설정

#### H2 개발 환경 (기본)
```properties
spring.profiles.active=dev
# H2 메모리 DB 사용 (기존과 동일)
```

#### MySQL 개발 환경
```properties
spring.profiles.active=dev,mysql
# Docker Compose MySQL 사용
```

#### MySQL 운영 환경
```properties
spring.profiles.active=mysql
# 운영 MySQL 서버 연결
```

### 데이터베이스 연결 설정

**개발환경 (application-dev.properties)**
```properties
# H2 모드에서 MySQL 호환성 활성화
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.jpa.hibernate.ddl-auto=update
```

**MySQL 환경 (application-mysql.properties)**
```properties
spring.datasource.url=jdbc:mysql://localhost:3307/ai_evaluation_dev_db
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=validate
```

## 📊 데이터베이스 관리

### Adminer 웹 UI 접근
```
URL: http://localhost:8081
서버: mysql-ai-eval
사용자명: ai_eval_dev
비밀번호: dev_password
데이터베이스: ai_evaluation_dev_db
```

### MySQL CLI 접근
```bash
# Docker 컨테이너 내부 접근
docker exec -it ai-eval-mysql-dev mysql -u ai_eval_dev -p ai_evaluation_dev_db

# 로컬에서 직접 접근
mysql -h localhost -P 3307 -u ai_eval_dev -p ai_evaluation_dev_db
```

### 스키마 초기화
```sql
-- 데이터베이스 스키마 적용
source /path/to/database_schema.sql

-- 또는 Docker 볼륨을 통해 자동 적용됨
-- (docker-entrypoint-initdb.d 디렉토리 활용)
```

## 🔍 마이그레이션 가이드

### 기존 H2에서 MySQL로 마이그레이션

1. **데이터 백업 (H2)**
```sql
-- H2 콘솔에서 데이터 내보내기
SCRIPT TO 'backup.sql'
```

2. **MySQL 스키마 적용**
```bash
# Docker Compose로 MySQL 시작
docker-compose -f docker-compose-mysql.yml up -d mysql-ai-eval
```

3. **데이터 변환 및 이관**
```sql
-- user_id, mission_id 타입 변경 필요
-- String -> BIGINT 변환
UPDATE evaluation_summary SET 
  user_id = CAST(user_id AS UNSIGNED),
  mission_id = CAST(mission_id AS UNSIGNED);
```

### 프로덕션 배포

1. **MySQL 서버 준비**
```sql
CREATE DATABASE ai_evaluation_prod_db 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'ai_eval_prod'@'%' 
  IDENTIFIED BY 'secure_production_password';

GRANT ALL PRIVILEGES ON ai_evaluation_prod_db.* 
  TO 'ai_eval_prod'@'%';

FLUSH PRIVILEGES;
```

2. **애플리케이션 설정**
```properties
# application-prod.properties
spring.profiles.active=mysql
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
```

## 🚨 주의사항

### 데이터 타입 변경
- `user_id`: String → BIGINT
- `mission_id`: String → BIGINT  
- `mission_attempt_id`: VARCHAR(255) → VARCHAR(36)

### 호환성 유지
- 기존 API는 그대로 동작
- 새로운 점수 체계 지원
- 기존 점수 필드도 유지됨

### 성능 최적화
- 복합 인덱스 활용
- 커넥션 풀 최적화
- 배치 처리 활성화

## 🔧 트러블슈팅

### 일반적인 문제들

**1. 연결 오류**
```
Could not create connection to database server
```
해결: Docker 컨테이너 상태 확인 및 포트 충돌 확인

**2. 스키마 오류**
```
Table doesn't exist
```
해결: `spring.jpa.hibernate.ddl-auto=update`로 설정 변경

**3. 인코딩 문제**
```
Incorrect string value
```
해결: UTF-8MB4 설정 확인

### 로그 확인
```bash
# 애플리케이션 로그
docker-compose -f docker-compose-mysql.yml logs -f ai-eval-service

# MySQL 로그
docker-compose -f docker-compose-mysql.yml logs -f mysql-ai-eval

# 전체 로그
docker-compose -f docker-compose-mysql.yml logs -f
```

## 📈 모니터링

### Health Check 엔드포인트
```bash
# 애플리케이션 상태
curl http://localhost:8084/actuator/health

# 데이터베이스 연결 상태  
curl http://localhost:8084/actuator/health/db
```

### 메트릭 수집
```properties
# Prometheus 메트릭 활성화
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.metrics.enabled=true
management.endpoint.prometheus.enabled=true
```

이제 AI 평가 서비스가 MySQL과 완벽하게 연동되어 미션 서비스와 원활하게 동작할 수 있습니다!