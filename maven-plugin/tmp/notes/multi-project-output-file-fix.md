# Multi-Project Output File Fix

## ✅ Fixed Missing Output File Parameter in Multi-Project Function

### Problem Identified
The user correctly spotted that the `executeMultiProjectEmbedderBatch` function was missing the output file parameter in the Java command:

**Before (Broken):**
```typescript
const command = `java ... NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`;
//                                                                                                          ^^^^^^^^^^^^
//                                                                                          Missing outputFile parameter!
```

### Root Cause
There were two executor functions in the file:
1. `runEmbedderExecutor` - Single task execution (✅ had output file)
2. `executeMultiProjectEmbedderBatch` - Multi-project execution (❌ missing output file)

The multi-project function was still using the old 4-parameter format instead of the new 5-parameter format.

### Solution Applied

**1. Added Output File Logic:**
```typescript
// Create output file for results - use outputFile option or default to workspace tmp directory
const { outputFile } = options;
const outputFileName = `maven-embedder-results-${Date.now()}-${Math.random().toString(36).substr(2, 9)}.json`;
const defaultOutputDir = join(workspaceRoot, 'tmp');
const resultsFile = outputFile || join(defaultOutputDir, outputFileName);

// Ensure the output directory exists if we're creating the file
if (!outputFile && !existsSync(defaultOutputDir)) {
  mkdirSync(defaultOutputDir, { recursive: true });
}
```

**2. Updated Command Construction:**
```typescript
// FIXED: Now includes resultsFile parameter
const command = `java ... NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" "${resultsFile}" ${verboseFlag}`;
```

**3. Replaced stdout Parsing with File Reading:**
```typescript
// OLD: Complex stdout parsing
const lines = output.trim().split('\n');
// ... complex JSON extraction logic

// NEW: Direct file reading
const jsonContent = readFileSync(resultsFile, 'utf-8');
const result: Record<string, TaskExecutionResult> = JSON.parse(jsonContent);
```

**4. Added Smart Cleanup:**
```typescript
// Only clean up files we created, not user-specified ones
if (!outputFile) {
  try {
    unlinkSync(resultsFile);
  } catch (cleanupError) {
    logger.warn(`Failed to clean up output file ${resultsFile}: ${cleanupError}`);
  }
}
```

### Command Comparison

**Before (4 parameters):**
```bash
java ... NxMavenEmbedderBatchExecutor "compile" "/workspace" ".,module1,module2" true
```

**After (5 parameters):**
```bash
java ... NxMavenEmbedderBatchExecutor "compile" "/workspace" ".,module1,module2" "/workspace/tmp/results-123.json" true
```

### Functions Fixed

✅ **Single Task Execution** (`runEmbedderExecutor`)
- Already had output file parameter
- Uses workspace tmp directory

✅ **Multi-Project Execution** (`executeMultiProjectEmbedderBatch`) 
- **FIXED:** Now includes output file parameter
- Uses workspace tmp directory
- Consistent with single task execution

## Status: ✅ Complete

Both execution paths now properly pass the output file parameter to the Java process and read results from files instead of parsing stdout. This ensures consistent file-based communication across all execution modes.