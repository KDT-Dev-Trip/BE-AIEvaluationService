# Dockerfile

# 1단계: 빌드(Build) 환경 설정
# FROM: 베이스 이미지를 지정. 코드를 컴파일하고 빌드하기 위해 JDK(Java Development Kit)가 포함된 이미지를 사용
# as builder: 이 단계를 'builder'라고 지정. 나중에 이 단계의 결과물만 가져올 수 있음
FROM openjdk:17-jdk-slim as builder

# 컨테이너 내부의 작업 디렉토리를 설정. 앞으로의 모든 명령어는 이 경로를 기준으로 실행
WORKDIR /workspace/app

# 먼저 Gradle 설정 파일들만 복사하여 의존성만 미리 다운로드 (Docker 레이어 캐싱 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# RUN: 컨테이너 내부에서 명령어를 실행
# chmod +x ./gradlew: Gradle 실행 스크립트에 실행 권한을 부여
# ./gradlew dependencies: 프로젝트의 모든 의존성을 미리 다운로드. 소스코드가 바뀌어도 의존성이 그대로면 이 단계는 캐시를 사용해 빌드 속도 향상
RUN chmod +x ./gradlew && ./gradlew dependencies

# 소스 코드를 컨테이너 내부로 복사
COPY src src

# ./gradlew build: Gradle을 사용하여 애플리케이션을 빌드 (-x test는 빌드 시 테스트를 생략하는 옵션)
# build/libs 폴더 안에 실행 가능한 .jar 파일이 생성
RUN ./gradlew build -x test

# 2단계: 실행(Final) 환경 설정
#가볍고 실행에만 필요한 JRE(Java Runtime Environment) 버전을 최종 실행용 베이스 이미지로 사용
FROM openjdk:17-slim-bullseye

# 최종 실행 컨테이너의 작업 디렉토리를 설정
WORKDIR /app

# COPY --from=builder: 다른 단계(여기서는 'builder' 단계)의 결과물을 복사
# 빌드 단계에서 생성된 JAR 파일을 실행 환경으로 복사, 이름을 app.jar로 통일하여 실행하기 쉽게 생성
COPY --from=builder /workspace/app/build/libs/*.jar app.jar

# 8082 포트를 외부에 노출할 것임을 명시(우리 서비스 포트)
EXPOSE 8084

# ENTRYPOINT: 컨테이너가 시작될 때 실행할 기본 명령어를 지정
# ["java","-jar","/app.jar"]: java -jar /app.jar 명령으로 Spring Boot 애플리케이션을 실행
ENTRYPOINT ["java","-jar","app.jar"]