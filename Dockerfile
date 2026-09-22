# Render 배포용 Dockerfile (백엔드 Spring Boot, backend/).
# 이 프로젝트는 Gradle이 아니라 Maven(mvnw)을 사용하고, pom.xml의 java.version이 21이라 이에 맞춤.
# frontend(정적 HTML/JS)와 customhouse-ai(Python)는 별도 배포 대상이라 여기 포함하지 않는다.

# 1. Java 21 및 Maven 빌드 환경 설정
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 프로젝트 파일 복사 (backend 모듈만 - .dockerignore로 .env/.venv 등도 차단됨)
COPY backend/ .

# Maven 프로젝트 빌드 (mvnw 사용, Gradle 사용 시와 달리 -DskipTests로 테스트 스킵)
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# 2. 실행 환경
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드된 jar 파일 복사
COPY --from=builder /app/target/*.jar app.jar

# 앱 실행
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
