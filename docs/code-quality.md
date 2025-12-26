# Code Quality Reports

DocArchitect uses automated code analysis to maintain high code quality and provide visibility into the codebase's health. Weekly analysis reports are generated using [Sokrates](https://github.com/zeljkoobrenovic/sokrates), a comprehensive code analysis tool.

## Accessing Reports

The latest code quality reports are available at:

- **[Latest Report](../sokrates/)** - Current week's analysis
- **[Report Archive](../sokrates/archive/)** - Historical reports (last 4 weeks)

## What's Analyzed

Sokrates provides detailed insights into various aspects of the codebase:

### Code Volume
- Lines of code by language
- Lines of code by component
- Growth trends over time

### Code Duplication
- Duplicate code blocks
- Duplication percentage
- Duplication hotspots

### Code Complexity
- Cyclomatic complexity metrics
- Complex methods and classes
- Complexity distribution

### File Age & Freshness
- Recently changed files
- Oldest files
- Change frequency

### Contributor Activity
- Commit patterns
- Active contributors
- Code ownership

## Report Schedule

Reports are generated automatically:

- **Frequency**: Every Monday at 2:00 AM UTC
- **Retention**: Last 4 weeks of reports are kept in the archive
- **Update Time**: Reports typically take 5-10 minutes to generate

You can also trigger a manual report generation by navigating to the [Sokrates Analysis workflow](https://github.com/emilholmegaard/doc-architect/actions/workflows/sokrates-analysis.yml) and clicking "Run workflow".

## Understanding the Reports

Each report includes:

1. **Overview** - High-level metrics and trends
2. **Source Code** - Detailed breakdown of source files
3. **Duplication** - Duplicate code analysis
4. **File Size** - Distribution of file sizes
5. **Unit Size** - Method/function size metrics
6. **Conditional Complexity** - Complexity analysis
7. **Concerns** - Cross-cutting concerns and patterns

## Configuration

Sokrates analysis is configured in the `_sokrates/config.json` file. This configuration:

- Defines which files to analyze
- Sets language-specific rules
- Configures metrics thresholds
- Specifies analysis scope

## Integration with CI/CD

The code quality reports are automatically integrated into our TechDocs deployment:

1. Sokrates workflow runs weekly and generates reports
2. Reports are uploaded as workflow artifacts
3. TechDocs workflow downloads the latest reports
4. Combined site (documentation + reports) is deployed to GitHub Pages

This ensures that quality metrics are always accessible alongside the project documentation.

## Taking Action

When the reports identify areas for improvement:

1. **High Duplication** - Consider extracting common code into reusable components
2. **High Complexity** - Refactor complex methods into smaller, focused functions
3. **Old Files** - Review stale code for potential cleanup or updates
4. **Large Files** - Consider breaking down large files into smaller modules

Regular review of these metrics helps maintain code quality and technical health over time.
