# CLAUDE.md – Development Guidelines

## Tech stack

- Java 21, Spring Boot 3.x, Gradle (Kotlin DSL), JUnit 5, Testcontainers, Docker  
- Docs managed via **Backstage TechDocs** (ADR + feature docs in `/docs`)

---

## Principles

- **KISS:** simplest solution first  
- **Clean Code & SOLID:** small functions, clear names, single responsibility, composition over inheritance  
- **Tests & Docs:** every change must include unit/integration tests + ADR or feature doc  
- **Security & Observability:** validate inputs, no secrets in code, structured logs, metrics

---

## Architecture

```
src/main/java/io/holmegaard/docarchitect/
  api/            # Controllers, DTOs
  application/    # Services, use cases
  domain/         # Entities, value objects, ports
  infrastructure/ # Persistence, messaging, clients
```

- DTOs never expose domain objects  
- Config via profiles (`local`, `test`, `prod`) and env vars  

---

## Git & Review

- Branches: `feature/<id>-<name>`, `hotfix/<id>-<name>`  
- Commits: Conventional Commits (feat, fix, docs…)  
- PRs: small scope, 2 human approvals, include tests + docs  

---

## Testing & CI

- Unit tests for domain/services  
- Slice tests (`@WebMvcTest`, `@DataJpaTest`)  
- Integration tests with Testcontainers  
- Coverage ≥ 80% for core logic  
- CI: build → lint → tests → coverage → docker build → publish  

---

## Documentation

- **ADR:** record major decisions (problem, options, decision, consequences)  
- **Feature doc:** describe endpoints, inputs/outputs, behavior, tests, observability, security  
- Stored in `/docs` and rendered in Backstage TechDocs  

---

## Deployment

- Gradle reproducible builds  
- Flyway migrations for DB  
- Docker multi-stage build, non-root user, healthcheck via `/actuator/health`  
- Versioning: SemVer, tag Git + Docker images  

---

This trimmed version is **Backstage-ready** and keeps Claude focused on coding with the right guardrails.  

👉 Do you want me to also generate the **`catalog-info.yaml` + `mkdocs.yml`** boilerplate so you can plug this straight into Backstage?
