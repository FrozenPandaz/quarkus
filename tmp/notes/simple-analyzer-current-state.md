# Simple Analyzer Current State and Required Improvements

## Current Simple Analyzer Implementation

The simple analyzer at `/Users/jason/projects/triage/java/quarkus/maven-plugin/simple-graph-analyzer/` currently generates only **11 targets** versus the complex analyzer's **41 targets**.

### Current Target Generation (`SimpleAnalyzerMojo.kt`)

Currently generates only these targets:
1. **7 lifecycle phase targets**: validate, compile, test, package, verify, install, deploy
2. **4 utility targets**: clean, dependency-tree, dependency-analyze, help-effective-pom

### Issues with Current Implementation

1. **Missing Plugin Goal Discovery**: Only generates basic lifecycle phases, missing individual plugin goals
2. **Missing Execution-Specific Targets**: No support for goals with execution IDs like `maven-compiler:compile@compile`
3. **Oversimplified Dependencies**: Uses basic phase-to-phase dependencies, not goal-to-goal dependencies
4. **No Maven API Integration**: Doesn't use Maven execution plan analysis
5. **No Plugin Introspection**: Doesn't analyze project's actual plugin configuration

## Required Changes to Match Complex Analyzer

### 1. Add Maven API Integration

The simple analyzer needs these Maven API components:
- `LifecycleExecutor` for execution plan analysis
- `DefaultLifecycles` for lifecycle information  
- Maven session management for project analysis

### 2. Plugin Goal Discovery

Add logic to discover all plugin goals similar to complex analyzer:
```kotlin
// Discover goals from project plugins
project.buildPlugins?.forEach { plugin ->
    plugin.executions?.forEach { execution ->
        execution.goals?.forEach { goal ->
            // Create target for each goal with execution ID
            val targetName = "${plugin.artifactId}:$goal@${execution.id}"
            // Generate run-commands target
        }
    }
}
```

### 3. Execution Plan Analysis

Add execution plan analysis to discover all goals:
```kotlin
// Analyze execution plans for all lifecycle phases
val phases = listOf("validate", "compile", "test", "package", "verify", "install", "deploy", "clean", "site")
phases.forEach { phase ->
    val executionPlan = lifecycleExecutor.calculateExecutionPlan(session, phase)
    executionPlan.mojoExecutions.forEach { mojoExecution ->
        // Create target for each discovered goal
    }
}
```

### 4. Goal-to-Goal Dependencies

Replace phase-to-phase dependencies with goal-to-goal dependencies:
```kotlin
// Goals depend on other goals from preceding phases
val precedingGoals = getPrecedingGoalsInLifecycle(project, currentPhase)
target.dependsOn = precedingGoals
```

### 5. Phase Target Orchestration

Phase targets should depend on goal targets, not other phases:
```kotlin
// Phase targets depend on goals that execute in that phase
val phaseTarget = TargetConfiguration("nx:run-commands").apply {
    dependsOn = getGoalsForPhase(phase).toMutableList()
}
```

### 6. Common Goal Addition

Add common goals for well-known plugins:
```kotlin
val commonGoals = when (plugin.artifactId) {
    "maven-compiler-plugin" -> listOf("compile", "testCompile")
    "maven-surefire-plugin" -> listOf("test")
    "quarkus-maven-plugin" -> listOf("dev", "build")
    else -> emptyList()
}
```

## Implementation Strategy

### Phase 1: Add Maven API Integration
1. Add `LifecycleExecutor` and `DefaultLifecycles` injection
2. Add execution plan analysis capability
3. Add project plugin enumeration

### Phase 2: Plugin Goal Discovery
1. Implement goal discovery from project plugins
2. Add execution-specific goal targets
3. Add common goal addition for well-known plugins

### Phase 3: Dependency Resolution
1. Replace phase-to-phase with goal-to-goal dependencies
2. Add cross-module dependency support
3. Implement phase target orchestration

### Phase 4: Target Generation
1. Generate individual goal targets with run-commands executor
2. Generate phase targets that depend on goal targets
3. Add proper target naming and metadata

## Expected Target Count

After implementing these changes, the simple analyzer should generate **41 targets** matching the complex analyzer:

- **~25 individual goal targets**: From plugin executions and common goals
- **~9 lifecycle phase targets**: From default lifecycle phases  
- **~3 clean lifecycle targets**: From clean lifecycle
- **~4 utility targets**: Additional common goals

## Key Differences from Complex Analyzer

The simple analyzer will:
- Use same goal/phase discovery logic
- Use same dependency calculation logic
- Use `nx:run-commands` executor instead of `@nx-quarkus/maven-plugin:maven-batch`
- Generate Maven commands like `mvn groupId:artifactId:goal@execution`
- Use same target naming conventions

## Files to Modify

1. **`SimpleAnalyzerMojo.kt`** - Add Maven API integration and goal discovery
2. **`CreateNodesResultGenerator.kt`** - Update target generation callback
3. **Add new service files** - Port relevant logic from complex analyzer services

The key insight is that the simple analyzer needs to use the same Maven API-based discovery logic as the complex analyzer, just with different target execution (run-commands vs custom executor).