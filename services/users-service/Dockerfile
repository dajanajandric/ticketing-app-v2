# Faza 1: build - kompajlira i pakuje JAR unutar kontejnera (ne treba lokalna Java/Maven)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Faza 2: runtime - samo JRE + JAR, bez Maven-a i izvornog koda
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
