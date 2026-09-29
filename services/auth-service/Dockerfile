FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY platform platform
COPY services services
RUN mvn -q -pl services/auth-service -am dependency:go-offline
RUN mvn -q -pl services/auth-service -am package -DskipTests

FROM eclipse-temurin:25-jre-jammy AS runtime-base
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/services/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R 10001:10001 /app
USER 10001
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM runtime-base AS local
COPY --from=build --chown=10001:10001 /workspace/services/auth-service/src/test/resources/keys /etc/auth

FROM runtime-base AS runtime
