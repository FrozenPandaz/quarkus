# Maven Plugin Verbose Parameter Analysis

## Summary
I searched for TypeScript files in the maven-plugin directory that might be calling the Maven executor with verbose parameters. Found several files that pass verbose flags to the Java process.

## Key Findings

### Files That Pass Verbose Parameters to Java

1. **`executor-embedder.ts`** (Primary Maven Embedder Executor)
   - Lines 162, 454: Creates `verboseFlag` variable as 'true' or 'false' string
   - Lines 170, 458: Passes `verboseFlag` as final argument to Java command
   - Commands executed:
     - Single project: `java ... NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectRoot}" "${outputFile}" ${verboseFlag}`
     - Multi-project: `java ... NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`

2. **`executor.ts`** (Legacy Maven Batch Executor)
   - Lines 96, 337: Creates `verboseFlag` as 'true' or 'false' string
   - Lines 100, 341: Passes `verboseFlag` as final argument to Java command
   - Commands executed:
     - Single project: `java ... NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectRoot}" ${verboseFlag}`
     - Multi-project: `java ... NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`

3. **`maven-plugin.ts`** (Main Plugin Entry Point)
   - Lines 178, 199: Uses verbose flag in Maven goal execution
   - Passes `-Dnx.verbose=${isVerbose}` as system property to Maven process

## Command Pattern Analysis

The TypeScript files are calling the Java `NxMavenEmbedderBatchExecutor` class with different signatures:

### Current Command Signatures:
- **Single project with output file**: `goals, workspaceRoot, projectRoot, outputFile, verboseFlag`
- **Single project without output file**: `goals, workspaceRoot, projectRoot, verboseFlag`
- **Multi-project**: `goals, workspaceRoot, projectsString, verboseFlag`

### Verbose Flag Handling:
- Both executor files convert the boolean `verbose` option to a string: `verbose ? 'true' : 'false'`
- The verbose flag is always passed as the last argument to the Java process

## Environment Control
The executor-embedder.ts has logic to switch between embedder and invoker implementations based on the `NX_MAVEN_USE_EMBEDDER` environment variable (defaults to using embedder).

## Next Steps
To understand how the Java side handles these verbose parameters, we would need to examine the Java `NxMavenEmbedderBatchExecutor` class implementation.