# ---- Stage 1: build the jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Dependencies first: this layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Windows checkouts may have CRLF line endings, which break the script on Linux
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw --batch-mode dependency:go-offline

# Then the sources. Tests are skipped here: they need Docker (Testcontainers),
# which is not available inside a build. They run locally and in CI.
COPY src src
RUN ./mvnw --batch-mode package -Dmaven.test.skip=true

# ---- Stage 2: runtime image ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Never run the application as root
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]