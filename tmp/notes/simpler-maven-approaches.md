# Simpler Ways to Use Maven Programmatically

## 1. Maven CLI (Simplest)

The absolute simplest approach - just shell out to Maven:

```kotlin
import java.io.File

fun simplestMavenExecution(goals: List<String>, projectDir: File): Boolean {
    val command = listOf("mvn") + goals
    
    val process = ProcessBuilder(command)
        .directory(projectDir)
        .inheritIO()  // Show output in real-time
        .start()
    
    return process.waitFor() == 0
}

// Usage
val success = simplestMavenExecution(listOf("clean", "compile"), File("."))
```

## 2. Maven CLI Class (Still Simple)

Use Maven's CLI class directly - no Plexus container needed:

```kotlin
import org.apache.maven.cli.MavenCli
import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun mavenCliExecution(goals: List<String>, projectDir: File): Boolean {
    val cli = MavenCli()
    
    // Capture output
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    
    val args = goals.toTypedArray()
    val exitCode = cli.doMain(args, projectDir.absolutePath, PrintStream(out), PrintStream(err))
    
    if (exitCode != 0) {
        println("Maven failed: ${err.toString()}")
    }
    
    return exitCode == 0
}

// Usage  
val success = mavenCliExecution(listOf("clean", "compile"), File("."))
```

## 3. Maven Invoker (Recommended for Most Cases)

Maven provides a dedicated API for this:

```kotlin
import org.apache.maven.shared.invoker.*

fun mavenInvokerExecution(goals: List<String>, projectDir: File): Boolean {
    val request = DefaultInvocationRequest().apply {
        setPomFile(File(projectDir, "pom.xml"))
        setGoals(goals)
        setBaseDirectory(projectDir)
        isBatchMode = true
    }
    
    val invoker = DefaultInvoker()
    val result = invoker.execute(request)
    
    return result.exitCode == 0
}

// Usage
val success = mavenInvokerExecution(listOf("clean", "compile"), File("."))
```

### With Better Configuration:

```kotlin
import org.apache.maven.shared.invoker.*

class SimpleMavenExecutor {
    private val invoker = DefaultInvoker()
    
    fun execute(
        goals: List<String>, 
        projectDir: File,
        properties: Map<String, String> = emptyMap(),
        profiles: List<String> = emptyList(),
        offline: Boolean = false,
        threads: Int? = null
    ): MavenResult {
        
        val request = DefaultInvocationRequest().apply {
            setPomFile(File(projectDir, "pom.xml"))
            setGoals(goals)
            setBaseDirectory(projectDir)
            isBatchMode = true
            isOffline = offline
            
            // Add properties (-D flags)
            if (properties.isNotEmpty()) {
                setProperties(java.util.Properties().apply { 
                    putAll(properties) 
                })
            }
            
            // Add profiles (-P flags)
            if (profiles.isNotEmpty()) {
                setProfiles(profiles)
            }
            
            // Add parallel execution (-T flag)
            threads?.let { setThreads(it.toString()) }
        }
        
        val result = invoker.execute(request)
        
        return MavenResult(
            success = result.exitCode == 0,
            exitCode = result.exitCode,
            executionException = result.executionException
        )
    }
}

data class MavenResult(
    val success: Boolean,
    val exitCode: Int,
    val executionException: Throwable?
)

// Usage
val executor = SimpleMavenExecutor()
val result = executor.execute(
    goals = listOf("clean", "test", "package"),
    projectDir = File("."),
    properties = mapOf("maven.test.skip" to "false"),
    profiles = listOf("development"),
    threads = 4
)

if (result.success) {
    println("✅ Build succeeded!")
} else {
    println("❌ Build failed with exit code: ${result.exitCode}")
    result.executionException?.printStackTrace()
}
```

## 4. Why Use the Complex Embedder Approach?

The Plexus container approach we use is complex but necessary for our specific needs:

### Simple Approaches Are Limited:
```kotlin
// ❌ These can't do what we need:
// - Can't access Maven's internal project model
// - Can't get detailed execution results per goal
// - Can't integrate with Maven's session/lifecycle
// - Can't customize plugin execution
// - Can't access dependency graphs
// - Can't persist session data
```

### Embedder Approach Gives Us:
```kotlin
// ✅ Full access to Maven internals:
// - Project model (MavenProject objects)  
// - Detailed goal execution results
// - Session persistence between runs
// - Plugin introspection and customization
// - Dependency graph analysis
// - Artifact resolution details
// - Custom lifecycle participation
```

## Comparison Table

| Approach | Complexity | Control | Integration | Use Case |
|----------|------------|---------|-------------|----------|
| **Process/Shell** | ⭐ | ⭐ | ⭐ | Simple scripts |
| **Maven CLI** | ⭐⭐ | ⭐⭐ | ⭐ | Basic automation |
| **Maven Invoker** | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | Most applications |
| **Maven Embedder** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Build tools, IDEs |

## Recommendation

**For 90% of use cases:** Use Maven Invoker API
**For build tools/IDEs:** Use Maven Embedder (what we do)
**For simple scripts:** Use ProcessBuilder

## Convert Our Use Case to Maven Invoker

If we simplified our requirements, we could use:

```kotlin
fun executeBatchSimple(goals: List<String>, projects: List<String>): Map<String, Boolean> {
    val executor = SimpleMavenExecutor()
    val results = mutableMapOf<String, Boolean>()
    
    for (project in projects) {
        val projectDir = File(project)
        val result = executor.execute(goals, projectDir)
        results[project] = result.success
    }
    
    return results
}
```

But we'd lose:
- Detailed per-goal timing and results
- Maven project model access  
- Session persistence
- Plugin introspection
- Dependency analysis
- Artifact resolution details

The complexity of our Embedder approach is justified by the rich integration we need with Maven's internal APIs.