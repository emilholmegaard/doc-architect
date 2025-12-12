# Multi-stage Docker build for DocArchitect
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy POM first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="DocArchitect Team"
LABEL org.opencontainers.image.source="https://github.com/emilholmegaard/doc-architect"
LABEL org.opencontainers.image.description="Automated Architecture Documentation Generator"
LABEL org.opencontainers.image.licenses="MIT"

# Install runtime dependencies
RUN apk add --no-cache \
    git \
    bash \
    && rm -rf /var/cache/apk/*

# Create non-root user for security
RUN addgroup -S docarchitect && \
    adduser -S docarchitect -G docarchitect

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create necessary directories
RUN mkdir -p /workspace /output && \
    chown -R docarchitect:docarchitect /app /workspace /output

# Switch to non-root user
USER docarchitect

# Volumes for workspace and output
VOLUME ["/workspace", "/output"]

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD java -version || exit 1

# Entry point
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--help"]
