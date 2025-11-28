#!/bin/bash

# Test script to directly invoke the embedder executor
echo "=== Testing Maven Embedder Executor Directly ==="

WORKSPACE_ROOT="/home/jason/projects/triage/java/quarkus"
PLUGIN_DIR="$WORKSPACE_ROOT/maven-plugin"

# Check if JARs exist
EMBEDDER_JAR="$PLUGIN_DIR/embedder-executor/target/embedder-executor-999-SNAPSHOT.jar"
ANALYZER_JAR="$PLUGIN_DIR/graph-analyzer/target/graph-analyzer-999-SNAPSHOT.jar"  
DEPENDENCY_DIR="$PLUGIN_DIR/nx-plugin-core/target/dependency"

echo "Checking required files:"
echo "  Embedder JAR: $EMBEDDER_JAR"
if [ -f "$EMBEDDER_JAR" ]; then
    echo "    ✅ EXISTS"
else
    echo "    ❌ MISSING"
    exit 1
fi

echo "  Analyzer JAR: $ANALYZER_JAR"
if [ -f "$ANALYZER_JAR" ]; then
    echo "    ✅ EXISTS"
else
    echo "    ❌ MISSING" 
    exit 1
fi

echo "  Dependency directory: $DEPENDENCY_DIR"
if [ -d "$DEPENDENCY_DIR" ]; then
    echo "    ✅ EXISTS"
    DEP_COUNT=$(ls -1 "$DEPENDENCY_DIR"/*.jar 2>/dev/null | wc -l)
    echo "    📦 Contains $DEP_COUNT JAR files"
    
    # List some key dependencies
    echo "    Key Maven dependencies:"
    ls -1 "$DEPENDENCY_DIR" | grep -E "(maven-core|maven-embedder|plexus|sisu)" | head -5 | sed 's/^/      /'
else
    echo "    ❌ MISSING"
    exit 1
fi

echo ""
echo "Testing minimal invocation (should show usage):"
echo "----------------------------------------"

CLASSPATH="$EMBEDDER_JAR:$ANALYZER_JAR:$DEPENDENCY_DIR/*"
JAVA_CMD="java -Dmaven.multiModuleProjectDirectory=\"$WORKSPACE_ROOT\" -cp \"$CLASSPATH\" NxMavenEmbedderBatchExecutor"

echo "Command: $JAVA_CMD"
echo ""
echo "Output:"
$JAVA_CMD 2>&1

EXIT_CODE=$?
echo ""
echo "Exit code: $EXIT_CODE"

if [ $EXIT_CODE -eq 1 ]; then
    echo "✅ Expected exit code 1 for insufficient arguments - main class is being found and invoked"
else
    echo "❌ Unexpected exit code - there may be a classpath or main class issue"
fi

echo ""
echo "=== Test completed ==="