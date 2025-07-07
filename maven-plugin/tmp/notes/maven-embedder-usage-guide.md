# Maven Embedder API Usage Guide

## Overview

The Maven Embedder API implementation provides enhanced Maven integration for Nx with improved plugin resolution, simplified session management, and per-task result tracking.

## Key Benefits

### 🚀 Enhanced Performance
- Direct Maven API integration vs external process invocation
- Built-in plugin resolution and caching
- Reduced session management overhead

### 🔧 Improved Developer Experience
- Per-task success/failure tracking
- Detailed error messages with Maven context
- Enhanced debugging capabilities
- Better artifact and dependency information

### 🏗️ Simplified Architecture
- No external session plugin dependencies
- Direct embedder session management
- Comprehensive state caching

## Usage

### Basic Configuration

#### Default Behavior (Embedder Enabled)
```bash
# Embedder is enabled by default - no environment variable needed
nx run my-app:compile
nx run my-app:test
```

#### Disable Embedder (Use Legacy Invoker)
```bash
# Only set this if you need to use the legacy invoker implementation
export NX_MAVEN_USE_EMBEDDER=false

# Run your Maven tasks with legacy invoker
nx run my-app:compile
nx run my-app:test
```

#### Nx Task Configuration (No Changes Required)
```json
{
  "targets": {
    "compile": {
      "executor": "@nx/maven:batch",
      "options": {
        "goals": ["compile"],
        "verbose": true
      }
    }
  }
}
```

#### Full Build Configuration
```json
{
  "targets": {
    "build": {
      "executor": "@nx/maven:batch", 
      "options": {
        "goals": ["clean", "compile", "test", "package"],
        "verbose": false,
        "failOnError": true
      }
    }
  }
}
```

### Advanced Configuration

#### Multi-Goal Execution
```json
{
  "targets": {
    "full-build": {
      "executor": "@nx/maven:batch",
      "options": {
        "goals": [
          "clean",
          "generate-sources", 
          "compile",
          "test-compile",
          "test",
          "package"
        ],
        "verbose": true,
        "outputFile": "build-results.json"
      }
    }
  }
}
```

#### Plugin-Specific Goals
```json
{
  "targets": {
    "quarkus-dev": {
      "executor": "@nx/maven:batch",
      "options": {
        "goals": [
          "io.quarkus:quarkus-maven-plugin:dev"
        ],
        "verbose": true
      }
    }
  }
}
```

## Command Line Usage

### Direct Execution
```bash
# Using embedder implementation
nx run my-app:compile

# With verbose output
nx run my-app:compile --verbose

# Multiple goals
nx run my-app:full-build
```

### Batch Execution
```bash
# Execute across multiple projects
nx run-many --target=compile --projects=app1,app2,app3

# All projects with parallel execution
nx run-many --target=test --all --parallel
```

## Migration from Legacy Invoker

### Step 1: Test Default Embedder (Recommended)
The embedder is now the default implementation. Test your existing tasks:

```bash
# No changes needed - embedder is used by default
nx run my-app:compile
nx run my-app:test
```

### Step 2: Rollback if Needed (Temporary)
If you encounter issues, you can temporarily disable the embedder:

```bash
# Temporarily use legacy invoker if needed
export NX_MAVEN_USE_EMBEDDER=false
nx run my-app:compile
```

### Step 3: Report Issues and Remove Workarounds
Report any issues with the embedder implementation and remove the environment variable once resolved.

## Result Structure

### Enhanced Task Results
The embedder provides detailed per-task results:

```typescript
interface TaskExecutionResult {
  taskId: string;
  success: boolean;
  duration: number;
  goalResults: GoalExecutionResult[];
  artifacts?: ArtifactResult[];
  dependencies?: DependencyResult[];
  errorMessage?: string;
  executionContext?: Record<string, any>;
}
```

### Per-Goal Tracking
Each goal execution is tracked individually:

```typescript
interface GoalExecutionResult {
  goal: string;
  success: boolean;
  duration: number;
  output: string[];
  errors: string[];
  exitCode: number;
  pluginInfo?: PluginInfo;
}
```

### Artifact Information
Enhanced artifact and dependency tracking:

```typescript
interface ArtifactResult {
  groupId: string;
  artifactId: string;
  version: string;
  type: string;
  classifier?: string;
  scope?: string;
  file?: string;
  resolved: boolean;
}
```

## Best Practices

### 1. Goal Selection
```json
{
  // ✅ Good: Specific goals for precise control
  "goals": ["compile", "test-compile", "test"]
  
  // ❌ Avoid: Broad lifecycle phases when specific control is needed
  "goals": ["verify"]  
}
```

### 2. Error Handling
```json
{
  "options": {
    "goals": ["compile", "test"],
    "useEmbedder": true,
    "failOnError": true,     // Fail fast on errors
    "verbose": true,         // Enable for debugging
    "outputFile": "results.json"  // Save detailed results
  }
}
```

### 3. Performance Optimization
```json
{
  // Enable parallel execution for independent projects
  "parallelism": 3,
  "options": {
    "useEmbedder": true,
    "verbose": false  // Disable verbose in CI for performance
  }
}
```

## Troubleshooting

### Common Issues

#### 1. Plugin Resolution Failures
**Problem**: Plugin cannot be resolved
**Solution**: Check plugin coordinates and repository access

```bash
# Enable verbose logging for debugging
nx run my-app:compile --verbose
```

#### 2. Classpath Issues
**Problem**: Classes not found during execution  
**Solution**: Ensure plugin dependencies are properly compiled

```bash
# Recompile Maven plugin
cd maven-plugin
mvn clean compile dependency:copy-dependencies
```

#### 3. Session Management Errors
**Problem**: Session state conflicts
**Solution**: The embedder handles session automatically - no manual intervention needed

### Debug Information

#### Enable Debug Logging
```json
{
  "options": {
    "goals": ["compile"],
    "useEmbedder": true,
    "verbose": true  // Shows detailed execution information
  }
}
```

#### Check Plugin Compilation
```bash
# Verify Maven plugin is compiled
ls maven-plugin/target/classes/
ls maven-plugin/target/dependency/

# Recompile if needed
cd maven-plugin && mvn compile dependency:copy-dependencies
```

## Performance Comparison

### Embedder vs Invoker
| Aspect | Embedder | Invoker |
|--------|----------|---------|
| Plugin Resolution | ✅ Automatic with caching | ❌ Manual availability checks |
| Session Management | ✅ In-memory, direct API | ❌ File-based, external goals |
| Result Tracking | ✅ Per-task, detailed | ❌ Batch-level aggregation |
| Error Context | ✅ Full Maven context | ❌ Limited external process info |
| Performance | ✅ Direct API calls | ❌ External process overhead |

## Compatibility

### Backward Compatibility
- ✅ Same Nx executor interface
- ✅ No changes to existing task configurations
- ✅ Legacy invoker available via environment variable fallback

### Requirements
- Maven 3.8.8+
- Java 11+
- Compiled Maven plugin (`mvn compile dependency:copy-dependencies`)

## Future Enhancements

### Planned Features
- Enhanced caching integration with Nx
- Plugin-specific optimization hints
- Advanced dependency resolution strategies
- Performance metrics and monitoring

### Experimental Features
To try experimental features, they will be controlled via environment variables:

```bash
# Enable experimental features (when available)
export NX_MAVEN_EXPERIMENTAL_CACHING=true
export NX_MAVEN_EXPERIMENTAL_PARALLEL_GOALS=true
```