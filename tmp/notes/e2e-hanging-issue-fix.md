# E2E Test Hanging Issue Fix

## Problem

E2e tests were hanging after printing "Maven analysis completed successfully" at line 19 in `e2e-smoke.test.ts`:

```typescript
execSync(`npx nx graph --file ${sharedGraphFile} --verbose`, { stdio: 'inherit' });
```

The Maven analysis would complete but the process wouldn't exit, causing the test to hang indefinitely.

## Root Cause Analysis

The hanging was caused by several issues in the Maven process spawning in `maven-plugin.ts`:

1. **Inadequate Process Cleanup**: Child Maven processes weren't being properly terminated
2. **Missing Batch Mode**: Maven could be waiting for user interaction
3. **Stdio Inheritance Issues**: Using `stdio: 'inherit'` in test environments can cause hanging
4. **Event Listener Leaks**: Process event listeners weren't being cleaned up
5. **No Timeout Protection**: No timeout mechanism for Maven processes

## Fixes Applied

### 1. Added Maven Batch Mode Flags (`lines 201-213`)
```typescript
const mavenArgs = useComplexAnalyzer ? [
    'io.quarkus:graph-analyzer:999-SNAPSHOT:analyze',
    `-Dnx.outputFile=${outputFile}`,
    `-Dnx.verbose=${isVerbose}`,
    '--batch-mode',              // ← New: Prevents interactive mode
    '--no-transfer-progress'     // ← New: Reduces output noise
] : [
    'io.quarkus:simple-graph-analyzer:999-SNAPSHOT:simple-analyze',
    `-Dnx.outputFile=${outputFile}`,
    `-Dnx.verbose=${isVerbose}`,
    '--batch-mode',              // ← New: Prevents interactive mode  
    '--no-transfer-progress'     // ← New: Reduces output noise
];
```

### 2. Improved Process Spawning (`lines 223-240`)
```typescript
const child = spawn(mavenExecutable, mavenArgs, {
    cwd: workspaceRoot,
    stdio: isVerbose ? 'inherit' : 'pipe',  // ← Changed: Use pipe in non-verbose mode
    detached: false                         // ← New: Ensure proper cleanup
});

// Collect output if not in verbose mode
if (!isVerbose) {
    child.stdout?.on('data', (data) => {
        stdout += data.toString();
    });
    child.stderr?.on('data', (data) => {
        stderr += data.toString();
    });
}
```

### 3. Added Process Timeout (`lines 242-246`)
```typescript
// Set a reasonable timeout for the Maven process
const timeout = setTimeout(() => {
    child.kill('SIGTERM');
    reject(new Error(`Maven analysis timed out after 5 minutes`));
}, 300000); // 5 minutes
```

### 4. Enhanced Process Cleanup (`lines 252-301`)
```typescript
const cleanup = () => {
    if (!child.killed) {
        child.kill('SIGTERM');
        setTimeout(() => {
            if (!child.killed) {
                child.kill('SIGKILL');  // ← Forceful kill if SIGTERM fails
            }
        }, 5000);
    }
};

child.on('close', (code) => {
    clearTimeout(timeout);
    
    // Remove our specific cleanup listeners
    process.removeListener('exit', cleanup);
    process.removeListener('SIGINT', cleanup);
    process.removeListener('SIGTERM', cleanup);
    
    // ... handle exit code
});

// Register cleanup handlers
process.on('exit', cleanup);
process.on('SIGINT', cleanup);
process.on('SIGTERM', cleanup);
```

### 5. Better Error Handling (`lines 281-283`)
```typescript
const errorMsg = `Maven process exited with code ${code}`;
const fullError = stderr ? `${errorMsg}\nStderr: ${stderr}` : errorMsg;
reject(new Error(fullError));
```

## Key Improvements

1. **Batch Mode**: `--batch-mode` prevents Maven from waiting for user input
2. **No Transfer Progress**: `--no-transfer-progress` reduces console output noise
3. **Conditional stdio**: Uses `pipe` in non-verbose mode to avoid stdio inheritance issues
4. **Process Timeout**: 5-minute timeout prevents infinite hangs
5. **Graceful Termination**: SIGTERM followed by SIGKILL if needed
6. **Event Cleanup**: Properly removes process event listeners
7. **Error Capturing**: Captures stderr for better error reporting

## Expected Results

- E2e tests should complete properly after "Maven analysis completed successfully"
- No hanging processes after Maven analysis
- Better error reporting if Maven fails
- Timeout protection against infinite hangs
- Cleaner process lifecycle management

## Testing

To verify the fix:
```bash
npm run test:e2e
```

The test should complete without hanging after the Maven analysis phase.