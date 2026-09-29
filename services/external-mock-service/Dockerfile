FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY platform platform
COPY services services
RUN mvn -q -pl services/external-mock-service -am dependency:go-offline
RUN mvn -q -pl services/external-mock-service -am package -DskipTests

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/services/external-mock-service/target/external-mock-service-0.0.1-SNAPSHOT.jar app.jar
RUN chown 10001:10001 /app/app.jar
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["java","-jar","/app/app.jar"]
