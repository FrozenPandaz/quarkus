# Per-Task Failure Reporting Test

## What Was Implemented

Enhanced the Maven batch executor to report failures per task instead of marking all tasks as failed when any task fails.

### Key Changes Made

1. **Added Task-Goal Mapping**: Store which goals belong to which tasks
2. **Per-Task Success Logic**: Check if each task's specific goals succeeded
3. **Goal Matching**: Handle Maven goal resolution (e.g., `compile` → `maven-compiler-plugin:compile`)
4. **Task-Specific Output**: Return relevant output for each task

### Implementation Details

- Modified `batchMavenExecutor()` in `executor.ts` to maintain task-to-goal mapping
- Added `isTaskSuccessful()` function to check individual task success
- Added `getTaskSpecificOutput()` function to provide relevant output per task
- Preserved single Maven session execution (no changes to Java/Kotlin executor)

## Test Plan

Need to test scenarios where:
1. All tasks succeed → All should report success
2. Some tasks fail → Only failed tasks should report failure
3. Mixed goal types → Proper goal matching works
4. Goal resolution → Handles Maven's goal name resolution

## Next Steps

Ready to test the implementation with real scenarios.