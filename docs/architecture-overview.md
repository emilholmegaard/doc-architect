---
id: architecture-overview
title: Architecture Overview
description: Overview of the system architecture and design principles
tags:
  - architecture
  - overview
  - design-principles
---

# Architecture Overview

## System Purpose

DocArchitect is an automated architecture documentation generator that scans source code across multiple languages and technologies to produce comprehensive architectural diagrams, dependency graphs, and API documentation.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      DocArchitect CLI (Picocli)                     │
│              (Commands: init, scan, diff, generate)                 │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
    ┌─────────────┐  ┌──────────────┐  ┌───────────────┐
    │  Scanners   │  │ Configuration│  │ Output Manager│
    │  (SPI-based)│  │  (YAML)      │  │               │
    └─────────────┘  └──────────────┘  └───────────────┘
         │
         ▼
    ┌─────────────────────────┐
    │ ArchitectureModel       │
    │ (Intermediate Format)   │
    └────────┬────────────────┘
             │
         ┌───┴────┬────────────┐
         ▼        ▼            ▼
    ┌────────┐ ┌────────┐ ┌──────────┐
    │Mermaid │ │PlantUML│ │D2/Custom │
    │Generator│ │Generator│ │Generators│
    └────────┘ └────────┘ └──────────┘
         │        │            │
         └────────┼────────────┘
                  ▼
         ┌─────────────────────┐
         │ OutputRenderer      │
         │ (Markdown + Diagrams)
         └─────────────────────┘
```

## Core Components

### 1. CLI Layer (Picocli)

- **Entry point** for all operations
- **Commands**: `init`, `scan`, `diff`, `generate`
- Handles parameter parsing and orchestration
- Supports lightweight CI/CD mode for detecting changes

### 2. Configuration System

- **Format**: YAML (`docarchitect.yaml`)
- **Scope**: Project metadata, repository definitions, scanner selection, output preferences
- **Features**: Single repo (mono-repo) or multi-repo support with optional URL cloning

### 3. Scanner Framework (SPI-based)

Plugin architecture using Java ServiceLoader for extensibility:

#### Dependency Scanners

- Maven (pom.xml), Gradle
- npm/yarn, pip/poetry
- NuGet (.csproj), Go modules

#### API Scanners

- REST: Spring MVC, JAX-RS, FastAPI, Flask, ASP.NET Core, Express.js
- GraphQL, gRPC/Protobuf

#### Database Scanners

- ORM/Schema: JPA/Hibernate, SQLAlchemy, Django ORM, Entity Framework, Mongoose
- SQL migrations

#### Messaging Scanners

- Event Streaming: Kafka, RabbitMQ, Azure Service Bus
- Schema Detection: Avro, AsyncAPI

#### Structure Scanners

- Module/service boundary detection
- Layer analysis
- Sokrates scope file generation

### 4. Intermediate Model (ArchitectureModel)

- **Purpose**: Language-agnostic representation of discovered architecture
- **Contents**: Services, dependencies, APIs, data models, message flows, integrations
- **Benefits**: Decouples scanners from generators

### 5. Generator Framework

Transforms the intermediate model into multiple formats:

- **Mermaid**: Flowcharts, dependency graphs, ER diagrams, sequence diagrams
- **PlantUML**: C4 diagrams, component diagrams
- **D2/Structurizr DSL**: Advanced visualizations
- **Markdown**: Documentation with embedded diagrams

### 6. Output Management

Generates organized documentation structure:

```
docs/architecture/
├── overview/           # C4 Context & Container diagrams
├── components/         # Per-service documentation
├── dependencies/       # Dependency graphs & matrices
├── api/               # REST, GraphQL, gRPC catalogs
├── data/              # ER diagrams & entity docs
├── messaging/         # Kafka, event flow diagrams
└── integration/       # Sokrates scope, OpenAPI export
```

## Design Principles

### 1. **Plugin Architecture**

- **Implementation**: Java SPI (ServiceLoader)
- **Benefit**: Users can add custom scanners without modifying core
- **Deployment**: Mount custom JAR in Docker

### 2. **Multi-Language Support**

- Support for 6+ languages/ecosystems (Java, Kotlin, Python, C#/.NET, Node.js, Go)
- **Strategy**: Language-specific scanner implementations

### 3. **Multi-Diagram Format Support**

- Avoids lock-in to single visualization tool
- **Flexibility**: Intermediate model allows future formats without re-scanning

### 4. **Configuration-Driven**

- Single YAML file controls behavior
- **Advantages**: Version-controlled, reproducible scans, multi-repo support

### 5. **Docker-First Deployment**

- **Benefit**: Runs without language runtime dependencies installed
- **Use case**: CI/CD, developer machines without build tools

### 6. **Incremental Analysis**

- **Diff command**: Detects breaking changes vs. baseline
- **Use case**: Lightweight CI/CD checks without full re-scan

## Data Flow

```
1. CLI Parse Config (docarchitect.yaml)
   ↓
2. Initialize/Clone Repositories
   ↓
3. Run Enabled Scanners
   - Parallel execution where possible
   - Each scanner produces intermediate model contributions
   ↓
4. Merge Scanner Results → ArchitectureModel
   ↓
5. Execute Generators (Mermaid, PlantUML, etc.)
   ↓
6. Render Output
   - Write Markdown files
   - Embed diagrams
   - Generate index/navigation
   ↓
7. Output to Directory (./docs/architecture or custom)
```

## Extensibility Points

1. **Custom Scanners**: Implement `Scanner` interface, register via SPI
2. **Custom Generators**: Add diagram format handler
3. **Configuration**: Add new YAML properties for custom behavior
4. **Output Renderers**: Customize markdown generation and formatting

## Deployment Model

- **Docker Image**: `ghcr.io/emilholmegaard/doc-architect:latest`
- **Volume Mounts**:
  - `/workspace` - Source code & config
  - `/output` - Generated documentation
- **No Dependencies**: Self-contained; users don't need language runtimes

## Integration Points

- **Sokrates**: Generated scope file for code metrics
- **OpenAPI**: REST API export
- **AsyncAPI**: Messaging schema export
- **CI/CD**: Diff command for automated change detection
