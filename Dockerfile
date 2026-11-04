FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system normbuild \
    && useradd --system --gid normbuild --home-dir /app normbuild
WORKDIR /app
COPY --from=build --chown=normbuild:normbuild /workspace/target/normbuild-api-0.1.0.jar /app/application.jar
USER normbuild
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
