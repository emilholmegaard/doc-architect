#!/bin/bash
# test-java-keycloak.sh
# Tests DocArchitect against Keycloak identity and access management platform

set -e

PROJECT_DIR="test-projects/keycloak"

echo "=========================================="
echo "Java Identity Management Test"
echo "Project: Keycloak"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Keycloak..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Keycloak..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/keycloak/keycloak.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Keycloak"
  version: "24.0"
  description: "Identity and Access Management Platform"

repositories:
  - name: "keycloak"
    path: "."

scanners:
  enabled:
    - maven-dependencies
    - jaxrs-api
    - jpa-entities

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
echo "Running DocArchitect on Keycloak..."
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/keycloak:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Keycloak scan complete."
echo "  Results: output/keycloak/"
echo ""
echo "Expected outputs:"
echo "  - 50+ components (Maven modules)"
echo "  - 500+ JAX-RS API endpoints (vs 0 before)"
echo "  - 150+ JPA entities"
echo "  - Complex module dependencies"
echo ""
