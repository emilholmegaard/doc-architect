#!/bin/bash
# test-ruby-gitlab.sh
# Tests DocArchitect against GitLab DevOps platform

set -e

PROJECT_DIR="test-projects/gitlab"

echo "=========================================="
echo "Ruby Rails DevOps Platform Test"
echo "Project: GitLab"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating GitLab..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning GitLab..."
    mkdir -p test-projects
    git clone --depth 1 https://gitlab.com/gitlab-org/gitlab.git "$PROJECT_DIR"
fi

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "GitLab"
  version: "16.0"
  description: "Complete DevOps Platform with Rails Backend"

repositories:
  - name: "gitlab"
    path: "."

scanners:
  enabled:
    - graphql-schema
    # Note: bundler-dependencies and rails-api scanners not yet implemented

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
echo "Running DocArchitect on GitLab..."
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/gitlab:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ GitLab scan complete."
echo "  Results: output/gitlab/"
echo ""
echo "Expected outputs:"
echo "  - 50+ components (Rails engines/modules)"
echo "  - 200+ Ruby gem dependencies"
echo "  - 500+ REST API endpoints"
echo "  - 300+ GraphQL types and operations"
echo "  - 200+ database models"
echo ""
