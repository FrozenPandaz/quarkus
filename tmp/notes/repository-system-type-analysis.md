# RepositorySystem Type Analysis

## Summary
The NxMavenEmbedderBatchExecutor.kt file uses **both** types of RepositorySystem - the Maven RepositorySystem and the Aether RepositorySystem. This dual usage is the root cause of the binding error.

## Analysis Results

### 1. Import Statements
The file imports both types:
- Line 13: `import org.apache.maven.repository.RepositorySystem`
- Line 23: `import org.eclipse.aether.RepositorySystem as AetherRepositorySystem`

### 2. Variable Declarations
- Line 56: `private lateinit var repositorySystemSession: RepositorySystemSession`
  - This is using the Aether RepositorySystemSession

### 3. Method Usage Patterns

#### Maven RepositorySystem Usage:
- Not directly used in variable declarations
- Only imported (line 13)
- Also referenced in MinimalMavenSession.kt (line 8) and MavenSessionFactory.kt (line 5)

#### Aether RepositorySystem Usage:
- Line 386: `val aetherRepositorySystem = createRepositorySystem(verbose)`
- Line 387: `repositorySystemSession = createRepositorySystemSession(aetherRepositorySystem, settings, verbose)`
- Line 819: Function parameter `repositorySystem: AetherRepositorySystem`
- Line 982: Function returns `AetherRepositorySystem`
- Line 989: `org.apache.maven.repository.internal.MavenRepositorySystemUtils.newServiceLocator()`
- Line 1004: `getComponent<AetherRepositorySystem>("org.eclipse.aether.RepositorySystem")`

### 4. The Problem
The code is trying to use both:
1. **Maven RepositorySystem** (`org.apache.maven.repository.RepositorySystem`) - This is the older, deprecated API
2. **Aether RepositorySystem** (`org.eclipse.aether.RepositorySystem`) - This is the modern API

The error "No implementation for RepositorySystem was bound" occurs because:
- The code imports Maven RepositorySystem but doesn't use it
- The code actually uses Aether RepositorySystem throughout
- The Plexus container lookup on line 1004 is trying to find `org.eclipse.aether.RepositorySystem` but the container may not have this component properly bound

### 5. Solution Strategy
The fix should:
1. Remove the unused Maven RepositorySystem import (line 13)
2. Ensure the Aether RepositorySystem is properly configured in the Plexus container
3. Use consistent Aether APIs throughout the codebase

### 6. Component Lookup Analysis
Line 1004 shows the problematic lookup:
```kotlin
getComponent<AetherRepositorySystem>("org.eclipse.aether.RepositorySystem")
```

This indicates the container is missing the Aether RepositorySystem component binding. The solution likely involves configuring the Maven repository system components properly in the Plexus container initialization.

## Key Files Affected
- `/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`
- `/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/src/main/kotlin/MinimalMavenSession.kt`
- `/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/src/main/kotlin/MavenSessionFactory.kt`

## Recommendation
Focus on the Aether RepositorySystem configuration in the `configureRepositorySystem()` method and ensure the Plexus container has all necessary Maven resolver components properly bound.