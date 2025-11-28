# Maven Embedder Executor Debug Analysis

## Issue
The `nx install quarkus-core` command fails with Maven embedder executor exit code 1, but no helpful error output is provided.

## Java Command Being Executed
```bash
java -Dmaven.multiModuleProjectDirectory="/home/jason/projects/triage/java/quarkus" -cp "/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/target/embedder-executor-999-SNAPSHOT.jar:/home/jason/projects/triage/java/quarkus/maven-plugin/graph-analyzer/target/graph-analyzer-999-SNAPSHOT.jar:/home/jason/projects/triage/java/quarkus/maven-plugin/nx-plugin-core/target/dependency/*" NxMavenEmbedderBatchExecutor "validate" "/home/jason/projects/triage/java/quarkus" "." "/home/jason/projects/triage/java/quarkus/.nx/workspace-data/maven-plugin/maven-embedder-results-1753130718156-9y6madrqr.json" false
```

## Analysis of Kotlin Main Class

From `NxMavenEmbedderBatchExecutor.kt`, the main function is defined as:

```kotlin
object NxMavenEmbedderBatchExecutor {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 4) {
            System.err.println("Usage: java NxMavenEmbedderBatchExecutor <goals> <workspaceRoot> <projects> <outputFile> [verbose] [additional-properties...]")
            exitProcess(1)
        }
        // ... rest of implementation
    }
}
```

## Potential Issues

### 1. **Argument Count Validation**
The command provides 5 arguments:
- `"validate"` (goals)
- `"/home/jason/projects/triage/java/quarkus"` (workspaceRoot)  
- `"."` (projects)
- `"/home/jason/projects/triage/java/quarkus/.nx/workspace-data/maven-plugin/maven-embedder-results-1753130718156-9y6madrqr.json"` (outputFile)
- `false` (verbose)

This should pass the `args.size < 4` check since we have 5 arguments.

### 2. **Classpath Issues**
The classpath includes:
- `embedder-executor-999-SNAPSHOT.jar` ✓ (contains main class)
- `graph-analyzer-999-SNAPSHOT.jar` ✓ (dependency)
- `nx-plugin-core/target/dependency/*` (Maven dependencies)

**Potential Issue**: The wildcard `dependency/*` expansion might not work correctly, or required Maven dependencies might be missing.

### 3. **Maven Dependencies Missing**
The executor requires many Maven API classes:
- `org.apache.maven.execution.*`
- `org.apache.maven.cli.MavenCli` 
- `org.eclipse.aether.*`
- `org.codehaus.plexus.*`
- And many more...

If these aren't in the dependency directory, the class loading will fail.

### 4. **Initialization Failures**
Looking at the `initializeEmbedder` method, it does complex Maven container setup:
- Creates Plexus container with Eclipse Sisu
- Sets up Maven settings and repositories
- Initializes Maven session factory
- Creates repository system session

Any of these steps could fail silently and cause exit code 1.

## Debugging Steps Needed

1. **Check if main class is found**: Verify `NxMavenEmbedderBatchExecutor` class exists in JAR
2. **Verify classpath expansion**: Check if `dependency/*` contains required Maven JARs
3. **Test with minimal arguments**: Try running with just the required 4 arguments
4. **Add debug output**: Modify the executor to provide more verbose error output
5. **Check Maven environment**: Ensure Maven home and settings are properly configured

## Recommended Fixes

### 1. **Improve Error Handling**
Modify the main method to catch and report initialization errors:

```kotlin
@JvmStatic
fun main(args: Array<String>) {
    try {
        // existing logic
    } catch (e: Throwable) {
        System.err.println("EMBEDDER ERROR: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
```

### 2. **Verify Dependencies**
Check that all required Maven dependencies are present in the classpath.

### 3. **Test Class Loading**
Add debug output to verify the main class is loaded and arguments are received correctly.