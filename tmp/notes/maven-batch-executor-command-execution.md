# Maven Batch Executor Command Execution Analysis

## Overview
This document identifies the key files and locations where Java commands are executed in the Maven batch embedder executor system, providing a roadmap for adding logging and debugging capabilities.

## TypeScript Layer - Command Execution Points

### 1. Primary Executor Entry Points

**File: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/impl.ts`**
- **Purpose**: Re-exports the embedder-based executor
- **Command**: Re-exports from `executor-embedder.ts`
- **Lines**: 1-2

**File: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/executor-embedder.ts`**
- **Purpose**: Main TypeScript executor that determines whether to use embedder or invoker
- **Key Functions**:
  - `runExecutor()` - Main entry point (lines 76-108)
  - `runEmbedderExecutor()` - Embedder implementation (lines 111-299)
  - `runInvokerExecutor()` - Fallback to original implementation (lines 302-309)
  - `batchEmbedderExecutor()` - Batch execution with embedder (lines 330-404)

### 2. Java Command Construction and Execution

**Location**: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/executor-embedder.ts`

#### Single Task Execution (lines 160-178)
```typescript
// Build command for embedder: goals, workspaceRoot, projects, verbose
const classpath = `${embedderExecutorClasspath}:${dependencyPath}/*`;
const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectRoot}" ${verboseFlag}`;
```

#### Batch Execution (lines 442-448)
```typescript
// Build command for embedder: goals, workspaceRoot, projects, verbose
const classpath = `${embedderExecutorClasspath}:${dependencyPath}/*`;
const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`;
```

### 3. Command Execution Function

**Function**: `executeWithStreaming()` (lines 555-598)
- **Purpose**: Executes Java commands using Nx PseudoTerminal
- **Key Features**:
  - Creates PseudoTerminal for command execution
  - Captures output for JSON parsing
  - Handles error conditions and exit codes
  - Streams output to console in real-time

#### Current Logging in executeWithStreaming():
```typescript
if (verbose) {
    logger.info(`Executing with PseudoTerminal: ${command}`);
}
```

## Kotlin Layer - Maven Embedder Executor

### 1. Main Entry Point

**File: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`**

**Function**: `main()` (lines 54-87)
- **Purpose**: Main entry point for the Java process
- **Command Line Arguments**: `<goals> <workspaceRoot> <projects> [verbose]`
- **Key Actions**:
  - Parses command line arguments
  - Calls `executeBatch()` 
  - Outputs JSON results to stdout
  - Handles exit codes

### 2. Batch Execution Logic

**Function**: `executeBatch()` (lines 92-225)
- **Purpose**: Orchestrates the entire batch execution process
- **Performance Timing**: Extensive logging for performance analysis
- **Key Phases**:
  1. Initialize Maven Embedder (lines 105-117)
  2. Build task executions (lines 119-131)
  3. Load previous session data (lines 133-145)
  4. Execute all tasks (lines 147-164)
  5. Save session data (lines 166-178)
  6. Performance summary (lines 180-197)

### 3. Maven Embedder Initialization

**Function**: `initializeEmbedder()` (lines 230-364)
- **Purpose**: Sets up Maven environment exactly like Maven CLI
- **Key Components**:
  - Environment variable setup (line 233)
  - Maven CLI container initialization (lines 235-288)
  - Settings loading (line 291)
  - Repository system configuration (lines 337-348)
  - Session factory creation (line 342)

### 4. Task Execution

**Function**: `executeTask()` (lines 491-671)
- **Purpose**: Executes individual Maven tasks using embedder
- **Key Features**:
  - Parallel goal execution (lines 529-533)
  - Output/error capture (lines 547-637)
  - Performance timing per task
  - Result aggregation

## Legacy Command Execution (Invoker)

**File: `/home/jason/projects/triage/java/quarkus/maven-plugin/src/executors/maven-batch/executor.ts`**

### Key Command Execution Points:

#### Single Task (lines 95-112)
```typescript
const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectRoot}" ${verboseFlag}`;
```

#### Batch Task (lines 327-337)
```typescript
const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`;
```

## Environment Control

**Environment Variable**: `NX_MAVEN_USE_EMBEDDER`
- **Default**: `true` (uses embedder)
- **When `false`**: Falls back to invoker implementation
- **Location**: Checked in `executor-embedder.ts` lines 97 and 318

## Command Execution Flow

1. **TypeScript Entry**: `executor-embedder.ts:runExecutor()`
2. **Environment Check**: Decide between embedder/invoker
3. **Command Building**: Construct Java command with classpath
4. **Execution**: Call `executeWithStreaming()` with PseudoTerminal
5. **Kotlin Processing**: `NxMavenEmbedderBatchExecutor.main()`
6. **Maven Embedder**: Initialize and execute Maven goals
7. **Result Output**: JSON to stdout for TypeScript parsing

## Key Logging Points for Enhancement

### TypeScript Layer:
- **executor-embedder.ts:172**: Command construction logging
- **executor-embedder.ts:562**: PseudoTerminal execution logging
- **executor-embedder.ts:438**: Batch command construction logging

### Kotlin Layer:
- **NxMavenEmbedderBatchExecutor.kt:95-102**: Batch execution start logging
- **NxMavenEmbedderBatchExecutor.kt:454**: Individual task execution logging
- **NxMavenEmbedderBatchExecutor.kt:531**: Parallel goal execution logging

## Performance Timing Already Implemented

The Kotlin layer already includes extensive performance timing:
- Total execution time breakdown
- Per-phase timing (init, task building, execution, saving)
- Percentage breakdown of time spent
- Individual task timing

## Recommendations for Additional Logging

1. **Command Arguments**: Log the exact command arguments being passed
2. **Classpath Details**: Log the resolved classpath components
3. **Environment Variables**: Log key Maven environment variables
4. **POM Resolution**: Log which POM files are being processed
5. **Goal Resolution**: Log how goals are resolved and executed
6. **Dependency Resolution**: Log dependency resolution process
7. **Session State**: Log Maven session state changes
8. **Error Context**: Enhanced error logging with context information