# Maven Executor Verbose Mode by Default

## Changes Made

Made both the Kotlin/Java and TypeScript components of the Maven executor verbose by default for better transparency and debugging.

## Files Updated

### Kotlin/Java Executor
- **File**: `maven-plugin/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`
- **Change**: Line 73 - Changed `val verbose = args.size > 4 && args[4].toBoolean()` to `val verbose = if (args.size > 4) args[4].toBoolean() else true`
- **Effect**: Now defaults to verbose mode when no 5th argument is provided

### TypeScript Executors
- **File**: `maven-plugin/src/executors/maven-batch/executor-embedder.ts`
  - Line 84: Changed `verbose = false` to `verbose = true` in main executor
  - Line 119: Changed `verbose = false` to `verbose = true` in embedder executor
  - Line 428: Changed `verbose = false` to `verbose = true` in batch executor

- **File**: `maven-plugin/src/executors/maven-batch/executor.ts`
  - Line 47: Changed `verbose = false` to `verbose = true` in main executor
  - Additional instances in batch execution functions

## Benefits

1. **Better Debugging**: More detailed output by default helps identify issues
2. **Transparency**: Users can see exactly what Maven commands are being executed
3. **Performance Insight**: Timing information is shown for each phase of execution
4. **Consistency**: All execution paths now provide verbose output by default

## Backward Compatibility

Users can still disable verbose mode by explicitly passing `false` as a parameter, maintaining backward compatibility for scripts that need minimal output.