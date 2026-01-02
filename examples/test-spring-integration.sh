#!/bin/bash
# test-spring-integration.sh
# Tests DocArchitect against Spring Integration samples
#
# Repository: https://github.com/spring-projects/spring-integration-samples
# Contains: Enterprise Integration Patterns (EIP) with Spring Integration

set -e

PROJECT_DIR="test-projects/spring-integration-samples"

echo "=========================================="
echo "Spring Integration Samples Test"
echo "Project: Spring Integration EIP Patterns"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Spring Integration samples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Spring Integration samples..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/spring-projects/spring-integration-samples.git "$PROJECT_DIR"
fi

# Create DocArchitect config (focus on basic and intermediate samples)
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Spring Integration Samples"
  version: "1.0.0"
  description: "Enterprise Integration Patterns with Spring Integration"

repositories:
  - name: "spring-integration-samples"
    path: "."

scanners:
  mode: auto

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
echo "Running DocArchitect on Spring Integration samples..."
mkdir -p "$(pwd)/output/spring-integration"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/spring-integration:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Spring Integration samples scan complete."
echo "  Results: output/spring-integration/"
echo ""
echo "Expected outputs:"
echo "  - 30+ components (sample applications and modules)"
echo "  - 50+ Maven dependencies (Spring Integration modules)"
echo "  - 10+ message flows (JMS, AMQP, Kafka, File, HTTP)"
echo "  - Enterprise Integration Patterns implementations"
echo ""
echo "Notable EIP patterns:"
echo "  - Message Channel - Point-to-point and pub-sub channels"
echo "  - Message Router - Content-based routing"
echo "  - Message Translator - Data transformation"
echo "  - Message Filter - Selective message processing"
echo "  - Splitter/Aggregator - Message decomposition and recomposition"
echo "  - Service Activator - Message endpoint that invokes service"
echo ""
echo "Integration adapters:"
echo "  - AMQP (RabbitMQ) - examples/basic/amqp/"
echo "  - JMS - examples/basic/jms/"
echo "  - File - examples/basic/file/"
echo "  - HTTP - examples/basic/http/"
echo "  - JDBC - examples/basic/jdbc/"
echo "  - MongoDB - examples/basic/mongodb/"
echo ""
