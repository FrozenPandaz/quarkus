# Maven Wrapper Detection Implementation

## Overview
Successfully implemented automatic Maven wrapper detection in the Nx Maven plugin to replace the manual `mavenExecutable` configuration option.

## Changes Made

### 1. Updated TypeScript Plugin Interface
- **File**: `maven-plugin/maven-plugin.ts`
- **Changes**:
  - Removed `mavenExecutable` property from `MavenPluginOptions` interface
  - Updated `DEFAULT_OPTIONS` to be an empty object
  - Added `detectMavenWrapper()` function that:
    - Detects Windows vs non-Windows platforms
    - Looks for `mvnw.cmd` on Windows, `mvnw` on other platforms
    - Falls back to `mvn` if no wrapper is found
  - Updated Maven command execution to use detected wrapper

### 2. Updated Configuration Files
- **File**: `nx.json`
  - Removed `mavenExecutable: "mvn"` from plugin options
- **File**: `nx-maven-config.json`
  - Removed `mavenExecutable: "mvn"` from plugin options

### 3. Updated Test Files
- **File**: `maven-plugin/maven-plugin.test.ts`
  - Removed `mavenExecutable` configuration from test options
  - Updated test description to reflect new behavior

## Detection Logic

The `detectMavenWrapper()` function:

```typescript
function detectMavenWrapper(): string {
  const isWindows = process.platform === 'win32';
  const wrapperFile = isWindows ? 'mvnw.cmd' : 'mvnw';
  const wrapperPath = join(workspaceRoot, wrapperFile);
  
  if (existsSync(wrapperPath)) {
    return wrapperPath;
  }
  
  // Fallback to 'mvn' if no wrapper found
  return 'mvn';
}
```

## Platform Support

- **Windows**: Detects `mvnw.cmd` in workspace root
- **Non-Windows** (Linux, macOS): Detects `mvnw` in workspace root
- **Fallback**: Uses `mvn` if no wrapper is found

## Verification

- ✅ Maven wrapper detection works correctly
- ✅ Plugin correctly detects `/home/jason/projects/triage/java/quarkus/mvnw`
- ✅ Project discovery and analysis functions normally
- ✅ E2E tests run successfully (though take longer due to large codebase)

## Benefits

1. **Automatic Detection**: No manual configuration needed
2. **Platform Aware**: Correctly handles Windows vs Unix systems
3. **Fallback Safety**: Uses `mvn` if no wrapper found
4. **Simplicity**: Removes configuration complexity for users

## Testing Output

The plugin now automatically detects and uses the Maven wrapper:
```
Maven executable: /home/jason/projects/triage/java/quarkus/mvnw
```

This ensures consistent Maven version usage across all team members and CI/CD environments.