# Simple Analyzer Run-Commands Fix

## Summary
Successfully fixed the simple analyzer to use `nx:run-commands` instead of the `maven-batch` executor, aligning with its design purpose as a lightweight alternative to the complex analyzer.

## Problem Identified
The simple analyzer was incorrectly using the `@nx-quarkus/maven-plugin:maven-batch` executor for both:
- Maven lifecycle phases (validate, compile, test, package, install, etc.)
- Maven plugin goals (compiler:compile, surefire:test, etc.)

This made it behave like the complex analyzer rather than providing simple Maven command execution.

## Root Cause
**File**: `/maven-plugin/simple-graph-analyzer/src/main/kotlin/SimpleTargetGenerator.kt`

### Before Fix:
**Lines 78-89 (Lifecycle phases):**
```kotlin
val target = TargetConfiguration("@nx-quarkus/maven-plugin:maven-batch").apply {
    options = mutableMapOf(
        "goals" to listOf(phase)
    )
    // ...
}
```

**Lines 235-243 (Plugin goals):**
```kotlin
return TargetConfiguration("@nx-quarkus/maven-plugin:maven-batch").apply {
    options = mutableMapOf(
        "goals" to listOf(goal)
    )
    // ...
}
```

### After Fix:
**Lines 78-89 (Lifecycle phases):**
```kotlin
val target = TargetConfiguration("nx:run-commands").apply {
    options = mutableMapOf(
        "command" to "mvn $phase",
        "cwd" to projectRoot
    )
    // ...
}
```

**Lines 233-242 (Plugin goals):**
```kotlin
return TargetConfiguration("nx:run-commands").apply {
    options = mutableMapOf(
        "command" to command,
        "cwd" to projectRoot
    )
    // ...
}
```

## Key Changes Made

### 1. Lifecycle Phase Targets
- **Before**: Used `maven-batch` executor with `goals` array
- **After**: Use `nx:run-commands` with direct `mvn` command strings
- **Example**: `mvn install` instead of `maven-batch` with `goals: ["install"]`

### 2. Plugin Goal Targets  
- **Before**: Extracted goal from command and used `maven-batch` executor
- **After**: Use full Maven command directly with `nx:run-commands`
- **Example**: `mvn compiler:compile` instead of `maven-batch` with `goals: ["compiler:compile"]`

### 3. Target Configuration Structure
- **Before**: `{ "goals": ["install"] }` for maven-batch
- **After**: `{ "command": "mvn install", "cwd": "." }` for run-commands

## Architecture Alignment

### Simple Analyzer Purpose:
- ✅ **Lightweight** - Direct Maven command execution via run-commands
- ✅ **Basic functionality** - No advanced session management or batching
- ✅ **Fast** - Minimal overhead, just wraps Maven commands
- ✅ **Simple caching** - Standard Nx run-commands caching

### Complex Analyzer Purpose:
- **Advanced** - Uses maven-batch executor with Maven embedder API
- **Comprehensive** - Full session management and lifecycle analysis  
- **Sophisticated caching** - Custom Maven-aware caching strategies
- **Parallel execution** - Optimized batch processing

## Verification Results

### Target Configuration:
```bash
nx show project quarkus-core --json | jq '.targets.compile.executor'
# Result: "nx:run-commands" ✅

nx show project quarkus-core --json | jq '.targets.install.executor'  
# Result: "nx:run-commands" ✅

nx show project quarkus-core --json | jq '.targets["build-helper:add-source"].executor'
# Result: "nx:run-commands" ✅
```

### Execution Test:
```bash
NX_DAEMON=false nx install quarkus-core --verbose
# Result: ✅ SUCCESS - Executes `mvn install` directly via run-commands
```

## Benefits of the Fix

### 1. **Correct Architecture**
- Simple analyzer now behaves as intended - lightweight Maven command wrapper
- Complex analyzer remains the advanced option for sophisticated use cases

### 2. **Performance**
- Reduced overhead for simple use cases
- No unnecessary Maven embedder initialization for basic commands
- Faster startup for simple Maven operations

### 3. **Simplicity**
- Easier to debug - commands are direct Maven invocations
- More predictable behavior - exactly what Maven would do
- Clearer separation of concerns between simple vs complex analyzers

### 4. **Caching**
- Standard Nx run-commands caching works correctly
- No conflicts with maven-batch executor caching strategies
- Simpler cache invalidation logic

## Usage

The simple analyzer now properly provides basic Maven command execution:

```bash
# Uses simple analyzer (default) with run-commands
nx compile quarkus-core    # → nx:run-commands with "mvn compile"
nx test quarkus-core       # → nx:run-commands with "mvn test"  
nx install quarkus-core    # → nx:run-commands with "mvn install"

# Complex analyzer still available when needed
NX_MAVEN_COMPLEX_ANALYZER=true nx install quarkus-core  # → maven-batch executor
```

The simple analyzer now correctly serves its purpose as a lightweight alternative that wraps Maven commands with Nx's caching and parallelization benefits, while the complex analyzer provides advanced Maven embedder functionality for sophisticated use cases.