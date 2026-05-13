FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/streaming-service-*.jar /app/streaming-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/streaming-service.jar"]
