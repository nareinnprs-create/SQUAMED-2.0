# syntax=docker/dockerfile:1
# Multi-stage backend image: Gradle build stage -> slim JRE runtime stage.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY backend/squamed-core/gradlew backend/squamed-core/gradle ./backend-src/
RUN chmod +x backend-src/gradlew
COPY backend/squamed-core/build.gradle.kts backend/squamed-core/settings.gradle.kts backend-src/
COPY backend/squamed-core/src backend-src/src
RUN cd backend-src && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --create-home --system --uid 10001 squamed
WORKDIR /app
COPY --from=build /build/backend-src/build/libs/squamed-core-0.1.0.jar app.jar
USER squamed
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]