#!/bin/bash
# test-apache-camel.sh
# Tests DocArchitect against Apache Camel examples
#
# Repository: https://github.com/apache/camel-examples
# Contains: EIP routes, messaging patterns, and system integrations

set -e

PROJECT_DIR="test-projects/camel-examples"

echo "=========================================="
echo "Apache Camel Examples Test"
echo "Project: Apache Camel Integration Examples"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Apache Camel examples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Apache Camel examples..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/apache/camel-examples.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Apache Camel Examples"
  version: "1.0.0"
  description: "Integration framework with Enterprise Integration Patterns"

repositories:
  - name: "camel-examples"
    path: "."


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
echo "Running DocArchitect on Apache Camel examples..."
mkdir -p "$(pwd)/output/apache-camel"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/apache-camel:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Apache Camel examples scan complete."
echo "  Results: output/apache-camel/"
echo ""
echo "Expected outputs:"
echo "  - 50+ components (example routes and applications)"
echo "  - 100+ Maven dependencies (Camel components)"
echo "  - 20+ message flows (Kafka, AMQP, JMS, ActiveMQ)"
echo "  - EIP route definitions (Content-Based Router, Splitter, etc.)"
echo ""
echo "Notable EIP patterns:"
echo "  - Content-Based Router - Route messages based on content"
echo "  - Message Filter - Filter messages based on criteria"
echo "  - Splitter - Split composite messages"
echo "  - Aggregator - Combine related messages"
echo "  - Recipient List - Send message to dynamic list of recipients"
echo "  - Wire Tap - Route copy of message to secondary destination"
echo ""
echo "Integration components:"
echo "  - Kafka - camel-kafka examples"
echo "  - RabbitMQ - camel-rabbitmq examples"
echo "  - JMS/ActiveMQ - camel-jms examples"
echo "  - REST - camel-rest examples"
echo "  - Database - camel-jdbc, camel-jpa examples"
echo "  - File - camel-file examples"
echo ""
echo "Camel DSL:"
echo "  - Java DSL - Route builders in Java"
echo "  - XML DSL - Route definitions in XML"
echo "  - YAML DSL - Route definitions in YAML"
echo ""
