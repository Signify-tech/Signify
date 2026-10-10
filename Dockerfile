# Build from the repository root: docker build -t signify .
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build/backend
COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S signify && adduser -S -G signify signify
WORKDIR /app
COPY --from=build /build/backend/target/signify-backend-0.1.0.jar ./signify.jar
COPY data/vocabulary.csv ./data/vocabulary.csv
COPY media/ ./media/
# Assets are immutable image contents; no upload or persistent disk is required.
RUN chmod -R a-w /app && chmod -R a+rX /app
ENV SPRING_PROFILES_ACTIVE=production \
    SIGNIFY_MEDIA_DIR=/app/media \
    SIGNIFY_SEED_FILE=/app/data/vocabulary.csv \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=16m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
USER signify
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/signify.jar"]
