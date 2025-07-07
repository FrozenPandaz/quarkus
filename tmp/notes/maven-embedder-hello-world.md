# Maven Embedder API Hello World

This is a minimal example showing how to use Maven's Embedder API to execute Maven goals programmatically.

## Simple Java Example

```java
import org.apache.maven.Maven;
import org.apache.maven.execution.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;

import java.io.File;
import java.util.Arrays;

public class MavenEmbedderHelloWorld {
    
    public static void main(String[] args) throws Exception {
        // 1. Create Plexus container (Maven's dependency injection framework)
        PlexusContainer container = new DefaultPlexusContainer();
        
        // 2. Get Maven core component
        Maven maven = container.lookup(Maven.class);
        
        // 3. Create execution request
        MavenExecutionRequest request = new DefaultMavenExecutionRequest()
            .setBaseDirectory(new File("."))           // Current directory
            .setPom(new File("pom.xml"))              // POM file
            .setGoals(Arrays.asList("clean", "compile")) // Goals to execute
            .setInteractiveMode(false)                // Non-interactive
            .setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_INFO);
        
        // 4. Execute Maven goals
        MavenExecutionResult result = maven.execute(request);
        
        // 5. Check results
        if (result.hasExceptions()) {
            System.out.println("❌ Build failed!");
            for (Throwable exception : result.getExceptions()) {
                exception.printStackTrace();
            }
        } else {
            System.out.println("✅ Build succeeded!");
            System.out.println("Executed goals: " + request.getGoals());
        }
        
        // 6. Cleanup
        container.dispose();
    }
}
```

## Kotlin Version (More Similar to Our Implementation)

```kotlin
import org.apache.maven.Maven
import org.apache.maven.execution.*
import org.codehaus.plexus.DefaultPlexusContainer
import java.io.File

fun main() {
    // 1. Setup Maven environment
    val container = DefaultPlexusContainer()
    
    try {
        // 2. Get Maven core component
        val maven = container.lookup(Maven::class.java)
        
        // 3. Create execution request
        val request = DefaultMavenExecutionRequest().apply {
            setBaseDirectory(File("."))
            setPom(File("pom.xml"))
            setGoals(listOf("clean", "compile"))
            setInteractiveMode(false)
            setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_INFO)
        }
        
        // 4. Execute Maven
        println("🚀 Executing Maven goals: ${request.goals}")
        val result = maven.execute(request)
        
        // 5. Handle results
        if (result.hasExceptions()) {
            println("❌ Build failed!")
            result.exceptions.forEach { it.printStackTrace() }
        } else {
            println("✅ Build succeeded!")
            println("Projects built: ${result.project?.artifactId ?: "unknown"}")
        }
        
    } finally {
        // 6. Always cleanup
        container.dispose()
    }
}
```

## Key Components Explained

### 1. PlexusContainer
```kotlin
val container = DefaultPlexusContainer()
```
- Maven's dependency injection container
- Manages all Maven components and their dependencies
- Must be disposed when done

### 2. Maven Core
```kotlin
val maven = container.lookup(Maven::class.java)
```
- Main Maven execution engine
- Coordinates the entire build process
- Handles lifecycle, plugins, and dependencies

### 3. MavenExecutionRequest
```kotlin
val request = DefaultMavenExecutionRequest().apply {
    setBaseDirectory(File("."))        // Project root
    setPom(File("pom.xml"))           // POM file location
    setGoals(listOf("clean", "compile")) // What to execute
    setInteractiveMode(false)         // Batch mode
}
```
- Configuration for what Maven should do
- Similar to command-line arguments
- Can set profiles, properties, logging levels, etc.

### 4. Execution and Results
```kotlin
val result = maven.execute(request)
```
- Returns `MavenExecutionResult` with success/failure info
- Contains any exceptions that occurred
- Provides access to built projects

## Advanced Configuration

```kotlin
val request = DefaultMavenExecutionRequest().apply {
    // Basic settings
    setBaseDirectory(File("/path/to/project"))
    setPom(File("/path/to/project/pom.xml"))
    setGoals(listOf("clean", "test", "package"))
    
    // Parallel execution
    setDegreeOfConcurrency(Runtime.getRuntime().availableProcessors() - 1)
    
    // Properties (like -D flags)
    systemProperties.setProperty("maven.test.skip", "false")
    userProperties.setProperty("custom.property", "value")
    
    // Profiles (like -P flags)
    activeProfiles = listOf("development", "testing")
    
    // Logging
    setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_DEBUG)
    setInteractiveMode(false)
    
    // Offline mode
    setOffline(false)
}
```

## Error Handling

```kotlin
try {
    val result = maven.execute(request)
    
    when {
        result.hasExceptions() -> {
            println("Build failed with ${result.exceptions.size} errors:")
            result.exceptions.forEachIndexed { index, exception ->
                println("${index + 1}. ${exception.message}")
            }
        }
        else -> {
            println("Build completed successfully!")
            result.project?.let { project ->
                println("Built: ${project.groupId}:${project.artifactId}:${project.version}")
            }
        }
    }
} catch (e: Exception) {
    println("Failed to execute Maven: ${e.message}")
    e.printStackTrace()
}
```

## Comparison with Command Line

| Command Line | Embedder API |
|-------------|--------------|
| `mvn clean compile` | `setGoals(listOf("clean", "compile"))` |
| `mvn -Dmaven.test.skip=true` | `systemProperties.setProperty("maven.test.skip", "true")` |
| `mvn -Pdev,testing` | `activeProfiles = listOf("dev", "testing")` |
| `mvn -X` (debug) | `setLoggingLevel(LOGGING_LEVEL_DEBUG)` |
| `mvn -o` (offline) | `setOffline(true)` |
| `mvn -T 4` (parallel) | `setDegreeOfConcurrency(4)` |

This hello world example shows the basic pattern used in our more complex batch executor!