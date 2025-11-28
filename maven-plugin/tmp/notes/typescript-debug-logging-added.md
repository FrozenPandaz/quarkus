# TypeScript Debug Logging Added

## Changes Made

Added comprehensive debug logging to the TypeScript executor that shows the task graph execution plan.

## Location
File: `src/executors/maven-batch/executor-embedder.ts`

## Debug Logging Added

### 1. Task Graph Execution Plan (lines 402-425)
Shows before executing the embedder batch:
- Total tasks in graph
- Unique goals to execute
- Unique projects to execute
- Verbose mode status
- Detailed breakdown of each task including:
  - Project name
  - Target name
  - Project root
  - Goals to execute
  - Full options object

### 2. Task Results Mapping (lines 437-453)
Shows after embedder batch execution:
- Success/failure status for each task
- Goals executed for each task
- Output length for each task

## Sample Output

```
=== TASK GRAPH EXECUTION PLAN ===
📋 Total tasks in graph: 1
🎯 Unique goals to execute: 1 [install]
📦 Unique projects to execute: 1 [maven-plugin]
🔍 Verbose mode: true

📊 Task breakdown:
  • maven-plugin:install:
    - Project: maven-plugin
    - Target: install
    - Project Root: maven-plugin
    - Goals: [install]
    - Options: {
      "goals": ["install"],
      "projectRoot": "maven-plugin",
      "verbose": true
    }

🚀 Executing embedder batch...
=====================================

=== TASK RESULTS MAPPING ===
  📋 maven-plugin:install: ❌ FAILED
    - Goals: [install]
    - Output length: 1234 characters
=============================
```

## Benefits

1. **Visibility**: Clear view of what the executor is about to execute
2. **Debugging**: Easy to see which goals are being executed on which projects
3. **Troubleshooting**: Can identify issues with task graph construction
4. **Performance**: Shows the batch optimization - multiple tasks executed in single Java process

## Usage

This debug logging will automatically appear when using the embedder executor (when `NX_MAVEN_USE_EMBEDDER=true` or default behavior).

The existing multi-project batch logging already shows the Java command being executed, so you'll see both the TypeScript task graph analysis and the Java command execution details.