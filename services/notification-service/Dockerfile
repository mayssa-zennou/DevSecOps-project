FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -Dmaven.test.skip=true


FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]