# Deprecated Session Plugin Dependencies

## Overview
With the introduction of the Maven Embedder API implementation, the external session plugin dependencies are no longer needed. The embedder provides direct session management capabilities.

## Deprecated Components

### LoadSessionMojo.kt
- **Purpose**: Loaded session context from disk for Nx batch executor
- **Replacement**: Direct embedder session management via `EmbedderSessionContext`
- **Status**: Deprecated - use embedder implementation instead

### SaveSessionMojo.kt  
- **Purpose**: Saved session context to disk for Nx batch executor
- **Replacement**: In-memory session state management via `EmbedderSessionContext`
- **Status**: Deprecated - use embedder implementation instead

## Migration Path

### Before (Invoker + Session Plugins)
```bash
# Session goals were automatically added
mvn io.quarkus:maven-plugin:999-SNAPSHOT:load-session compile test io.quarkus:maven-plugin:999-SNAPSHOT:save-session
```

### After (Embedder)
```bash
# Direct execution without session goals
mvn compile test
```

## Technical Improvements

### Session Management
- **Before**: File-based session persistence in `.nx-maven-sessions/` directory
- **After**: In-memory session state with caching via `EmbedderSessionContext`

### Plugin Dependencies
- **Before**: Required external plugin availability check
- **After**: No external plugin dependencies - self-contained

### Performance
- **Before**: File I/O for session state + external plugin execution overhead
- **After**: Direct memory management + embedded execution

## Cleanup Strategy

### Phase 1: Deprecation (Current)
- Mark components as deprecated
- Document migration path
- Maintain for backward compatibility

### Phase 2: Feature Flag Migration
- Default to embedder implementation for new users
- Provide fallback to invoker for existing users
- Monitor usage and feedback

### Phase 3: Removal (Future)
- Remove deprecated session plugin components
- Clean up related test files
- Update documentation

## Related Files

### Core Session Components (Deprecated)
- `LoadSessionMojo.kt` - Session loading logic
- `SaveSessionMojo.kt` - Session saving logic

### Session Management in Invoker (Deprecated)
```kotlin
// In NxMavenBatchExecutor.kt
val sessionAwareGoals = if (isSessionPluginAvailable()) {
    listOf("io.quarkus:maven-plugin:999-SNAPSHOT:load-session") + goals + 
    listOf("io.quarkus:maven-plugin:999-SNAPSHOT:save-session")
} else {
    goals
}
```

### Replacement in Embedder
```kotlin
// In NxMavenEmbedderBatchExecutor.kt
sessionContext = EmbedderSessionContext(mavenSession)
// Direct session management - no external goals needed
```

## Benefits of Migration

### Simplification
- Eliminates external plugin dependencies
- Reduces complexity of goal execution
- Simplified error handling

### Performance
- No file I/O for session state
- Faster execution without external plugin overhead
- Better caching strategies

### Reliability
- No dependency on external plugin availability
- Self-contained execution
- Better error recovery

## Backward Compatibility

The embedder implementation maintains the same external interface while providing these internal improvements. Existing Nx task configurations require no changes.