# syntax=docker/dockerfile:1
# login-service — gRPC 9090; client to signup:9090. Maven ≥ 3.9.11.
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY auth-proto auth-proto
COPY signup-service/pom.xml signup-service/pom.xml
COPY login-service login-service
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn -B -pl login-service -am -Dmaven.test.skip=true package \
    && cp login-service/target/login-service-0.1.0-SNAPSHOT.jar /build/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
COPY --from=build /build/app.jar /app/app.jar
USER app
ENV GRPC_PORT=9090
ENV GRPC_SERVER_PORT=9090
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
