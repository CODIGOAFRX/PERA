FROM maven:3.9.11-eclipse-temurin-21 AS builder
ARG SERVICE
WORKDIR /workspace
COPY backend /workspace/backend
RUN mvn -f /workspace/backend/pom.xml -pl ":${SERVICE}" -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S pera && adduser -S pera -G pera
WORKDIR /app
COPY --from=builder "/workspace/backend/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar" /app/application.jar
USER pera
EXPOSE 8080 8081 8082 8083 8084 8085 8086 8087
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/application.jar"]
