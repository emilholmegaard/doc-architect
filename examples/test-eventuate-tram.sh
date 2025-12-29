#!/bin/bash
# test-eventuate-tram.sh
# Tests DocArchitect against Eventuate Tram saga orchestration with messaging

set -e

PROJECT_DIR="test-projects/eventuate-tram-sagas"

echo "=========================================="
echo "Eventuate Tram Saga Test"
echo "Project: Eventuate Tram Sagas Examples"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Eventuate Tram examples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Eventuate Tram examples..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/eventuate-tram/eventuate-tram-sagas-examples-customers-and-orders.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Eventuate Tram Sagas"
  version: "1.0.0"
  description: "Saga orchestration with event-driven microservices"

repositories:
  - name: "eventuate-sagas"
    path: "."

scanners:
  enabled:
    - maven-dependencies
    - spring-rest-api
    - jpa-entities
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
echo "Running DocArchitect on Eventuate Tram sagas..."
mkdir -p "$(pwd)/output/eventuate-tram"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/eventuate-tram:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Eventuate Tram scan complete."
echo "  Results: output/eventuate-tram/"
echo ""
echo "Expected outputs:"
echo "  - 3+ microservices (Order, Customer, etc.)"
echo "  - 15+ Kafka message flows (domain events)"
echo "  - Saga orchestration patterns"
echo "  - Event sourcing entities"
echo ""
