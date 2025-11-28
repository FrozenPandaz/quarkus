# Maven Embedder Container Initialization Fix

## Problem Summary

The Maven embedder is failing to initialize because the PlexusContainer doesn't have Maven-specific components like:
- `MavenExecutionRequestPopulator`
- `org.eclipse.aether.RepositorySystem`
- `org.apache.maven.Maven`

## Root Cause

The `maven-embedder` dependency (3.9.10) contains only the Maven CLI classes, not a full embedder API. The proper Maven Embedder API was removed in favor of using MavenCli, but MavenCli requires full initialization which triggers Maven execution.

## Attempted Solutions

1. **Basic PlexusContainer**: Created DefaultPlexusContainer but it lacks Maven components
2. **MavenCli Reflection**: Tried to access MavenCli's container via reflection but hits GuiceCreationException
3. **ClassWorld Configuration**: Enhanced container with proper ClassWorld but still missing components

## Current Error

```
com.google.inject.CreationException: Unable to create injector
1) Binding to null instances is not allowed
```

The MavenCli.container() method requires proper CliRequest initialization which includes Maven execution setup.

## Solution Path

The correct approach is to use Maven's dependency injection properly. The components need to be loaded from `maven-core` dependency, not just `maven-embedder`.

### Option 1: Add maven-core dependency
```xml
<dependency>
    <groupId>org.apache.maven</groupId>
    <artifactId>maven-core</artifactId>
    <version>3.9.10</version>
</dependency>
```

### Option 2: Use Maven Invoker API
Switch to using the existing `maven-invoker` dependency for goal execution instead of the embedder API.

## Next Steps

1. Try adding maven-core dependency to get proper Maven components
2. If that fails, simplify to use maven-invoker for goal execution
3. Keep session management separate from goal execution

## Key Learning

Modern Maven (3.9+) has simplified the embedder story - most integrations should use maven-invoker for execution and maven-core for component access.