---
id: adr-002
title: ADR-002 - Use C4 Model for Architecture Documentation
description: Decision to adopt the C4 model for visualizing and documenting software architecture
tags:
  - adr
  - documentation
  - c4-model
  - visualization
status: Accepted
date: 2024-01-20
---

# ADR-002: Use C4 Model for Architecture Documentation

| Metadata | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2024-01-20 |
| **Deciders** | Architecture Team |
| **Tags** | documentation, visualization, c4-model |

## Context

The architecture documentation of our software systems has been primarily text-based, which while thorough, has led to challenges in quickly conveying the system's structure and interactions to both technical and non-technical stakeholders. As our systems have grown in complexity, the limitations of this approach have become more pronounced. There is a need for a more effective way to visualize and document our software architecture that can scale with our systems and be easily understood by all stakeholders.

## Decision

After evaluating several options, we have decided to adopt the C4 model as our standard for architecture documentation. The C4 model allows for a hierarchical approach to diagramming, starting with a high-level context diagram and progressively diving deeper into the container, component, and code levels as needed. This approach provides the flexibility to document at varying levels of detail and helps in managing the complexity of our systems.

## Consequences

Adopting the C4 model will change how we document our architecture. We will need to create new diagrams for our existing systems to bring them in line with the C4 model. This will require an initial investment of time and resources, but it is expected to pay off in the long term as the documentation will be more useful and easier to maintain. All new projects will use the C4 model from the outset.

## Links

- [C4 Model Website](https://c4model.com/)
- [C4 Model GitHub Repository](https://github.com/structurizr/c4-plantuml)
- [PlantUML Documentation](https://plantuml.com/)