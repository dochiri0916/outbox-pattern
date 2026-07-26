FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
COPY core core
COPY polling-worker polling-worker
COPY cdc-consumer cdc-consumer

RUN chmod +x gradlew \
    && ./gradlew :polling-worker:bootJar --no-daemon \
    && jar_path="$(find polling-worker/build/libs -maxdepth 1 -type f -name 'polling-worker-*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$jar_path" \
    && cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
