# Nx Maven Plugin Development

This repository contains an Nx plugin for Maven integration that enables Nx to work seamlessly with Maven projects while providing enhanced caching and parallelism.

## Project Overview

**Goal**: Develop a Maven plugin that allows Nx to invoke Maven goals exactly as Maven does, but with Nx's advanced caching and parallel execution capabilities.

## Architecture

### Core Components

1. **TypeScript Graph Plugin** (`maven-plugin/maven-plugin.ts`)
   - Main entry point for Nx integration
   - Generates project graph using Maven workspace analysis
   - Interfaces with Maven APIs through the mojo analyzer

2. **Mojo Analyzer** (`./maven-plugin/`)
   - Java-based component using official Maven APIs
   - Analyzes Maven projects and dependencies
   - Provides structured data about Maven workspace to Nx

3. **Batch Maven Executor**
   - Uses Maven APIs to execute Maven targets
   - Handles parallel execution with proper Maven session context
   - Maintains Maven's exact execution behavior

## Development Principles

- **Never hardcode**: Always use Maven APIs to retrieve project information
- **Maven Compatibility**: Execute Maven goals exactly as Maven would
- **API-First**: Leverage official Maven APIs for all Maven interactions
- **Nx Enhancement**: Add caching and parallelism without changing Maven behavior

## Testing Commands

```bash
# Verify plugin functionality
nx show projects

# Generate project graph
nx graph --file graph.json

# View detailed project information
nx show projects --verbose
```

## Analyzer Configuration

The Maven plugin supports two analyzer modes that can be switched using environment variables:

### Simple Analyzer (Default)
- **Usage**: Default behavior, no environment variable needed
- **Features**: Basic run-commands targets for Maven phases and plugin goals
- **Performance**: Faster analysis, suitable for most use cases
- **Implementation**: Uses `simple-graph-analyzer` Maven plugin

### Complex Analyzer
- **Usage**: Set `NX_MAVEN_COMPLEX_ANALYZER=true`
- **Features**: Advanced lifecycle phase analysis and comprehensive target generation
- **Performance**: More detailed analysis, slower but more comprehensive
- **Implementation**: Uses `graph-analyzer` Maven plugin

```bash
# Use simple analyzer (default)
nx show projects

# Use complex analyzer
NX_MAVEN_COMPLEX_ANALYZER=true nx show projects

# Set as environment variable for session
export NX_MAVEN_COMPLEX_ANALYZER=true
nx show projects
nx graph
```

## Development Workflow

When making changes to the Maven plugin:

```bash
# 1. Recompile the Java components (disable build cache to ensure fresh compilation)
npm run compile-java:fresh

# 2. Reset Nx state to pick up changes
nx reset

# 3. Test your changes
nx show projects

# 4. Run end-to-end tests (MANDATORY before committing)
npm run test:e2e
```

## Commit Guidelines

**MANDATORY**: Always run `npm run test:e2e` before committing any changes. This ensures the Maven plugin works correctly with real Maven projects and prevents regressions.

## Prerequisites

- Nx CLI (v21.1.3+)
- Maven (3.9.9+)
- Java Development Kit
- Compiled plugin: `/maven-plugin/target/classes/`

## Test Project Context

The Quarkus application code in this repository serves solely as a test case for the Maven plugin. The actual Quarkus implementation details are not relevant to the plugin development - it's simply a real-world Maven project used to validate that the Nx Maven plugin works correctly with various Maven configurations and dependencies.
