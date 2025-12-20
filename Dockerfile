# Multi-stage Docker build for DocArchitect
# Stage 1: Build the application
FROM maven:3-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy parent POM and module POMs for better layer caching
COPY pom.xml .
COPY doc-architect-core/pom.xml ./doc-architect-core/
COPY doc-architect-cli/pom.xml ./doc-architect-cli/

# Download dependencies for all modules
RUN mvn dependency:go-offline -B

# Copy source code for all modules
COPY doc-architect-core/src ./doc-architect-core/src
COPY doc-architect-cli/src ./doc-architect-cli/src

# Build the multi-module project (skip tests in Docker build)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

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

# Copy built CLI JAR from build stage (the shaded JAR with all dependencies)
# Note: Maven Shade plugin creates doc-architect-cli-*.jar (shaded) and original-*.jar (without deps)
COPY --from=build /app/doc-architect-cli/target/doc-architect-cli-*.jar app.jar

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
