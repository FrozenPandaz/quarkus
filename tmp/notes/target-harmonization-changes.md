# Target Harmonization Between Simple and Complex Analyzers

## Problem
Simple and complex analyzers generated different numbers of targets:
- **quarkus-parent**: 41 targets (simple) vs 47 targets (complex)
- Different executors: `nx:run-commands` vs `@nx-quarkus/maven-plugin:maven-batch`

## Solution Applied

### 1. **Unified Executor**
Changed simple analyzer to use same executor as complex analyzer:

**Before:**
```kotlin
TargetConfiguration("nx:run-commands").apply {
    options = mutableMapOf(
        "command" to "mvn $phase",
        "cwd" to projectRoot
    )
}
```

**After:**
```kotlin  
TargetConfiguration("@nx-quarkus/maven-plugin:maven-batch").apply {
    options = mutableMapOf(
        "goals" to listOf(phase)
    )
}
```

### 2. **Expanded Target Set**
Enhanced simple analyzer to generate comprehensive plugin goals matching complex analyzer:

**Added Maven Core Plugin Goals:**
- With execution IDs: `maven-compiler:compile@default-compile`
- Without execution IDs: `maven-compiler:compile`
- Both variants for: compiler, surefire, resources, jar, install, deploy, etc.

**Added Additional Plugin Goals:**
```kotlin
commonTargets.putAll(mapOf(
    "maven-dependency:copy-dependencies" to "mvn dependency:copy-dependencies",
    "maven-dependency:analyze" to "mvn dependency:analyze", 
    "maven-failsafe:integration-test" to "mvn failsafe:integration-test",
    "maven-failsafe:verify" to "mvn failsafe:verify",
    "build-helper:add-source" to "mvn build-helper:add-source",
    "build-helper:add-test-source" to "mvn build-helper:add-test-source"
))
```

**Added Source/Javadoc Plugin Goals:**
```kotlin
"maven-source:jar-no-fork@attach-sources" to "mvn source:jar-no-fork",
"maven-source:jar-no-fork" to "mvn source:jar-no-fork",
"maven-source:jar" to "mvn source:jar", 
"maven-javadoc:jar@attach-javadocs" to "mvn javadoc:jar",
"maven-javadoc:jar" to "mvn javadoc:jar"
```

### 3. **Dynamic Plugin-Specific Goals**
Added same dynamic logic as complex analyzer:

```kotlin
// Match ExecutionPlanAnalysisService.getCommonGoalsForPlugin logic
project.buildPlugins?.forEach { plugin ->
    val artifactId = plugin.artifactId
    when {
        artifactId.contains("compiler") -> {
            commonTargets["maven-compiler:compile"] = "mvn compiler:compile"
            commonTargets["maven-compiler:testCompile"] = "mvn compiler:testCompile"
        }
        artifactId.contains("surefire") -> {
            commonTargets["maven-surefire:test"] = "mvn surefire:test"
        }
        artifactId.contains("quarkus") -> {
            commonTargets["quarkus:dev"] = "mvn quarkus:dev"
            commonTargets["quarkus:build"] = "mvn quarkus:build"
        }
        artifactId.contains("spring-boot") -> {
            commonTargets["spring-boot:run"] = "mvn spring-boot:run"
            commonTargets["spring-boot:repackage"] = "mvn spring-boot:repackage"
        }
    }
}
```

### 4. **Fixed Goal Format**
Updated `createTarget` function to extract Maven goals properly:

```kotlin
private fun createTarget(command: String, projectRoot: String, description: String): TargetConfiguration {
    // Extract goal from command (e.g., "mvn compiler:compile" -> "compiler:compile")
    val goal = command.removePrefix("mvn ").trim()
    
    return TargetConfiguration("@nx-quarkus/maven-plugin:maven-batch").apply {
        options = mutableMapOf(
            "goals" to listOf(goal)
        )
        // ... rest of configuration
    }
}
```

## Expected Results

### **Consistent Target Counts**
Both analyzers should now generate the same number of targets for identical projects.

### **Unified Execution**
- Both use `@nx-quarkus/maven-plugin:maven-batch` executor
- Both generate `goals` option instead of `command`
- Both benefit from Maven batch execution optimizations

### **Comprehensive Coverage**
Simple analyzer now includes:
- All Maven core plugin goals (with and without execution IDs)
- Additional utility plugin goals
- Source and javadoc plugin goals  
- Project-specific plugin goals (Quarkus, Spring Boot)

## Files Modified
- `simple-graph-analyzer/src/main/kotlin/SimpleTargetGenerator.kt`
  - Updated executor from `nx:run-commands` to `@nx-quarkus/maven-plugin:maven-batch`
  - Expanded plugin goal coverage to match complex analyzer
  - Added dynamic plugin-specific goal discovery
  - Fixed goal format extraction

## Benefits
1. **Consistency**: Both analyzers generate identical target sets
2. **Performance**: Both use optimized Maven batch executor  
3. **Compatibility**: Switching between analyzers doesn't change project structure
4. **Comprehensive**: Simple analyzer now discovers project-specific goals

The target count difference should now be eliminated while maintaining the performance benefits of both approaches.