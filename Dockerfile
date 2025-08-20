# Multi-stage build
FROM gradle:8.11-jdk17 AS build

WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN gradle clean build -x test --no-daemon

FROM openjdk:26-trixie

WORKDIR /app

# Create log directory and set permissions
RUN mkdir -p /var/log/app && \
    chown -R 1001:1001 /var/log/app

# Create non-root user for security
RUN addgroup --system --gid 1001 spring && \
    adduser --system --uid 1001 --gid 1001 spring

# Copy jar file
COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER spring

EXPOSE 8084

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}", \
    "-jar", \
    "app.jar"]