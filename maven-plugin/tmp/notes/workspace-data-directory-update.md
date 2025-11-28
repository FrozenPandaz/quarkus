# Workspace Data Directory Update

## Changes Made

Updated the Maven embedder executor to use Nx's `workspaceDataDirectory` instead of a `tmp` directory in the workspace root for storing execution results.

## Files Modified

### `src/executors/maven-batch/executor-embedder.ts`

#### 1. Added Import
```typescript
import { workspaceDataDirectory } from 'nx/src/utils/cache-directory';
```

#### 2. Updated Single Executor Output Path (lines 172-175)
**Before:**
```typescript
const defaultOutputDir = join(context.workspaceRoot, 'tmp');
```

**After:**
```typescript
const defaultOutputDir = join(workspaceDataDirectory, 'maven-plugin');
```

#### 3. Updated Multi-Project Batch Output Path (lines 512-515)
**Before:**
```typescript
const defaultOutputDir = join(workspaceRoot, 'tmp');
```

**After:**
```typescript
const defaultOutputDir = join(workspaceDataDirectory, 'maven-plugin');
```

## Benefits

### 1. **Follows Nx Conventions**
- Uses the official Nx workspace data directory
- Consistent with other Nx plugins and tools
- Respects user's Nx configuration

### 2. **Better Organization**
- Results are stored in a dedicated `maven-plugin` subdirectory
- Separates Maven plugin data from other workspace files
- Avoids cluttering the workspace root

### 3. **Cache Integration**
- Aligns with Nx's caching strategy
- Data is stored alongside other Nx cache and workspace data
- Better integration with Nx's data management

## New Path Structure

Results will now be stored in:
```
{workspaceDataDirectory}/maven-plugin/maven-embedder-results-{timestamp}-{random}.json
```

Instead of:
```
{workspaceRoot}/tmp/maven-embedder-results-{timestamp}-{random}.json
```

## Backward Compatibility

- The change maintains the same file naming convention
- Directory creation is still handled automatically
- Custom `outputFile` option continues to work as before
- Cleanup behavior remains the same

## Testing

The changes have been verified to:
- Use the correct import path consistent with existing code
- Maintain proper TypeScript syntax
- Preserve all existing functionality
- Follow Nx best practices for data storage