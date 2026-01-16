# Maven Session Goal Results Tracking

## Enhancement Overview

Extended the Maven session persistence system to capture and store goal execution results alongside session data. This provides comprehensive tracking of what goals were executed, their outcomes, and timing information.

## Implementation Details

### SaveSessionMojo Enhancements

#### New Goal Results Capture
**File**: `SaveSessionMojo.kt:149-155`
```kotlin
// Capture goal execution results
val goalResults = captureGoalResults(project)
sessionData["goalResults"] = goalResults

// Capture execution timestamp
sessionData["executionTimestamp"] = System.currentTimeMillis()
sessionData["executionDate"] = java.time.Instant.now().toString()
```

#### Goal Results Data Structure
The `captureGoalResults()` method captures:
- **Requested Goals**: List of goals from the Maven request
- **Lifecycle Phases**: Detailed breakdown of each goal with plugin information
- **Build Success Status**: Whether the build completed successfully
- **Exception Information**: Error details if the build failed
- **Timing Data**: Session start time and execution timestamps
- **Execution Context**: Project base directory and execution root

#### Plugin Information Parsing
**File**: `SaveSessionMojo.kt:201-213`
```kotlin
// Try to extract plugin info from goal
if (goal.contains(":")) {
    val parts = goal.split(":")
    if (parts.size >= 2) {
        phaseInfo["plugin"] = "${parts[0]}:${parts[1]}"
        if (parts.size >= 3) {
            phaseInfo["goalName"] = parts[2]
        }
        if (parts.size >= 4) {
            phaseInfo["execution"] = parts[3]
        }
    }
}
```

### LoadSessionMojo Enhancements

#### Goal Results Property Exposure
**File**: `LoadSessionMojo.kt:135-140`
```kotlin
// Load and expose goal results information
sessionData["goalResults"]?.let { goalResults ->
    if (goalResults is Map<*, *>) {
        loadGoalResults(project, goalResults)
    }
}
```

#### Maven Properties Integration
The `loadGoalResults()` method exposes captured data as Maven properties:
- `nx.{artifactId}.goalResults.requestedGoals` - Comma-separated list of goals
- `nx.{artifactId}.goalResults.buildSuccess` - Success status (true/false)
- `nx.{artifactId}.goalResults.executionTimestamp` - When session was saved
- `nx.{artifactId}.goalResults.phase.{index}.goal` - Individual goal information
- `nx.{artifactId}.goalResults.phase.{index}.plugin` - Plugin coordinates
- `nx.{artifactId}.goalResults.exception.{index}.message` - Error information

## Use Cases

### 1. Build Result Validation
```xml
<!-- Check if previous compilation was successful -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-antrun-plugin</artifactId>
  <executions>
    <execution>
      <phase>validate</phase>
      <goals>
        <goal>run</goal>
      </goals>
      <configuration>
        <target>
          <condition property="compile.success">
            <equals arg1="${nx.maven-plugin.goalResults.buildSuccess}" arg2="true"/>
          </condition>
          <fail unless="compile.success" message="Previous compilation failed!"/>
        </target>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 2. Conditional Goal Execution
```xml
<!-- Only run tests if compile phase was executed -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <skipTests>${nx.maven-plugin.goalResults.buildSuccess}</skipTests>
  </configuration>
</plugin>
```

### 3. Build Analytics
- Track which goals are commonly executed together
- Measure execution timing across different runs
- Analyze failure patterns and error types
- Generate build reports with historical context

### 4. Dependency Resolution
- Verify that dependency goals (like `compile`) completed successfully
- Skip redundant goals if already executed in current session
- Chain goals based on previous execution results

## Data Structure Example

```json
{
  "goalResults": {
    "projectBasedir": "/path/to/project",
    "executionRootDirectory": "/path/to/workspace",
    "requestedGoals": ["compile", "test"],
    "buildSuccess": true,
    "sessionStartTime": 1641234567890,
    "currentTime": 1641234570123,
    "lifecyclePhases": [
      {
        "goal": "org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile",
        "plugin": "org.apache.maven.plugins:maven-compiler-plugin",
        "goalName": "compile",
        "execution": "default-compile",
        "timestamp": 1641234568000
      }
    ],
    "exceptions": []
  },
  "executionTimestamp": 1641234570123,
  "executionDate": "2024-01-03T15:42:50.123Z"
}
```

## Benefits

### Enhanced Build Intelligence
- **Goal Dependency Awareness**: Maven goals can check if prerequisite goals completed successfully
- **Failure Context**: Detailed error information available for debugging and recovery
- **Performance Tracking**: Timing data enables optimization of build workflows

### Nx Integration
- **Cache Validation**: Use goal results to validate cached outputs
- **Task Dependencies**: Better understanding of which tasks depend on each other
- **Build Analytics**: Rich data for build performance analysis

### Developer Experience
- **Better Error Messages**: Context about what goals failed and why
- **Build Optimization**: Skip unnecessary goals based on previous results
- **Debugging Support**: Complete execution context available for troubleshooting

## Property Naming Convention

All goal results are exposed under the prefix:
```
nx.{project-artifact-id}.goalResults.{property-name}
```

This ensures namespace isolation and makes it easy for other Maven plugins or goals to access the information programmatically.

## Backward Compatibility

This enhancement is fully backward compatible:
- Existing session files without goal results continue to work
- Goal results are optional and gracefully handle missing data
- No changes to existing Maven property patterns
- LoadSessionMojo safely handles both old and new session file formats