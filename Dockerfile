FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/gym-tracker-0.0.1-SNAPSHOT.jar \
    /app/gym-tracker.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/gym-tracker.jar"]
