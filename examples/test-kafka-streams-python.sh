#!/bin/bash
# test-kafka-streams-python.sh
# Tests DocArchitect against Faust stream processing library (Python)
#
# Repository: https://github.com/faust-streaming/faust
# Contains: Stream processing library porting Kafka Streams concepts to Python

set -e

PROJECT_DIR="test-projects/faust-streaming"

echo "=========================================="
echo "Kafka Streams Python (Faust) Test"
echo "Project: Faust Stream Processing"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Faust examples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Faust stream processing library..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/faust-streaming/faust.git "$PROJECT_DIR"
fi

# Create DocArchitect config (focus on examples directory)
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Faust Stream Processing"
  version: "1.0.0"
  description: "Python stream processing with Kafka - porting Kafka Streams to Python"

repositories:
  - name: "faust-examples"
    path: "examples"

scanners:
  enabled:
    - pip-poetry-dependencies
    - faust-streaming
    - django-apps

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
echo "Running DocArchitect on Faust Python examples..."
mkdir -p "$(pwd)/output/kafka-streams-python"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/kafka-streams-python:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Faust Python examples scan complete."
echo "  Results: output/kafka-streams-python/"
echo ""
echo "Expected outputs:"
echo "  - 3+ components (stream processing applications)"
echo "  - 15+ Python dependencies (faust-streaming, kafka-python, etc.)"
echo "  - 6+ Kafka message flows (agents, topics, tables)"
echo "  - Stream processing patterns (aggregations, joins, windowing)"
echo ""
echo "Notable features:"
echo "  - @app.agent decorators for stream processors"
echo "  - Faust Records for data models"
echo "  - Tables for stateful processing"
echo "  - Async/await stream processing"
echo ""
echo "Note: Faust brings Kafka Streams concepts to Python:"
echo "  - Agents = Stream processors"
echo "  - Topics = Kafka topics"
echo "  - Tables = KTables (changelog-backed state)"
echo ""
