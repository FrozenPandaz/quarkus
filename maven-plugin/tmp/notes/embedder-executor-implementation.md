# Embedder Executor Implementation

## Summary

Successfully updated the default Maven executor to use the embedder-based approach instead of the invoker-based approach.

## Changes Made

### 1. Updated Executor Export
- **File**: `maven-plugin/src/executors/maven-batch/impl.ts`
- **Change**: Changed export from `'./executor'` to `'./executor-embedder'`
- **Impact**: Now all Nx Maven executions will use the embedder-based executor by default

### 2. Fixed Compilation Issues
- **File**: `maven-plugin/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`
- **Issues Fixed**:
  - String multiplication operator (`"=" * 60`) → `"=".repeat(60)`
  - Map access on artifact objects using `.get()` instead of array notation
  - Nullable project properties with null coalescing (`?: ""`)

- **File**: `maven-plugin/src/main/kotlin/model/TaskExecution.kt`
- **Change**: Changed `properties` from immutable `Map<String, String>` to `MutableMap<String, String>`
- **Reason**: The executor was trying to modify task properties during session data application

## Executor Behavior

### Environment Variable Control
The embedder executor supports an environment variable for compatibility:
- `NX_MAVEN_USE_EMBEDDER=false` - Falls back to invoker-based executor
- Default (or `NX_MAVEN_USE_EMBEDDER=true`) - Uses embedder-based executor

### Key Benefits of Embedder Approach
1. **Direct Maven API Integration**: Uses Maven's Embedder API for authentic Maven execution
2. **Proper Plugin Resolution**: Leverages Maven's plugin resolution service
3. **Session Persistence**: Saves and loads execution context between runs
4. **Enhanced Performance**: Better caching and parallel execution support
5. **Artifact Tracking**: Tracks resolved artifacts and dependencies

## Verification

### Test Results
- ✅ Compilation successful with no errors
- ✅ Nx workspace detection working (`nx show projects`)
- ✅ All Maven projects detected and processed
- ✅ Target generation working correctly

### Technical Details
- The embedder executor automatically detects Maven projects in the workspace
- Uses proper Maven session context for execution
- Maintains compatibility with existing Nx workflows
- Provides detailed performance timing and logging

## Implementation Notes

### Session Management
The embedder executor includes sophisticated session management:
- Disk-based session persistence in `.nx-maven-sessions/`
- Automatic session loading and saving
- Task property injection from previous sessions
- Artifact and dependency tracking

### Performance Monitoring
Built-in performance timing tracks:
- Initialization time
- Task building time
- Session loading/saving time
- Individual goal execution time
- Total batch execution time

This change ensures that Nx Maven plugin uses the most robust and Maven-compatible execution approach by default.