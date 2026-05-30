FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q verify

FROM build AS infra-test
ARG INFRA_TEST
RUN mvn -q -Dtest=${INFRA_TEST} test

FROM scratch AS coverage
COPY --from=build /workspace/target/site/jacoco /jacoco
COPY --from=build /workspace/target/surefire-reports /surefire-reports

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/streaming-service-*.jar /app/streaming-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/streaming-service.jar"]
