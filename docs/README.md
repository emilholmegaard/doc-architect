# Documentation Structure

This directory contains the DocArchitect documentation built with MkDocs and TechDocs.

## Dynamic Navigation

The documentation uses the `awesome-pages` plugin for dynamic navigation:

- **Main sections** are defined in `docs/.pages`
- **ADR section** automatically discovers all ADR files in `docs/adrs/`
- **No manual maintenance** required when adding new ADRs

### Adding a New ADR

1. Create your ADR file: `docs/adrs/NNN-short-title.md`
2. Follow the template in `docs/adrs/TEMPLATE.md`
3. The ADR will automatically appear in the navigation menu

### Adding New Documentation

1. Create your markdown file in `docs/`
2. If it's part of an existing section, update `docs/.pages` to include it
3. If it's a new section, add it to `docs/.pages`

## Building Locally

```bash
# Install dependencies
pip install -r docs-requirements.txt

# Build documentation
mkdocs build

# Serve locally with live reload
mkdocs serve
```

Visit http://localhost:8000 to view the documentation.

## CI/CD

Documentation is automatically built and deployed to GitHub Pages on every push to `main`:

- GitHub Actions workflow: `.github/workflows/techdocs.yml`
- Published to: https://emilholmegaard.github.io/doc-architect/

## Structure

```
docs/
├── .pages                      # Main navigation configuration
├── README.md                   # This file
├── index.md                    # Homepage
├── architecture-overview.md    # Getting started
├── onboarding-guide.md
├── adrs/                       # Architecture Decision Records
│   ├── .pages                  # ADR navigation (auto-discovery)
│   ├── TEMPLATE.md             # ADR template
│   ├── 001-*.md                # Individual ADRs (auto-discovered)
│   ├── 002-*.md
│   └── ...
└── ...                         # Other documentation files
```

## Configuration Files

- **mkdocs.yml**: Main MkDocs configuration (theme, plugins, extensions)
- **docs/.pages**: Navigation structure for main sections
- **docs/adrs/.pages**: Navigation structure for ADRs (template first, then auto-discover)
- **docs-requirements.txt**: Python dependencies for building docs

