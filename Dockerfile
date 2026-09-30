# 오점왓 운영 이미지: React 빌드 결과를 Spring Boot jar의 static 리소스로 넣어 이미지 하나로 배포한다.

# 1) 프론트엔드 빌드
FROM node:24-alpine AS frontend
WORKDIR /app
COPY ohjumwhat-frontend/package.json ohjumwhat-frontend/package-lock.json ./
RUN npm ci
COPY ohjumwhat-frontend/ ./
RUN npm run build

# 2) 백엔드 빌드 (테스트는 CI에서 먼저 돌린다)
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY ohjumwhat-backend/gradlew ohjumwhat-backend/settings.gradle.kts ohjumwhat-backend/build.gradle.kts ./
COPY ohjumwhat-backend/gradle ./gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY ohjumwhat-backend/src ./src
# application.yml은 git에 없으므로 예시 파일(플레이스홀더만 있음)을 쓴다. 실제 값은 실행 시 환경변수로 주입된다.
RUN cp src/main/resources/application.example.yml src/main/resources/application.yml
COPY --from=frontend /app/dist ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon -q

# 3) 실행
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=backend /app/build/libs/*.jar app.jar
USER app
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
