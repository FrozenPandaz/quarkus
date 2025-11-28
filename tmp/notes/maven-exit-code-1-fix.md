# Maven Exit Code 1 Error Fix

## Problem
Users were getting the error: "Maven dependency analysis failed: Maven process exited with code 1"

## Root Cause
The Maven plugin analyzers (both simple and complex) were not compiled, so Maven couldn't find the plugin goals to execute.

## Investigation Process

### 1. Checked Compilation Status
- Neither `simple-graph-analyzer/target/` nor `graph-analyzer/target/` directories existed
- This indicated that the Maven plugins were not compiled

### 2. Enhanced Error Reporting
Added better error messages to help users understand what went wrong:

```typescript
const analyzerName = useComplexAnalyzer ? 'complex (graph-analyzer)' : 'simple (simple-graph-analyzer)';
let errorMsg = `Maven ${analyzerName} process exited with code ${code}`;

if (stderr) {
    errorMsg += `\nStderr: ${stderr}`;
}
if (stdout && !isVerbose) {
    errorMsg += `\nStdout: ${stdout}`;
}

errorMsg += `\nMaven command: ${mavenExecutable} ${mavenArgs.join(' ')}`;
errorMsg += `\nWorking directory: ${workspaceRoot}`;

console.error('Maven analysis failed:');
console.error(errorMsg);
```

### 3. Improved Compilation Check
Enhanced the analyzer availability check with helpful compilation instructions:

```typescript
if (!availableAnalyzer) {
    const analyzerName = useComplexAnalyzer ? 'complex (graph-analyzer)' : 'simple (simple-graph-analyzer)';
    const compilationHint = `
To compile the Maven plugin, run:
  npm run compile-java:fresh

This will compile both simple and complex analyzers.

Alternatively, you can compile manually:
  cd maven-plugin && ../mvnw clean install -T6 -DskipTests -Ddevelocity.cache.local.enabled=false`;
    
    throw new Error(`Maven ${analyzerName} analyzer not found. Please ensure maven-plugin is compiled.${compilationHint}`);
}
```

### 4. Added Debug Path Checking
Added verbose logging to show which analyzer paths are being checked:

```typescript
if (process.env.NX_VERBOSE_LOGGING === 'true' || process.argv.includes('--verbose')) {
    console.log(`Looking for ${useComplex ? 'complex' : 'simple'} analyzer in paths:`);
    orderedPaths.forEach(path => {
        console.log(`  ${existsSync(path) ? '✅' : '❌'} ${path}`);
    });
}
```

## Solution

### For Users Getting This Error:

1. **Compile the Maven Plugin:**
   ```bash
   npm run compile-java:fresh
   ```

2. **Reset Nx Cache:**
   ```bash
   nx reset
   ```

3. **Try the Analysis Again:**
   ```bash
   nx show projects
   ```

### Alternative Manual Compilation:
```bash
cd maven-plugin
../mvnw clean install -T6 -DskipTests -Ddevelocity.cache.local.enabled=false
```

## Prevention

The enhanced error messages now provide:
- Clear indication of which analyzer is missing
- Exact compilation commands to fix the issue
- Debug information when running with `--verbose`
- Better Maven command and working directory context

## Expected Paths After Compilation

**Simple Analyzer:**
- `maven-plugin/simple-graph-analyzer/target/classes/`
- `maven-plugin/simple-graph-analyzer/target/simple-graph-analyzer-999-SNAPSHOT.jar`

**Complex Analyzer:**  
- `maven-plugin/graph-analyzer/target/classes/`
- `maven-plugin/graph-analyzer/target/graph-analyzer-999-SNAPSHOT.jar`

## Related Commands

- Use verbose mode for debugging: `nx show projects --verbose`
- Switch to complex analyzer: `NX_MAVEN_COMPLEX_ANALYZER=true nx show projects`
- Check compilation status: `ls -la maven-plugin/*/target/`

The error should now provide clear guidance on how to resolve the compilation issue.