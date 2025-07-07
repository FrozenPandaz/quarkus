# TypeScript Batch Result Types Analysis

## Summary
Found two different result type systems in the TypeScript code that the Java Maven batch executor needs to match.

## Legacy Result Types (executor.ts)
The original executor uses these simpler types:

```typescript
export interface MavenGoalResult {
  goal: string;
  success: boolean;
  durationMs: number;
  exitCode: number;
  output: string[];
  errors: string[];
}

export interface MavenBatchResult {
  overallSuccess: boolean;
  totalDurationMs: number;
  errorMessage?: string;
  goalResults: MavenGoalResult[];
}
```

## Enhanced Result Types (executor-embedder.ts)
The embedder executor uses more detailed types:

```typescript
export interface TaskExecutionResult {
  taskId: string;
  success: boolean;
  duration: number;
  goalResults: GoalExecutionResult[];
  artifacts?: ArtifactResult[];
  dependencies?: DependencyResult[];
  errorMessage?: string;
  executionContext?: Record<string, any>;
}

export interface GoalExecutionResult {
  goal: string;
  success: boolean;
  duration: number;
  output: string[];
  errors: string[];
  exitCode: number;
  pluginInfo?: PluginInfo;
  executionId?: string;
}

export interface PluginInfo {
  groupId: string;
  artifactId: string;
  version: string;
  goalName: string;
  executionId?: string;
}

export interface ArtifactResult {
  groupId: string;
  artifactId: string;
  version: string;
  type: string;
  classifier?: string;
  scope?: string;
  file?: string;
  resolved: boolean;
}

export interface DependencyResult {
  groupId: string;
  artifactId: string;
  version: string;
  type: string;
  classifier?: string;
  scope: string;
  optional: boolean;
  file?: string;
}
```

## Key Differences

1. **Return Format**: 
   - Legacy: Returns `MavenBatchResult` directly
   - Embedder: Returns `Record<string, TaskExecutionResult>` (task ID to result mapping)

2. **Goal Results**:
   - Legacy: Uses `durationMs` field
   - Embedder: Uses `duration` field

3. **Additional Data**:
   - Legacy: Basic goal execution info only
   - Embedder: Includes plugin info, artifacts, dependencies, execution context

## Current Implementation Status
The existing Java executor appears to return the legacy format based on the parsing logic in both executors expecting JSON output that gets parsed into these structures.

## Recommendation
The Java executor should support both formats with the embedder format being the preferred output since it provides richer information for advanced caching and analysis.