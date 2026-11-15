# --- Сборка ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Сначала только pom.xml: слой с зависимостями кэшируется, пока pom не менялся.
COPY pom.xml .
RUN mvn -B -q dependency:resolve dependency:resolve-plugins
COPY src ./src
RUN mvn -B -q -DskipTests package

# --- Запуск ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/vivamind-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
