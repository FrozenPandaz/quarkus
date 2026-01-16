# Maven Plugin Plexus Container Fix

## Issue
The `npx nx install maven-plugin --skipNxCache` command was failing with a `NoSuchMethodError` related to the Plexus container lookup method.

## Root Cause
The error was:
```
java.lang.NoSuchMethodError: 'java.lang.Object org.codehaus.plexus.PlexusContainer.lookup(java.lang.Class)'
```

This occurred because the code was using `PlexusContainer.lookup(Class)` method which doesn't exist in the Maven version being used. The correct method signature is `lookup(String)` or `lookup(String, String)`.

## Solution
Fixed all instances of `plexusContainer.lookup(SomeClass::class.java)` to use the string-based lookup method:

### Files Modified:
1. **NxMavenEmbedderBatchExecutor.kt**:
   - `plexusContainer.lookup(MavenExecutionRequestPopulator::class.java)` → `plexusContainer.lookup("org.apache.maven.execution.MavenExecutionRequestPopulator") as MavenExecutionRequestPopulator`
   - `plexusContainer.lookup(AetherRepositorySystem::class.java)` → `plexusContainer.lookup("org.eclipse.aether.RepositorySystem") as AetherRepositorySystem`
   - `plexusContainer.lookup(ProjectBuilder::class.java)` → `plexusContainer.lookup("org.apache.maven.project.ProjectBuilder") as ProjectBuilder`
   - `plexusContainer.lookup(LifecycleExecutor::class.java)` → `plexusContainer.lookup("org.apache.maven.lifecycle.LifecycleExecutor") as LifecycleExecutor`

2. **MavenEmbedderPluginResolutionService.kt**:
   - `plexusContainer.lookup(PluginManager::class.java)` → `plexusContainer.lookup("org.apache.maven.plugin.PluginManager") as PluginManager`
   - `plexusContainer.lookup(PluginVersionResolver::class.java)` → `plexusContainer.lookup("org.apache.maven.plugin.version.PluginVersionResolver") as PluginVersionResolver`

3. **MavenSessionFactory.kt**:
   - Fixed compilation errors with File/String type mismatches
   - Added missing imports for `ProjectDependencyGraph` and `File`

## What is the Plexus Container?
The Plexus Container is a dependency injection framework used by Maven for managing components. It's like a service locator pattern where you can lookup various Maven components (like PluginManager, LifecycleExecutor, etc.) by their interface or implementation class name.

Think of it as Maven's "toolbox" - when you need a specific tool (like the lifecycle executor to run goals), you ask the container to give you that tool by its name or type.

## Status
✅ **FIXED** - The NoSuchMethodError has been resolved and compilation is successful.

The command may still have other issues, but the core Plexus container lookup problem has been addressed.