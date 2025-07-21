# Complex Analyzer Target Generation Analysis

## Overview
The complex analyzer in `/Users/jason/projects/triage/java/quarkus/maven-plugin/graph-analyzer/` uses a sophisticated system to generate 41 targets by analyzing Maven plugin goals, lifecycle phases, and dependencies through Maven APIs.

## Key Components

### 1. Goal Discovery (`ExecutionPlanAnalysisService.kt`)
- **Function**: `calculateExecutionPlan()` - Uses Maven's `LifecycleExecutor` to analyze all phases
- **Process**: 
  - Analyzes essential phases: validate, compile, test, package, verify, install, deploy, clean, site
  - Uses `MavenExecutionPlan` to discover `MojoExecution` objects
  - Extracts goals from plugin executions in effective POM
  - Creates target names using format: `pluginArtifactId:goal@executionId`

### 2. Plugin Goal Target Generation (`TargetGenerationService.kt`)
- **Function**: `generatePluginGoalTargets()` - Creates individual goal targets
- **Process**:
  - Iterates through `project.buildPlugins` and their executions
  - Creates targets for each goal with execution ID
  - Adds common goals for well-known plugins (compiler, surefire, quarkus)
  - Each target uses `@nx-quarkus/maven-plugin:maven-batch` executor

### 3. Phase Target Generation (`TargetGenerationService.kt`)
- **Function**: `generatePhaseTargets()` - Creates lifecycle phase targets
- **Process**:
  - Gets all phases from all 3 lifecycles (default, clean, site)
  - Phase targets use `nx:noop` executor and depend on goal targets
  - Uses `getGoalsCompletedByPhase()` to determine which goals a phase depends on

### 4. Goal Introspection (`MavenPluginIntrospectionService.kt`)
- **Purpose**: Analyzes plugin behavior using Maven APIs instead of hardcoded patterns
- **Process**:
  - Uses `MojoDescriptor` to analyze plugin parameters
  - Examines parameter types (`java.io.File`, etc.) to identify file usage
  - Analyzes plugin configuration XML to understand paths
  - Determines if goals process sources, tests, or resources

### 5. Dynamic Goal Analysis (`DynamicGoalAnalysisService.kt`)
- **Purpose**: Replaces hardcoded goal classification with API-based analysis
- **Process**:
  - Combines plugin introspection with lifecycle phase analysis
  - Uses `LifecyclePhaseAnalyzer` for phase-based behavior
  - Caches results for performance

### 6. Dependency Calculation (`TargetDependencyService.kt`)
- **Goal Dependencies**: Goals depend on other goals in preceding phases
- **Phase Dependencies**: Phases depend on all goals that belong to that phase
- **Cross-module Dependencies**: Uses actual Maven dependencies (not all reactor projects)

## Target Generation Flow

1. **Execution Plan Analysis**: Pre-analyze Maven execution plans for all projects
2. **Goal Discovery**: Extract all goals from plugin executions and add common goals
3. **Goal Target Creation**: Create individual targets for each goal with proper dependencies
4. **Phase Target Creation**: Create phase targets that depend on goal targets
5. **Dependency Resolution**: Calculate goal-to-goal and phase-to-goal dependencies

## Key Patterns Used

### Target Naming Convention
- Goal targets: `pluginArtifactId:goal@executionId` (e.g., `maven-compiler:compile@compile`)
- Phase targets: Use phase name directly (e.g., `compile`, `test`)

### Executor Selection
- Goal targets: Use `@nx-quarkus/maven-plugin:maven-batch` executor
- Phase targets: Use `nx:noop` executor (orchestration only)

### Dependency Patterns
- Goals depend on goals from preceding phases in same lifecycle
- Phases depend on all goals that execute in that phase
- Cross-module dependencies use format: `projectName:goalName`

## Files Containing Target Generation Logic

1. **`NxAnalyzerMojo.kt`** - Main orchestration and entry point
2. **`TargetGenerationService.kt`** - Core target generation logic
3. **`ExecutionPlanAnalysisService.kt`** - Maven execution plan analysis
4. **`TargetDependencyService.kt`** - Dependency calculation logic
5. **`MavenPluginIntrospectionService.kt`** - Plugin behavior analysis
6. **`DynamicGoalAnalysisService.kt`** - Dynamic goal classification
7. **`LifecyclePhaseAnalyzer.kt`** - Phase behavior analysis

## Simple Analyzer Requirements

To replicate this in the simple analyzer with run-commands executor:

1. **Same Goal Discovery**: Use same plugin and execution analysis
2. **Same Phase Discovery**: Use same lifecycle phase enumeration
3. **Same Dependency Logic**: Use same goal-to-goal and phase-to-goal dependencies
4. **Different Executor**: Replace `@nx-quarkus/maven-plugin:maven-batch` with `nx:run-commands`
5. **Maven Command Generation**: Convert goal names to `mvn plugin:goal@execution` commands

The key insight is that the complex analyzer generates exactly 41 targets by:
- Creating individual goal targets for each plugin execution
- Creating phase targets that orchestrate goal targets
- Using Maven APIs to discover all goals and phases dynamically
- Calculating precise dependencies between targets

The simple analyzer needs to replicate this same discovery and dependency logic, just with different execution approach.