#!/bin/bash

# Test script for NxMavenEmbedderBatchExecutor with real goal execution

echo "=== Testing Maven Embedder Batch Executor ==="
echo "Current directory: $(pwd)"
echo "Testing with goal: validate"
echo

# Check if classes exist
if [ -f "target/classes/NxMavenEmbedderBatchExecutor.class" ]; then
    echo "✅ NxMavenEmbedderBatchExecutor.class found"
else
    echo "❌ NxMavenEmbedderBatchExecutor.class not found"
    echo "Available classes:"
    find target/classes -name "*.class" | grep -i embedder
fi

# Build classpath
CLASSPATH="target/classes"
for jar in $(find target/dependency -name "*.jar" 2>/dev/null); do
    CLASSPATH="$CLASSPATH:$jar"
done

# Add Maven dependencies
if [ -f "pom.xml" ]; then
    MAVEN_CLASSPATH=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout 2>/dev/null)
    if [ $? -eq 0 ]; then
        CLASSPATH="$CLASSPATH:$MAVEN_CLASSPATH"
        echo "✅ Maven classpath added"
    else
        echo "⚠️  Maven classpath not available"
    fi
fi

echo "Classpath length: ${#CLASSPATH}"

# Test execution
echo
echo "=== Executing Test ==="
java -cp "$CLASSPATH" NxMavenEmbedderBatchExecutor "validate" "." "." true

echo
echo "=== Test Complete ==="