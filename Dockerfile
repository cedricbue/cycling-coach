# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Dependency layer — only invalidated when pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
COPY frontend ./frontend
RUN mkdir -p data && mvn package -q -DskipTests

# ── Stage 2: extract Spring Boot layers ─────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS extract
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 3: run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Layers ordered by change frequency — framework layers cached, app layer rebuilt on each release
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
