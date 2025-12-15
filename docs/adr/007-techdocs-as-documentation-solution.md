---
id: adr-007
title: ADR 007 - TechDocs as Documentation Solution
description: Decision to adopt TechDocs as the primary documentation solution
authors:
  - name: Architecture Team
decision_date: 2024-01-15
status: accepted
tags:
  - documentation
  - backstage
  - techdocs
  - infrastructure
---

# ADR 007: TechDocs as Documentation Solution

## Status

Accepted

## Context

We need a centralized, scalable documentation solution that integrates with our development workflow. Our team requires a tool that supports multiple documentation formats, enables easy collaboration, and provides discoverability of documentation across the organization.

Current challenges:

- Documentation scattered across multiple platforms
- Difficult to keep documentation synchronized with code
- Limited discoverability and search capabilities
- No standardized documentation structure
- Developers spend excessive time searching for information

## Decision

We have decided to adopt **TechDocs** (Backstage TechDocs) as our primary documentation solution.

TechDocs provides:

- MkDocs-based documentation as code approach
- Integration with Backstage for centralized discovery
- Support for Markdown documentation stored alongside code
- Built-in search and discoverability features
- CI/CD integration for automatic documentation generation and deployment

## Consequences

### Positive

- Documentation becomes version-controlled and treated as code
- Automatic documentation generation reduces manual overhead
- Centralized discovery through Backstage portal
- Enables teams to maintain documentation closer to code
- Supports multiple documentation formats via MkDocs plugins
- Easy collaboration through standard git workflows

### Negative

- Initial setup and migration effort required
- Team training needed for MkDocs and TechDocs conventions
- Dependency on Backstage infrastructure
- Requires maintaining documentation generation pipeline

## Alternatives Considered

### 1. Confluence

- Pros: User-friendly, real-time collaboration
- Cons: Expensive, documentation separate from code, limited version control

### 2. GitBook

- Pros: Beautiful UI, git-based
- Cons: Proprietary tool, limited customization, external dependency

### 3. Notion

- Pros: Flexible, collaborative, modern UI
- Cons: Not code-integrated, no version control, search limitations

### 4. Wiki (custom)

- Pros: Full control, open source
- Cons: High maintenance overhead, limited built-in features

## Implementation Notes

- Migrate existing documentation to MkDocs format
- Set up automated documentation publishing in CI/CD
- Configure TechDocs in Backstage instance
- Establish documentation standards and guidelines
- Provide team training and onboarding

## Related ADRs

- ADR 006: Backstage as Developer Portal (Accepted)
