#!/bin/bash
# test-kafka-spring-cloud-stream.sh
# Tests DocArchitect against Spring Cloud Stream samples with real Kafka messaging

set -e

PROJECT_DIR="test-projects/spring-cloud-stream-samples"

echo "=========================================="
echo "Kafka Spring Cloud Stream Test"
echo "Project: Spring Cloud Stream Samples"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Spring Cloud Stream samples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Spring Cloud Stream samples..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/spring-cloud/spring-cloud-stream-samples.git "$PROJECT_DIR"
fi

# Scan kafka-streams-samples subdirectory
SCAN_DIR="$PROJECT_DIR/kafka-streams-samples"

# Create DocArchitect config
cat > "$SCAN_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Spring Cloud Stream Kafka Samples"
  version: "1.0.0"
  description: "Event-driven microservices with Kafka"

repositories:
  - name: "kafka-samples"
    path: "."

scanners:
  enabled:
    - maven-dependencies
    - spring-rest-api
    - rest-event-flow  # Detect REST-based event flows and CRUD patterns
    - kafka-messaging
    - spring-components

generators:
  default: mermaid
  enabled:
    - mermaid
    - markdown

output:
  directory: "./docs/architecture"
  generateIndex: true
EOF

# Run DocArchitect
echo ""
echo "Running DocArchitect on Kafka samples..."
mkdir -p "$(pwd)/output/kafka-stream-samples"
docker run --rm \
    -v "$(pwd)/$SCAN_DIR:/workspace:ro" \
    -v "$(pwd)/output/kafka-stream-samples:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Kafka Stream samples scan complete."
echo "  Results: output/kafka-stream-samples/"
echo ""
echo "Expected outputs:"
echo "  - 5+ components (Kafka stream processors)"
echo "  - 10+ Maven dependencies"
echo "  - 8+ Kafka message flows (topics and consumers)"
echo "  - Kafka Streams topology"
echo ""
