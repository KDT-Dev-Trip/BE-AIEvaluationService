# ==================================================
# DevTrip AI Evaluation Service - Dockerfile
# 표준화된 멀티스테이지 빌드 (Build → Production)
# ==================================================

# ========================
# Build Stage - 빌드 전용 환경
# ========================
FROM eclipse-temurin:17-jdk-alpine AS builder

# 빌드 작업 디렉토리 설정
WORKDIR /build

# Gradle 래퍼 파일들 먼저 복사 (의존성 캐싱 최적화)
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# Gradle 실행 권한 부여 및 의존성 다운로드
RUN chmod +x ./gradlew && \
    ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src/ src/

# Spring Boot 애플리케이션 빌드 (테스트 제외로 빌드 속도 향상)
RUN ./gradlew bootJar --no-daemon -x test

# JAR 파일 확인 (디버깅용)
RUN ls -la build/libs/

# ========================
# Production Stage - 운영 실행 환경
# ========================
FROM eclipse-temurin:17-jre-alpine AS production

# 보안을 위한 비루트 사용자 생성
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 필수 패키지 설치 (헬스체크용 curl, 타임존 설정용 tzdata)
RUN apk add --no-cache \
    curl \
    tzdata \
    dumb-init

# 타임존 설정 (한국시간)
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 애플리케이션 작업 디렉토리 설정
WORKDIR /app

# 로그 디렉토리 생성 및 권한 설정
RUN mkdir -p /app/logs /var/log/app && \
    chown -R appuser:appgroup /app /var/log/app

# 빌드 단계에서 생성된 JAR 파일 복사
COPY --from=builder /build/build/libs/*.jar app.jar

# JAR 파일 소유권을 appuser로 변경
RUN chown appuser:appgroup app.jar

# 비루트 사용자로 실행
USER appuser

# JVM 최적화 옵션 설정 (AI 평가 서비스용 메모리 조정)
ENV JAVA_OPTS="-Xms512m -Xmx1024m \
               -XX:+UseG1GC \
               -XX:G1HeapRegionSize=16m \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UseCompressedOops \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

# AI Evaluation Service 포트 노출 (PORT_MAPPING.md에 따라 8085)
EXPOSE 8085

# 헬스체크 설정 (Spring Boot Actuator health 엔드포인트 사용)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1

# 애플리케이션 시작 (dumb-init으로 PID 1 문제 해결)
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ========================
# 메타데이터
# ========================
LABEL maintainer="DevTrip Platform Team"
LABEL service="ai-evaluation"
LABEL version="2.0.0"
LABEL description="DevTrip AI Evaluation Service - Gemini/OpenAI 기반 코드 평가 및 분석 서비스"
LABEL port="8085"