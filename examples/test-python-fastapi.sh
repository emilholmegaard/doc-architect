#!/bin/bash
# test-python-fastapi.sh
# Tests DocArchitect against Full-Stack-FastAPI-PostgreSQL

set -e

PROJECT_DIR="test-projects/fastapi-postgres"

echo "=========================================="
echo "Python FastAPI Test"
echo "Project: Full-Stack-FastAPI-PostgreSQL"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating FastAPI project..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning FastAPI project..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/tiangolo/full-stack-fastapi-postgresql.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Full Stack FastAPI"
  version: "1.0.0"
  description: "FastAPI + PostgreSQL + Celery Template"

repositories:
  - name: "fastapi-app"
    path: "./backend/app"

scanners:
  enabled:
    - pip-poetry-dependencies
    - fastapi-rest
    - sqlalchemy-entities

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
echo "Running DocArchitect on FastAPI project..."
# Create output directory with correct permissions before Docker mount
mkdir -p "$(pwd)/output/fastapi"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/fastapi:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ FastAPI scan complete."
echo "  Results: output/fastapi/"
echo ""
echo "Expected outputs:"
echo "  - 1 main component (FastAPI app)"
echo "  - Dependencies from requirements.txt/pyproject.toml"
echo "  - 20+ REST endpoints"
echo "  - 5+ SQLAlchemy entities"
echo "  - Celery task flows"
echo ""
