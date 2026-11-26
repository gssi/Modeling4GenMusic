# Build context: the repository root (the image also needs the metamodels folder).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml backend/pom.xml
RUN mvn -q -f backend/pom.xml dependency:go-offline
COPY metamodels metamodels
COPY fixtures fixtures
COPY backend/src backend/src
RUN mvn -q -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/backend/target/studio-backend-*.jar app.jar
COPY metamodels metamodels
ENV STUDIO_METAMODELS_DIR=/app/metamodels \
    STUDIO_WORKSPACE_DIR=/workspace
VOLUME /workspace
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
