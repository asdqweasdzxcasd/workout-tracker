# =====================================================
# Spring Boot 멀티스테이지 Dockerfile
# - Stage 1 (builder): Eclipse Temurin 17 JDK Alpine 에서 bootJar 빌드
# - Stage 2 (runtime): Eclipse Temurin 17 JRE Alpine 으로 실행 (이미지 크기 최소화)
# - JVM 메모리 보호: EC2 t3.small 2GB 환경에서 OOM 방지
# - 한국 timezone (KST), 비루트 사용자 실행
# =====================================================

# -----------------------------------------------------
# Stage 1: Builder
# -----------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# 1) Gradle wrapper / 빌드 스크립트만 먼저 복사 -> 의존성 캐시 활용
#    소스만 바뀌었을 때는 ./gradlew dependencies 레이어 재사용
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle
COPY gradle.properties gradle.properties

# gradlew 권한 부여 (Windows 체크인 시 실행권한 누락 가능)
RUN chmod +x ./gradlew

# 의존성만 먼저 다운로드 (소스 변경 시 캐시 히트 위해 분리)
# --no-daemon: 컨테이너 빌드에서는 Gradle daemon 불필요
RUN ./gradlew dependencies --no-daemon || true

# 2) 소스 복사 후 bootJar 빌드
COPY src src

# bootJar 만 빌드 (test 는 CI 에서 실행, 이미지 빌드는 산출물만)
RUN ./gradlew bootJar --no-daemon -x test

# -----------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# 한국 timezone 설정
RUN apk add --no-cache tzdata wget && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

ENV TZ=Asia/Seoul

# JVM 메모리 제한 (t3.small 2GB 보호)
# - Xmx384m: 힙 최대 384MB
# - MaxMetaspaceSize 128m: 메타스페이스 상한
# - user.timezone: KST 강제 (JDBC TIMESTAMPTZ <-> OffsetDateTime 일관성)
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:MaxMetaspaceSize=128m -Duser.timezone=Asia/Seoul"

# 비루트 사용자 생성 (보안)
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# builder 스테이지에서 만들어진 JAR 만 복사
# bootJar 결과는 build/libs/*.jar 로 생성됨
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

# 비루트 소유로 변경
RUN chown -R spring:spring /app

USER spring

EXPOSE 8080

# Docker healthcheck (compose 에서 override 가능)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
