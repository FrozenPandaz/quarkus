# NxMavenEmbedderBatchExecutor Investigation

## Issue Analysis

The NxMavenEmbedderBatchExecutor is failing because of a **package structure mismatch** between the source code and how it's being called.

## Root Cause

1. **Package Declaration Problem**: The `NxMavenEmbedderBatchExecutor.kt` file has been moved from the root package to the `embedder` package, but:
   - Line 1 of the file now has `package embedder`
   - The compiled class is located at `maven-plugin/target/classes/embedder/NxMavenEmbedderBatchExecutor.class`
   - BUT the command is trying to invoke it as `NxMavenEmbedderBatchExecutor` (root package)

2. **Import Issues**: The file imports `model.*` but the model classes are now in `graph.model` package.

## Files Restructured

The git status shows extensive reorganization:
- Many files moved from root to `embedder/` and `graph/` directories
- Old files deleted from root location
- New organized structure with proper packages

## Command Issue

The failing command:
```bash
java -cp "..." NxMavenEmbedderBatchExecutor "..."
```

Should be:
```bash
java -cp "..." embedder.NxMavenEmbedderBatchExecutor "..."
```

## Required Fixes

1. **Fix Package Declaration**: Remove `package embedder` from NxMavenEmbedderBatchExecutor.kt OR update the calling code to use the full package name
2. **Fix Import Statements**: Update `import model.*` to `import graph.model.*`
3. **Recompile**: Run fresh compilation to ensure correct package structure
4. **Update Callers**: Find and update any code that invokes this class

## Immediate Fix Strategy

The simplest fix is to move `NxMavenEmbedderBatchExecutor.kt` back to the root kotlin directory and remove the package declaration, since it's being called as a standalone Java class.