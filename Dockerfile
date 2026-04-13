# Multi-stage build for Spring Boot application with Maven
# Stage 1: Builder - Maven build
FROM maven:3.9-eclipse-temurin-17 as builder

WORKDIR /build

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -DskipTests

# Copy source code
COPY src ./src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime - Java execution (Debian-based for better runtime compatibility)
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install curl and CA certificates for health checks and outbound TLS
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN useradd -u 1000 -m appuser

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar /app/app.jar
RUN chown -R appuser:appuser /app

USER appuser

# Prefer IPv4 in containerized environments where IPv6 routing can be flaky
ENV JAVA_OPTS="-Djava.net.preferIPv4Stack=true -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError -Dhibernate.query.plan_cache_max_size=128"

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD sh -c 'curl -fsS "http://localhost:${PORT:-8081}/users/health" || exit 1'

# Expose port
EXPOSE 8081

# Run the Spring Boot application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8081}"]
