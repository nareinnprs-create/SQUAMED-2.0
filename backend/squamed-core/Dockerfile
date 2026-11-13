# syntax=docker/dockerfile:1
# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew bootJar --no-daemon --console=plain

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Xss512k -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

RUN useradd --create-home --shell /usr/sbin/nologin squamed
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R squamed:squamed /app

USER squamed

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]