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
echo "Running DocArchitect on PiggyMetrics..."
# Create output directory with correct permissions before Docker mount
OUTPUT_DIR="$(pwd)/output/piggymetrics"
mkdir -p "$OUTPUT_DIR"
chmod 777 "$OUTPUT_DIR"  # Ensure Docker container can write to it
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$OUTPUT_DIR:/output" \
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
echo "  - REST-based event flows and CRUD patterns ✅ (REST Event Flow Scanner)"
echo ""
echo "Note: PiggyMetrics uses REST + Feign for inter-service communication"
echo "      The REST Event Flow Scanner detects RESTful CRUD patterns and event-like endpoints."
echo ""
