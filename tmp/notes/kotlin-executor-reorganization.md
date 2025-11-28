# Kotlin Executor Reorganization

## What was done

Successfully reorganized the Kotlin Maven batch executor code by separating the original executor implementation from the embedder API implementation.

## Changes made

1. **Created new directory structure**:
   - `maven-plugin/src/main/kotlin/original/` - Contains the original Maven batch executor
   - `maven-plugin/src/test/kotlin/original/` - Contains the original test files

2. **Moved original executor files**:
   - `NxMavenBatchExecutor.kt` → `original/NxMavenBatchExecutor.kt` (uses Maven Invoker API)
   - `NxMavenBatchExecutorTest.kt` → `original/NxMavenBatchExecutorTest.kt` (test for original executor)

## Current structure

- **Embedder API implementation**: `NxMavenEmbedderBatchExecutor.kt` (uses Maven Embedder API)
- **Original implementation**: `original/NxMavenBatchExecutor.kt` (uses Maven Invoker API)
- **Embedder tests**: `NxMavenEmbedderBatchExecutorTest.kt`
- **Original tests**: `original/NxMavenBatchExecutorTest.kt`

## Benefits

- Clear separation between Maven Invoker API and Maven Embedder API implementations
- Easier to maintain and debug each approach independently
- Better organization for future development
- Preserved both implementations for comparison and fallback