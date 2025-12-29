#!/bin/bash
# test-python-saleor.sh
# Tests DocArchitect against Saleor GraphQL e-commerce platform

set -e

PROJECT_DIR="test-projects/saleor"

echo "=========================================="
echo "Python GraphQL E-commerce Test"
echo "Project: Saleor"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Saleor..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Saleor..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/saleor/saleor.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Saleor"
  version: "3.0"
  description: "GraphQL E-commerce Platform with Django"

repositories:
  - name: "saleor"
    path: "."

scanners:
  enabled:
    - pip-poetry-dependencies
    - django-apps
    - graphql-schema
    - django-orm
    - celery-tasks

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
echo "Running DocArchitect on Saleor..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/saleor"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/saleor:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Saleor scan complete."
echo "  Results: output/saleor/"
echo ""
echo "Expected outputs:"
echo "  - 15+ components (Django apps)"
echo "  - 200+ GraphQL types and operations"
echo "  - 100+ Django ORM models"
echo "  - GraphQL schema documentation"
echo ""
