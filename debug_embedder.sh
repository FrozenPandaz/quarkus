#!/bin/bash

# Debug script to test the Maven embedder executor
echo "Testing Maven embedder executor..."

JAVA_CMD="java -cp \"/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/target/embedder-executor-999-SNAPSHOT.jar:/home/jason/projects/triage/java/quarkus/maven-plugin/graph-analyzer/target/graph-analyzer-999-SNAPSHOT.jar:/home/jason/projects/triage/java/quarkus/maven-plugin/nx-plugin-core/target/dependency/*\" -Dmaven.multiModuleProjectDirectory=\"/home/jason/projects/triage/java/quarkus\" NxMavenEmbedderBatchExecutor"

echo "Running command: $JAVA_CMD"
echo "----------------------------------------"

eval "$JAVA_CMD" 2>&1
EXIT_CODE=$?

echo "----------------------------------------"
echo "Exit code: $EXIT_CODE"

# Also try with verbose error output
echo ""
echo "Trying with ClassNotFoundException detection..."
echo "----------------------------------------"
eval "$JAVA_CMD" 2>&1 | grep -i "class\|error\|exception" || echo "No class errors found"