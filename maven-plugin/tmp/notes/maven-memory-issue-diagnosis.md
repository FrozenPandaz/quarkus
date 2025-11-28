# Maven Memory Issue Diagnosis

## 🔍 Problem Identified: OutOfMemoryError

### Root Cause
The Maven task execution is failing due to `java.lang.OutOfMemoryError` when running Maven commands on the Quarkus project:

```
Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "JNA Cleaner"
Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "Resource Usage Collector Processes"
```

### Symptoms
- ✅ **Maven Embedder initialization**: Working correctly
- ✅ **Task building**: Successfully creates `TaskExecution` objects  
- ✅ **Session creation**: "Created Maven session successfully"
- ❌ **Goal execution**: Fails with "Some goals failed in 1ms"
- ❌ **Error details**: Empty `errors` array because JVM crashes before proper error handling

### Why This Happens
1. **Quarkus projects**: Are notoriously memory-hungry during Maven operations
2. **Default JVM settings**: May be insufficient for Quarkus compilation/validation
3. **Multiple threads**: The embedder uses parallel execution which increases memory pressure
4. **Maven embedder**: Runs in the same JVM process, sharing memory with the executor

## 🔧 Solutions

### Option 1: Increase JVM Memory (Recommended)
Add JVM memory arguments to the Java command in the TypeScript executor:

```typescript
const command = `java -Xmx4g -Xms1g -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor ...`;
```

### Option 2: Disable Parallel Execution
Modify the Maven execution request to use single-threaded execution:

```kotlin
// In NxMavenEmbedderBatchExecutor.kt
setDegreeOfConcurrency(1) // Instead of availableCores - 1
```

### Option 3: Use MAVEN_OPTS Environment Variable
Set memory options via environment:

```bash
export MAVEN_OPTS="-Xmx4g -Xms1g -XX:MaxPermSize=512m"
```

### Option 4: Test with Simpler Goal
Try a simpler goal like `help:effective-pom` instead of `validate` to verify the fix:

```bash
java ... NxMavenEmbedderBatchExecutor "help:effective-pom" "/workspace" "." "/tmp/results.json" true
```

## 🎯 Immediate Fix Needed

The Java executor is working correctly - the issue is insufficient memory for the Quarkus Maven project. The most immediate fix would be to add memory arguments to the Java command in the TypeScript executor.

## 🔄 Testing Strategy

1. **Apply memory fix** to TypeScript executor
2. **Test with simple goal** (like `help:effective-pom`)
3. **Gradually test complex goals** (like `validate`, `compile`)
4. **Monitor memory usage** and adjust settings as needed

The Maven embedder infrastructure is working correctly - we just need to give it enough memory to handle the Quarkus project successfully.