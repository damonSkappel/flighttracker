# NOTE: .github/workflows/deploy.yml does NOT use this file. CI ships the bare
# jar via azure/webapps-deploy, so the ENTRYPOINT below (including the prod
# profile flag) does not run in production -- there the prod profile is active
# only because SPRING_PROFILES_ACTIVE is set in Azure App Settings. Keep both in
# sync, or switch the workflow to a container build.

# Stage 1 — Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2 — Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]