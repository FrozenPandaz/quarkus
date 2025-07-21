#!/bin/bash

echo "=== Fixing Maven Embedder Executor ==="

# 1. Recompile the Java components
echo "Step 1: Recompiling Java components..."
npm run compile-java:fresh

if [ $? -eq 0 ]; then
    echo "✅ Java compilation successful"
else
    echo "❌ Java compilation failed"
    exit 1
fi

# 2. Reset Nx state
echo "Step 2: Resetting Nx state..."
nx reset

# 3. Test the fixed executor
echo "Step 3: Testing with enhanced debugging..."
nx install quarkus-core

echo "=== Fix attempt completed ==="