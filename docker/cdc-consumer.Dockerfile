FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
COPY core core
COPY polling-worker polling-worker
COPY cdc-consumer cdc-consumer

RUN chmod +x gradlew \
    && ./gradlew :cdc-consumer:bootJar --no-daemon \
    && jar_path="$(find cdc-consumer/build/libs -maxdepth 1 -type f -name 'cdc-consumer-*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$jar_path" \
    && cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
