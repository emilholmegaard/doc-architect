#!/bin/bash
# test-java-openhab.sh
# Tests DocArchitect against openHAB Core home automation platform

set -e

PROJECT_DIR="test-projects/openhab-core"

echo "=========================================="
echo "Java OSGi Home Automation Test"
echo "Project: openHAB Core"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating openHAB Core..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning openHAB Core..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/openhab/openhab-core.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "openHAB Core"
  version: "4.1"
  description: "Home Automation Platform with OSGi Architecture"

repositories:
  - name: "openhab-core"
    path: "."

scanners:
  enabled:
    - maven-dependencies
    - spring-components
    - spring-rest-api

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
echo "Running DocArchitect on openHAB Core..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/openhab"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/openhab:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ openHAB Core scan complete."
echo "  Results: output/openhab/"
echo ""
echo "Expected outputs:"
echo "  - 40+ OSGi bundles/components"
echo "  - 150+ Maven dependencies"
echo "  - 50+ REST API endpoints"
echo "  - OSGi modular architecture"
echo ""
