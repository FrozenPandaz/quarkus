# Maven Executor Analysis

## Default Executor Configuration

After analyzing the codebase, I found that the Nx Maven plugin uses **two different executors** depending on the situation:

### 1. Current Live Configuration: `@nx-quarkus/maven-plugin:maven-batch`

From the graph.json file and TargetGenerationService.kt, I can see that **currently all Maven targets are configured to use the `maven-batch` executor**:

```kotlin
val target = TargetConfiguration("@nx-quarkus/maven-plugin:maven-batch")
```

This executor is:
- **Defined in**: `/maven-plugin/executors.json`
- **Implementation**: `./src/executors/maven-batch/impl.ts`
- **Batch Implementation**: `./src/executors/maven-batch/impl.ts#batchMavenExecutor`
- **Schema**: `./src/executors/maven-batch/schema.json`

### 2. Fallback Configuration: `@nx/run-commands:run-commands`

The `core-targets.json` file shows an older configuration using the generic Nx run-commands executor:

```json
{
  "executor": "@nx/run-commands:run-commands",
  "options": {
    "command": "mvn clean",
    "cwd": "core/runtime"
  }
}
```

This appears to be either:
- A fallback configuration when the Maven plugin isn't available
- An older configuration that's been superseded
- A different execution path used in specific circumstances

## Key Findings

1. **Primary Executor**: `@nx-quarkus/maven-plugin:maven-batch` (TypeScript implementation)
2. **Fallback Executor**: `@nx/run-commands:run-commands` (Generic command runner)
3. **Batch Execution**: The maven-batch executor supports both single and batch execution modes
4. **Session Management**: The maven-batch executor includes Maven session persistence for artifact context

## Executor Configuration Details

The maven-batch executor accepts these options:
- `goals`: Array of Maven goals to execute
- `projectRoot`: Project root directory
- `verbose`: Enable verbose output
- `mavenPluginPath`: Path to Maven plugin directory
- `outputFile`: Optional file for execution results
- `failOnError`: Whether to fail on Maven goal errors (default: true)

## Architecture Decision

The plugin uses the custom `maven-batch` executor to:
- Execute Maven goals exactly as Maven would
- Maintain Maven session context between goals
- Provide enhanced caching and parallelism
- Preserve Maven's execution behavior while adding Nx benefits