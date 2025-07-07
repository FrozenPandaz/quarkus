# Maven Java Command Logging Enhancement

## Changes Made

Added comprehensive logging for Java commands that execute the Maven batch embedder executor across both executor implementations.

## Files Modified

### 1. executor-embedder.ts
- **Line 167-177**: Added detailed logging for single-task Maven Embedder execution
- **Line 454-464**: Added detailed logging for multi-project batch Maven Embedder execution
- **Line 581-588**: Enhanced PseudoTerminal execution logging

### 2. executor.ts  
- **Line 102-112**: Added detailed logging for single-task Maven batch execution
- **Line 343-353**: Added detailed logging for multi-project batch execution
- **Line 457-464**: Enhanced PseudoTerminal execution logging

## Logging Details

The enhanced logging now includes:

### Java Command Details
- **Goals**: List of Maven goals being executed
- **Projects**: Target projects for execution
- **Working directory**: Where the command runs
- **Java executable**: Always "java"
- **System property**: Maven multi-module project directory
- **Classpath**: Full classpath including dependencies
- **Main class**: NxMavenEmbedderBatchExecutor
- **Arguments**: All command-line arguments passed to the Java process
- **Full command**: Complete command string for debugging

### Execution Context
- **PseudoTerminal**: Logs when command execution starts
- **Working directory**: Where the command executes
- **Verbose mode**: Additional logging when enabled

## Benefits

1. **Debugging**: Full visibility into Java command construction and execution
2. **Troubleshooting**: Easy to identify classpath, argument, or environment issues
3. **Monitoring**: Clear tracking of what commands are being executed
4. **Transparency**: Complete audit trail of Maven embedder invocations

## Usage

The logging is now active by default - no need to enable verbose mode for basic command logging. Verbose mode still provides additional execution details.

## Example Output

```
Maven Embedder Java Command:
  Goals: compile, test
  Project: .
  Working directory: /workspace/maven-plugin
  Java executable: java
  System property: -Dmaven.multiModuleProjectDirectory="/workspace"
  Classpath: /workspace/maven-plugin/target/classes:/workspace/maven-plugin/target/dependency/*
  Main class: NxMavenEmbedderBatchExecutor
  Arguments: "compile,test" "/workspace" "." true
  Full command: java -Dmaven.multiModuleProjectDirectory="/workspace" -cp "/workspace/maven-plugin/target/classes:/workspace/maven-plugin/target/dependency/*" NxMavenEmbedderBatchExecutor "compile,test" "/workspace" "." true
```