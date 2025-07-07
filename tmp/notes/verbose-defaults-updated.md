# Verbose Defaults Updated

## Summary
Updated all instances of `verbose = false` to `verbose = true` in the Maven plugin TypeScript files to enable verbose logging by default.

## Changes Made

### Files Modified:
1. `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/executor.ts`
2. `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/executor-embedder.ts`

### Specific Changes:
- Line 311: Changed `verbose = false` to `verbose = true` in `executeMultiProjectMavenBatch` function
- Line 239: Changed `let verbose = false` to `let verbose = true` in `batchMavenExecutor` function
- Line 356: Changed `let verbose = false` to `let verbose = true` in `batchEmbedderExecutor` function

## Impact
These changes ensure that verbose logging is enabled by default in all Maven batch execution contexts, providing better visibility into the Maven execution process and debugging information.

## Testing
After these changes, all Maven plugin executions should now show verbose output by default, including detailed Java command logging and execution results.