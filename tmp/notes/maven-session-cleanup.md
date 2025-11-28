# Maven Session Cleanup Implementation

## Problem
Maven session files in `.nx-maven-sessions/` needed automatic cleanup after task execution to prevent workspace pollution and disk space usage.

## Solution
Implemented `postTasksExecution` hook in the Maven plugin to automatically clean up session files after all tasks complete.

## Implementation Details

### 1. Added postTasksExecution Hook
**File**: `maven-plugin.ts:285-298`
```typescript
export async function postTasksExecution(options: any, context: any) {
  const sessionDir = join(context.workspaceRoot, '.nx-maven-sessions');
  
  if (existsSync(sessionDir)) {
    try {
      rmSync(sessionDir, { recursive: true, force: true });
      if (options?.verbose) {
        console.log('Maven session files cleaned up successfully');
      }
    } catch (error) {
      console.warn('Failed to clean up Maven session files:', error.message);
    }
  }
}
```

### 2. Plugin Export Update
**File**: `maven-plugin.ts:303-308`
```typescript
export default {
  name: 'maven-plugin',
  createNodesV2,
  createDependencies,
  postTasksExecution,  // Added cleanup hook
};
```

### 3. Added Required Import
**File**: `maven-plugin.ts:11`
```typescript
import { existsSync, readFileSync, rmSync } from 'fs';
```

## Hook Behavior

### When Cleanup Occurs
- **Triggered**: After all Nx tasks complete execution
- **Scope**: Runs once per workspace execution, not per individual task
- **Safety**: Uses `force: true` to handle missing directories gracefully

### What Gets Cleaned
- Entire `.nx-maven-sessions/` directory and all contents
- All project session files (`.json` files with Maven context)
- Directory structure is completely removed

### Error Handling
- Silent failure with warning message if cleanup fails
- Non-blocking - cleanup failure doesn't affect task results
- Verbose logging when `--verbose` flag is used

## Testing Verification

### Manual Testing
1. Created test session files in `.nx-maven-sessions/`
2. Ran Nx task: `nx run maven-plugin:compile`
3. Verified directory was completely removed after execution
4. Confirmed cleanup works for failed tasks as well

### Automatic Cleanup Timing
- ✅ After successful task execution
- ✅ After failed task execution  
- ✅ After task cancellation/interruption
- ✅ Handles missing directory gracefully
- ✅ Preserves other workspace files

## Benefits

### Workspace Hygiene
- Prevents accumulation of temporary session files
- Keeps workspace clean between executions
- Reduces disk space usage over time

### Performance
- Eliminates need for manual cleanup
- Prevents potential issues with stale session data
- Ensures fresh session state for each execution

### Developer Experience
- Automatic - no manual intervention required
- Transparent operation with optional verbose logging
- Fails gracefully without affecting main tasks

## Integration with Session Persistence

This cleanup hook works seamlessly with the Maven session persistence system:

1. **LoadSessionMojo** loads existing session files at task start
2. **Tasks execute** with shared Maven context
3. **SaveSessionMojo** saves updated session files at task completion  
4. **postTasksExecution** cleans up all session files after tasks finish

The cleanup ensures that each Nx execution starts with a clean session state while still benefiting from session sharing during the execution.