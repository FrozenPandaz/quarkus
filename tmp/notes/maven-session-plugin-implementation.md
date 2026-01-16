# Maven Session Plugin Implementation

## Overview
Implemented Maven plugin goals for session capture/restore that run within the same Maven session as build goals, providing proper session state sharing and persistence.

## Architecture

### Maven Plugin Goals

#### 1. LoadSessionMojo (`nx:load-session`)
- **Phase**: `INITIALIZE` (runs first)
- **Purpose**: Loads session context from disk and populates Maven session
- **Conditional**: Only executes when `nx.session.enabled=true` property is set

**Features:**
- Loads `.nx-maven-sessions/{project}.json` for all projects in reactor
- Restores user properties to Maven session
- Sets artifact location properties for other goals to use
- Restores build directory paths and local repository information

#### 2. SaveSessionMojo (`nx:save-session`)
- **Phase**: `INSTALL` (runs last)
- **Purpose**: Captures session state and writes to disk
- **Conditional**: Only executes when `nx.session.enabled=true` property is set

**Features:**
- Captures all projects in the reactor session
- Saves main artifacts and attached artifacts (sources, javadoc, tests)
- Records resolved dependencies with file paths
- Stores build directories and project metadata

### Batch Executor Integration

**Modified `NxMavenBatchExecutor.executeMultiProjectGoalsWithInvoker()`:**
```kotlin
val request = DefaultInvocationRequest().apply {
    pomFile = rootPomFile
    baseDirectory = workspaceDir
    
    // Enable session management
    val props = Properties()
    props.setProperty("nx.session.enabled", "true")
    properties = props
    
    // Wrap user goals with session goals
    val sessionAwareGoals = listOf("nx:load-session") + goals + listOf("nx:save-session")
    setGoals(sessionAwareGoals)
}
```

## Execution Flow

1. **Maven Lifecycle Execution**:
   ```
   INITIALIZE: nx:load-session (loads all project sessions)
   COMPILE: user goals execute with session context
   INSTALL: nx:save-session (saves all project sessions)
   ```

2. **Single Maven Session**: All goals share the same session, enabling proper state sharing

3. **Multi-Project Support**: Goals handle all projects in the reactor automatically

## Session Data Format

**File**: `.nx-maven-sessions/{project-artifactId}.json`

```json
{
  "userProperties": {
    "nx.property": "value",
    "maven.repo.local": "/path/to/.m2/repository"
  },
  "artifacts": [
    {
      "file": "/path/to/target/app.jar",
      "groupId": "com.example",
      "artifactId": "app",
      "version": "1.0.0",
      "type": "jar",
      "classifier": null
    }
  ],
  "dependencies": [
    {
      "file": "/path/to/.m2/repository/org/junit/junit.jar",
      "groupId": "org.junit",
      "artifactId": "junit",
      "version": "4.13.2",
      "scope": "test"
    }
  ],
  "buildDirectory": "/path/to/target",
  "outputDirectory": "/path/to/target/classes",
  "localRepository": "/path/to/.m2/repository",
  "projectInfo": {
    "groupId": "com.example",
    "artifactId": "app",
    "version": "1.0.0",
    "packaging": "jar"
  }
}
```

## Benefits

1. **Native Maven Integration**: Uses Maven's own session management
2. **Full Session Access**: Complete access to MavenSession and MavenProject objects
3. **Automatic Ordering**: Lifecycle phases guarantee load→goals→save sequence
4. **Conditional Execution**: Only runs when invoked via Nx batch executor
5. **Multi-Project Aware**: Handles all projects in reactor automatically
6. **Nx Integration**: Session files included as project outputs for caching

## Safety Features

- **Property-based gating**: Goals only execute when `nx.session.enabled=true`
- **Error handling**: Graceful handling of missing or corrupted session files
- **Debug logging**: Detailed logging for session operations when verbose enabled
- **Workspace detection**: Automatically finds workspace root for session storage

## Usage

**Automatic via Batch Executor:**
```bash
# Session management happens transparently
java -cp maven-plugin/target/classes NxMavenBatchExecutor "compile,test" "." "my-module"
```

**Manual Testing:**
```bash
# Won't execute session goals (property not set)
mvn compile test

# Will execute session goals (property set)
mvn -Dnx.session.enabled=true nx:load-session compile test nx:save-session
```

This implementation provides robust, Maven-native session persistence that integrates seamlessly with Nx's caching and task execution model.