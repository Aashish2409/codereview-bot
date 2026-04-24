# ── Stage 1: Build the JAR ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first — Docker caches this layer so dependencies
# are only re-downloaded when pom.xml changes (not on every code change)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build (skip tests in Docker — run them in CI)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Minimal runtime image ────────────────────────────────────────────
# eclipse-temurin:17-jre-alpine is ~200MB vs ~600MB for the full JDK image
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user — never run Java apps as root in production
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the built JAR from Stage 1
COPY --from=build /app/target/codereview-bot-1.0.0.jar app.jar

# Expose the port Spring Boot listens on
EXPOSE 8080

# Start the app
# -XX:+UseContainerSupport  → respect Docker memory limits (JVM-aware)
# -XX:MaxRAMPercentage=75.0 → use max 75% of container RAM for heap
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]
