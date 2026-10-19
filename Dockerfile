# ==============================================================================
# Multi-stage Dockerfile for Hostel Complaint & Maintenance System (HCMS)
# Stage 1: Build stage with Maven and Eclipse Temurin JDK 21
# Stage 2: Runtime stage with minimal Eclipse Temurin JRE 21 & non-root user
# ==============================================================================

# ----------------- STAGE 1: Build -----------------
FROM maven:3.9.6-eclipse-temurin-21-jammy AS builder

WORKDIR /build

# Copy root pom and module poms for dependency caching
COPY pom.xml ./
COPY hcms-core/pom.xml hcms-core/
COPY hcms-persistence/pom.xml hcms-persistence/
COPY hcms-service/pom.xml hcms-service/
COPY hcms-web/pom.xml hcms-web/

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B || true

# Copy all source code
COPY hcms-core/src hcms-core/src
COPY hcms-persistence/src hcms-persistence/src
COPY hcms-service/src hcms-service/src
COPY hcms-web/src hcms-web/src

# Build, test, and shade package
RUN mvn clean package -DskipTests=false -B

# ----------------- STAGE 2: Runtime -----------------
FROM eclipse-temurin:21-jre-jammy AS runner

# Create dedicated non-root application user and group
RUN groupadd -g 10001 hcmsgroup && \
    useradd -u 10001 -g hcmsgroup -m -s /bin/bash hcmsuser

WORKDIR /app

# Install curl for container health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Copy shaded fat JAR from builder stage
COPY --from=builder --chown=hcmsuser:hcmsgroup /build/hcms-web/target/hcms-web-1.0.0.jar /app/hcms-app.jar

# Switch to unprivileged non-root user
USER hcmsuser:hcmsgroup

# Expose HTTP port
EXPOSE 8080

# Environment defaults
ENV PORT=8080 \
    APP_PROFILE=dev \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Container healthcheck using Javalin health endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Launch the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/hcms-app.jar"]
