#!/bin/bash
# test-java-druid.sh
# Tests DocArchitect against Apache Druid distributed OLAP database

set -e

PROJECT_DIR="test-projects/druid"

echo "=========================================="
echo "Java Distributed Database Test"
echo "Project: Apache Druid"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Apache Druid..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Apache Druid..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/apache/druid.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Apache Druid"
  version: "28.0"
  description: "Distributed OLAP Database"

repositories:
  - name: "druid"
    path: "."

scanners:
  enabled:
    - maven-dependencies
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
echo "Running DocArchitect on Apache Druid..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/druid"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/druid:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Apache Druid scan complete."
echo "  Results: output/druid/"
echo ""
echo "Expected outputs:"
echo "  - 100+ Maven modules"
echo "  - 200+ Maven dependencies"
echo "  - 50+ REST API endpoints"
echo "  - Complex module dependency graph"
echo ""
