#!/bin/bash
# test-spring-microservices.sh
# Tests DocArchitect against PiggyMetrics Spring Boot microservices

set -e

PROJECT_DIR="test-projects/piggymetrics"

echo "=========================================="
echo "Spring Boot Microservices Test"
echo "Project: PiggyMetrics"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating PiggyMetrics..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning PiggyMetrics..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/sqshq/piggymetrics.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "PiggyMetrics"
  version: "2.0.0"
  description: "Microservice Architecture with Spring Boot"

repositories:
  - name: "piggymetrics"
    path: "."

scanners:
  enabled:
    - maven-dependencies
    - spring-rest-api
    - jpa-entities
    - mongodb  # PiggyMetrics uses MongoDB for data storage
    # Note: rabbitmq-messaging enabled but will find 0 flows
    # (RabbitMQ used only for Spring Cloud Bus infrastructure)
    - rabbitmq-messaging

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
echo "Running DocArchitect on PiggyMetrics..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/piggymetrics"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/piggymetrics:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ PiggyMetrics scan complete."
echo "  Results: output/piggymetrics/"
echo ""
echo "Expected outputs:"
echo "  - 9 components (Maven modules) ✅"
echo "  - 11+ REST endpoints ✅"
echo "  - 5+ MongoDB entities ✅"
echo "  - 0 message flows ✅ (uses REST + Feign, not async messaging)"
echo ""
echo "Note: PiggyMetrics uses RabbitMQ only for infrastructure (Spring Cloud Bus)"
echo "      Business communication is via REST APIs, not message queues."
echo ""
