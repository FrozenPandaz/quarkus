# Workspace Directory Output Fix

## ✅ Fixed Output File Path Issue

### Problem
The Maven batch executor was writing results to incorrect paths because the TypeScript code was using system temp directory (`tmpdir()`) instead of the workspace data directory.

### Root Cause
```typescript
// OLD: Used system temp directory
const outputFile = join(tmpdir(), outputFileName);
```

This created files in places like `/tmp/maven-embedder-results-...json` instead of the workspace.

### Solution

**1. Updated Output File Logic:**
```typescript
// NEW: Use workspace tmp directory by default, but allow override via options
const defaultOutputDir = join(context.workspaceRoot, 'tmp');
const resultsFile = outputFile || join(defaultOutputDir, outputFileName);

// Ensure the output directory exists
if (!outputFile && !existsSync(defaultOutputDir)) {
  mkdirSync(defaultOutputDir, { recursive: true });
}
```

**2. Smart Cleanup Logic:**
```typescript
// Only clean up files we created, not user-specified ones
if (!outputFile) {
  try {
    unlinkSync(resultsFile);
  } catch (cleanupError) {
    logger.warn(`Failed to clean up output file: ${cleanupError}`);
  }
}
```

### Key Improvements

✅ **Workspace-Relative Paths**: Results written to `{workspaceRoot}/tmp/` by default  
✅ **User Override**: Still supports `outputFile` option for custom paths  
✅ **Directory Creation**: Auto-creates tmp directory if needed  
✅ **Smart Cleanup**: Only removes auto-generated files, preserves user-specified ones  
✅ **Better Error Messages**: Accurate file paths in error messages  

### File Locations

**Before:**
- Results: `/tmp/maven-embedder-results-123.json` ❌
- Random system temp location

**After:**
- Results: `{workspaceRoot}/tmp/maven-embedder-results-123.json` ✅
- Predictable workspace location
- Option to override via `outputFile` parameter

### Command Example

**Generated Command:**
```bash
java ... NxMavenEmbedderBatchExecutor "validate" "/workspace" "." "/workspace/tmp/maven-embedder-results-123.json" true
```

**File Structure:**
```
workspace/
├── tmp/
│   └── maven-embedder-results-123.json  ← Results here
├── maven-plugin/
└── pom.xml
```

## Status: ✅ Complete

The Maven batch executor now writes results to the correct workspace directory location, making them easily accessible and predictable for the TypeScript integration.