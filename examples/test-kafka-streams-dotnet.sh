#!/bin/bash
# test-kafka-streams-dotnet.sh
# Tests DocArchitect against Confluent .NET Kafka client with stream processing
#
# Repository: https://github.com/confluentinc/confluent-kafka-dotnet
# Contains: Producer, consumer, transactions, and stream processing examples

set -e

PROJECT_DIR="test-projects/confluent-kafka-dotnet"

echo "=========================================="
echo "Kafka Streams .NET Examples Test"
echo "Project: Confluent Kafka .NET Client"
echo "=========================================="

# Clone or update
if [ -d "$PROJECT_DIR" ]; then
    echo "Updating Kafka .NET examples..."
    cd "$PROJECT_DIR" && git pull && cd ../..
else
    echo "Cloning Kafka .NET examples..."
    mkdir -p test-projects
    git clone --depth 1 https://github.com/confluentinc/confluent-kafka-dotnet.git "$PROJECT_DIR"
fi

# Create DocArchitect config (focus on examples directory)
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Confluent Kafka .NET Examples"
  version: "1.0.0"
  description: "Kafka producer, consumer and stream processing with .NET"

repositories:
  - name: "kafka-dotnet-examples"
    path: "examples"

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
echo "Running DocArchitect on Kafka .NET examples..."
mkdir -p "$(pwd)/output/kafka-streams-dotnet"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/kafka-streams-dotnet:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

echo ""
echo "✓ Kafka .NET examples scan complete."
echo "  Results: output/kafka-streams-dotnet/"
echo ""
echo "Expected outputs:"
echo "  - 5+ components (example applications)"
echo "  - 10+ NuGet dependencies (Confluent.Kafka, etc.)"
echo "  - 8+ Kafka message flows (producers and consumers)"
echo "  - Transaction support and exactly-once semantics examples"
echo ""
echo "Notable examples:"
echo "  - Consumer - Message consumption with offset management"
echo "  - Producer - Async message production"
echo "  - ExactlyOnce - Word count with exactly-once semantics"
echo "  - AdminClient - Topic and configuration management"
echo ""
