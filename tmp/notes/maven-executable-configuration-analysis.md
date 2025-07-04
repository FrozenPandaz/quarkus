# Maven Executable Configuration Analysis

## Current Maven Executable Configuration Locations

This analysis documents where Maven executable configuration is currently handled in the Nx Maven plugin codebase.

## Key Configuration Files and Locations

### 1. Main Plugin Configuration (`maven-plugin.ts`)

**File**: `/home/jason/projects/triage/java/quarkus/maven-plugin/maven-plugin.ts`

**Configuration Interface**:
```typescript
export interface MavenPluginOptions {
  mavenExecutable?: string;
  verbose?: boolean;
}

const DEFAULT_OPTIONS: MavenPluginOptions = {
  mavenExecutable: 'mvn',
};
```

**Usage**:
- Line 22: Default Maven executable set to `'mvn'`
- Line 190: `console.log(Maven executable: ${options.mavenExecutable})`
- Line 206: `console.log(Executing Maven command: ${options.mavenExecutable} ${mavenArgs.join(' ')})`
- Line 214: `spawn(options.mavenExecutable, mavenArgs, { cwd: workspaceRoot, stdio: 'inherit' })`

### 2. Executor Configuration (`maven-batch/executor.ts`)

**File**: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/executor.ts`

**Configuration Interface**:
```typescript
export interface MavenBatchExecutorOptions {
  goals: string[];
  projectRoot?: string;
  verbose?: boolean;
  mavenPluginPath?: string;
  outputFile?: string;
  failOnError?: boolean;
}
```

**Note**: The batch executor does NOT directly use `mavenExecutable` - it uses Java-based batch execution instead.

### 3. Executor Schema (`maven-batch/schema.json`)

**File**: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/schema.json`

**Schema Properties**: The schema does NOT include `mavenExecutable` as a configuration option for the batch executor.

### 4. Global Configuration Files

**File**: `/home/jason/projects/triage/java/quarkus/nx.json`
```json
{
  "plugins": [
    {
      "plugin": "./maven-plugin",
      "options": {
        "mavenExecutable": "mvn"
      }
    }
  ]
}
```

**File**: `/home/jason/projects/triage/java/quarkus/nx-maven-config.json`
```json
{
  "plugins": [
    {
      "plugin": "./maven-plugin",
      "options": {
        "mavenExecutable": "mvn"
      }
    }
  ]
}
```

### 5. Java/Kotlin Batch Executor (`NxMavenBatchExecutor.kt`)

**File**: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/main/kotlin/NxMavenBatchExecutor.kt`

**Maven Executable Discovery Logic**:
```kotlin
// Lines 112-126: Maven executable detection
val mavenHome = System.getenv("MAVEN_HOME")
if (mavenHome != null) {
    invoker.mavenHome = File(mavenHome)
} else {
    // Try to find Maven executable in PATH
    val mavenExecutable = findMavenExecutable()
    if (mavenExecutable != null) {
        // Set Maven home to parent directory of mvn executable
        val mvnFile = File(mavenExecutable)
        val binDir = mvnFile.parentFile
        if (binDir != null && binDir.name == "bin") {
            invoker.mavenHome = binDir.parentFile
        }
    }
}
```

**Maven Executable Search Function**:
```kotlin
// Lines 206-221: Find Maven executable in PATH
private fun findMavenExecutable(): String? {
    val pathEnv = System.getenv("PATH") ?: return null
    val pathSeparator = System.getProperty("path.separator")
    val paths = pathEnv.split(pathSeparator)
    
    val mvnCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "mvn.cmd" else "mvn"
    
    for (path in paths) {
        val mvnFile = File(path, mvnCommand)
        if (mvnFile.exists() && mvnFile.canExecute()) {
            return mvnFile.absolutePath
        }
    }
    
    return null
}
```

### 6. Test Configuration (`maven-plugin.test.ts`)

**File**: `/home/jason/projects/triage/java/quarkus/maven-plugin/maven-plugin.test.ts`

**Test Configuration**:
```typescript
const options: MavenPluginOptions = {
  mavenExecutable: 'mvn',
  verbose: false
};
```

## Configuration Flow Summary

### TypeScript Plugin (Analysis Phase)
1. **Configuration**: Uses `MavenPluginOptions.mavenExecutable` (defaults to `'mvn'`)
2. **Usage**: Spawns Maven process for analysis using `options.mavenExecutable`
3. **Command**: `mvn io.quarkus:maven-plugin:999-SNAPSHOT:analyze`

### Java Batch Executor (Execution Phase)
1. **Configuration**: Does NOT use TypeScript `mavenExecutable` option
2. **Discovery**: Uses environment-based Maven discovery:
   - First checks `MAVEN_HOME` environment variable
   - Then searches PATH for `mvn` or `mvn.cmd`
   - Falls back to Maven Invoker API defaults
3. **Usage**: Uses Maven Invoker API for execution

## Key Findings

1. **Inconsistent Configuration**: The TypeScript plugin uses `mavenExecutable` config, but the Java batch executor uses environment-based discovery.

2. **Missing Configuration Bridge**: The `mavenExecutable` option from TypeScript is not passed to the Java batch executor.

3. **Environment Dependencies**: The Java batch executor relies on `MAVEN_HOME` or PATH-based discovery rather than explicit configuration.

4. **No Custom Maven Path Support**: The batch executor cannot use a custom Maven executable path specified in configuration.

## Potential Issues

1. **Configuration Mismatch**: Analysis and execution may use different Maven installations
2. **Limited Flexibility**: Users cannot specify custom Maven executable paths for batch execution
3. **Environment Dependency**: Batch executor requires proper Maven environment setup

## Recommendations

1. **Bridge Configuration**: Pass TypeScript `mavenExecutable` configuration to Java batch executor
2. **Unified Configuration**: Ensure both analysis and execution use the same Maven executable
3. **Explicit Path Support**: Allow users to specify absolute paths to Maven executables
4. **Environment Fallback**: Maintain current environment-based discovery as fallback