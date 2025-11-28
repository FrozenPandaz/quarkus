# Maven Plugin Current State Analysis

## Branch: simple-plugin

The Maven plugin is in an advanced working state with comprehensive functionality for Nx integration.

## Architecture Overview

### 1. TypeScript Plugin Layer (`maven-plugin/src/`)
- **Main Entry Point**: `maven-plugin.ts:2` - Exports createNodesV2, createDependencies, and plugin options
- **Caching Strategy**: Global in-memory cache + persistent file-based cache using project hash
- **Maven Detection**: Auto-detects Maven wrapper (mvnw/mvnw.cmd) or falls back to system Maven
- **Java Integration**: Executes `io.quarkus:simple-graph-analyzer:999-SNAPSHOT:simple-analyze` goal

### 2. Java Analysis Layer (`maven-plugin/simple-graph-analyzer/`)
- **Main Mojo**: `SimpleAnalyzerMojo.kt:20` - Aggregating plugin that analyzes all reactor projects  
- **Target Generation**: Comprehensive lifecycle phases + plugin goals using run-commands executor
- **Output Format**: JSON structure with createNodesResults and createDependencies
- **Scope**: No dependency resolution required (ResolutionScope.NONE)

### 3. Executor Layer (`maven-plugin/src/executors/maven-batch/`)
- **Single Task**: `executor.ts:40` - Executes Maven goals via Java batch executor
- **Batch Mode**: `batchMavenExecutor:236` - Collects all tasks and executes in single Maven session
- **Java Backend**: Uses compiled Java classes (NxMavenBatchExecutor) with Maven Invoker
- **Output Streaming**: PseudoTerminal integration for real-time output

## Current Functionality

### Working Features ✅
1. **Project Discovery**: Successfully detects 1,300+ Maven projects in Quarkus codebase
2. **Caching**: Efficient dual-layer caching (memory + disk) with hash-based invalidation
3. **Target Generation**: Creates comprehensive run-commands targets for all Maven goals/phases
4. **Dependency Analysis**: Generates inter-project dependencies based on Maven reactor
5. **Batch Execution**: Can execute multiple Maven goals across projects in single session

### Technical Implementation
- **Maven 4.0.0-rc-3**: Uses latest Maven APIs for compatibility
- **Kotlin Backend**: Modern Kotlin implementation for Java components
- **TypeScript Frontend**: Integrates seamlessly with Nx plugin architecture
- **Session Management**: Proper Maven session handling with cleanup
- **Error Handling**: Comprehensive error handling and logging

## Plugin Structure
```
maven-plugin/
├── src/                          # TypeScript plugin code
│   ├── plugin/maven-plugin.ts    # Main Nx plugin entry point  
│   └── executors/maven-batch/    # Maven batch executor
├── simple-graph-analyzer/        # Java analysis (current)
│   ├── pom.xml                   # Maven plugin configuration
│   └── src/main/kotlin/          # Kotlin analysis code
├── graph-analyzer/               # Complex Java analysis (legacy)
├── embedder-executor/            # Maven embedder executor
├── original-executor/            # Original batch executor
├── nx-plugin-core/              # Shared utilities
└── shared-models/               # Common data models
```

## Key Benefits
- **Maven Compatibility**: Executes goals exactly as Maven would
- **Performance**: Efficient caching and batch execution
- **Scalability**: Handles large codebases (1,300+ projects)
- **Standards**: Uses official Maven APIs throughout
- **Integration**: Seamless Nx graph and dependency management

## Development Commands
- `npm run compile-java:fresh` - Recompile Java components
- `nx reset` - Clear Nx cache to pick up changes
- `npm run test:e2e` - End-to-end validation (mandatory before commits)

The plugin is production-ready and successfully integrates Maven projects into the Nx ecosystem while maintaining Maven's execution semantics.