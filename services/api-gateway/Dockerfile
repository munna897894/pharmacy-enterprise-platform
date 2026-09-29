FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY pom.xml .
COPY platform platform
COPY services services
RUN mvn -q -pl services/api-gateway -am dependency:go-offline
RUN mvn -q -pl services/api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/services/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
RUN chown 10001:10001 /app/app.jar
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["java","-jar","/app/app.jar"]
