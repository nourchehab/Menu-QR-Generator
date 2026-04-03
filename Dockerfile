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

# Stage 2: Runtime - Java execution
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN adduser -D -u 1000 appuser

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar /app/app.jar
RUN chown -R appuser:appuser /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8081}/users/health || exit 1

# Expose port
EXPOSE 8081

# Run the Spring Boot application
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT:-8081}"]
