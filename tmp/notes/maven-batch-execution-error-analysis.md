# Maven Batch Execution Error Analysis

## Error Description
**Error**: "Failed to lookup component org.apache.maven.project.ProjectBuilder: com.google.inject.ProvisionException: Unable to provision, see the following errors"

This is a Guice dependency injection error occurring during Maven component lookup in the embedder batch executor.

## Root Cause Analysis

### 1. Component Lookup Issue Location
The error occurs in the Maven embedder batch executor when trying to lookup the ProjectBuilder component:

**File**: `/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`
**Line**: 474 - `val projectBuilder = getComponent<ProjectBuilder>("org.apache.maven.project.ProjectBuilder")`

### 2. Plexus Container Configuration Problem
The issue is in the Plexus container initialization. The current code enables component scanning but may not be properly configuring the container for Maven's component injection:

```kotlin
// Line 263-268 in NxMavenEmbedderBatchExecutor.kt
val configuration = DefaultContainerConfiguration()
    .setAutoWiring(true)
    .setComponentVisibility(PlexusConstants.REALM_VISIBILITY)
    .setClassPathScanning(PlexusConstants.SCANNING_ON)  // Enable component scanning

plexusContainer = DefaultPlexusContainer(configuration)
```

### 3. Maven Version Compatibility Issue
The code is using Maven 3.9.10 (declared in parent pom.xml) but may be missing required Guice bindings or component descriptors for the ProjectBuilder.

### 4. Component Registration Missing
The ProjectBuilder component might not be properly registered in the Plexus container's component registry. This can happen when:
- Maven component descriptors are not on the classpath
- Guice modules are not properly configured
- Component scanning fails to find the required components

## Evidence from Code Analysis

### Recent Changes
Based on git status, there have been significant changes to the Maven plugin structure:
- Multiple Kotlin files deleted from the old structure
- New separate projects created (embedder-executor, graph-analyzer, original-executor)
- TypeScript moved to main level structure

### Key Issues Identified

1. **Missing Maven Embedder Dependencies**: The embedder-executor may be missing critical Maven dependencies that provide the ProjectBuilder component.

2. **Incorrect Component Lookup Strategy**: The current approach uses string-based lookup which may not work properly with Maven's internal component structure.

3. **ClassPath Issues**: The component scanning may not be finding Maven's internal components due to classpath configuration.

4. **Environment Variable Issues**: The embedder is controlled by `NX_MAVEN_USE_EMBEDDER` environment variable (defaults to true), so this error will occur by default.

## Dependencies Analysis

### Current Dependencies in embedder-executor/pom.xml:
- maven-core: ✅ Present  
- maven-embedder: ✅ Present
- graph-analyzer: ✅ Present (internal dependency)

### Missing Dependencies:
- maven-compat: ❌ Missing (often required for component compatibility)
- maven-settings-builder: ❌ May be missing
- Specific Guice modules for Maven components: ❌ May be missing

## Recommended Solutions

### 1. Fix Plexus Container Configuration
The container needs proper Maven component discovery. We should use Maven's built-in container initialization approach.

### 2. Add Missing Dependencies
Add maven-compat and other required Maven dependencies to ensure all components are available.

### 3. Alternative Component Lookup Strategy
Instead of string-based lookup, use Maven's built-in component access patterns.

### 4. Environment Variable Workaround
Since embedder is enabled by default, provide fallback to original executor when embedder fails.

### 5. Improve Error Handling
Add better error handling and fallback mechanisms when component lookup fails.

## Impact Assessment
- **Severity**: High - Blocks Maven goal execution when embedder is enabled (default)
- **Scope**: All Maven operations using the embedder executor
- **Workaround**: Set `NX_MAVEN_USE_EMBEDDER=false` to use original executor
- **Timeline**: Should be fixed immediately to restore default functionality

## Files Requiring Changes
1. `/maven-plugin/embedder-executor/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`
2. `/maven-plugin/embedder-executor/pom.xml`
3. `/maven-plugin/src/executors/maven-batch/executor-embedder.ts`

## Testing Strategy
1. Test with both embedder enabled and disabled
2. Verify component lookup works in different Maven versions
3. Test with various Maven project structures
4. Ensure fallback mechanisms work properly