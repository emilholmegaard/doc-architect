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
    - spring-mvc-api
    - jpa-entities
    - kafka-messaging  # Actually uses RabbitMQ, but test scanner detection

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
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/piggymetrics:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ PiggyMetrics scan complete."
echo "  Results: output/piggymetrics/"
echo ""
echo "Expected outputs:"
echo "  - 7+ components (one per service)"
echo "  - 20+ REST endpoints"
echo "  - 10+ MongoDB entities"
echo "  - Service-to-service relationships"
echo ""
