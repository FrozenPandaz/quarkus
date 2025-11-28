# Maven Embedder Classpath Conflicts Investigation

## Root Cause Analysis

After investigating the "Duplicate key pom" errors in the Maven embedder executor, I've identified the core classpath conflicts causing the issues.

## Primary Conflict: Multiple DefaultType Classes

### Conflicting Classes Found:

1. **maven-resolver-provider-4.0.0-rc-3.jar**
   - `org/apache/maven/repository/internal/type/DefaultType.class`
   - `org/apache/maven/repository/internal/type/DefaultTypeProvider.class`

2. **maven-impl-4.0.0-rc-3.jar** 
   - `org/apache/maven/impl/resolver/type/DefaultType.class`
   - `org/apache/maven/impl/resolver/type/DefaultTypeProvider.class`

3. **maven-core-4.0.0-rc-3.jar**
   - `org/apache/maven/internal/impl/DefaultTypeRegistry.class`

### TypeRegistry Conflicts:

1. **maven-resolver-api-2.0.7.jar**
   - `org/eclipse/aether/DefaultRepositorySystemSession$NullArtifactTypeRegistry.class`
   - `org/eclipse/aether/artifact/ArtifactTypeRegistry.class`

2. **maven-resolver-util-2.0.7.jar**
   - `org/eclipse/aether/util/artifact/DefaultArtifactTypeRegistry.class`
   - `org/eclipse/aether/util/artifact/OverlayArtifactTypeRegistry.class`
   - `org/eclipse/aether/util/artifact/SimpleArtifactTypeRegistry.class`

3. **org.eclipse.sisu.plexus-0.9.0.M3.jar**
   - `org/eclipse/sisu/plexus/PlexusTypeRegistry.class`

## Architecture Issue

The problem stems from Maven 4.0's architectural changes:

### Maven 4.0 Structure:
- **New API Layer**: `maven-api-*` modules provide clean public APIs
- **Implementation Layer**: `maven-impl-4.0.0-rc-3.jar` contains new implementation
- **Legacy Compatibility**: `maven-resolver-provider-4.0.0-rc-3.jar` maintains backward compatibility

### Classpath Pollution:
The embedder-executor's POM includes both:
1. **maven-core** (pulls in maven-impl with new `DefaultType`)
2. **maven-resolver-provider** (contains old `DefaultType` for compatibility)

## Specific Files Causing Issues

### `/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/pom.xml`
- Lines 28-32: `maven-core` dependency
- Lines 34-38: `maven-embedder` dependency  
- Lines 47-52: `maven-resolver-provider` dependency (CONFLICT SOURCE)

### `/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/src/main/kotlin/NxMavenEmbedderBatchExecutor.kt`
- Line 316: `ClassWorld("plexus.core", Thread.currentThread().contextClassLoader)`
- Lines 333-341: Plexus container creation with conflicting dependencies

## Environment Conflicts

### Maven Version Mismatch:
- **System Maven**: 3.9.10 (at `/opt/apache-maven-3.9.10`)
- **Embedder Maven**: 4.0.0-rc-3 (configured in POM)
- **Classpath Maven**: Mixed 4.0.0-rc-3 and 2.0.7 resolver versions

## Suggested Fix Approach

### 1. Dependency Exclusion Strategy
Remove conflicting legacy dependencies from embedder-executor/pom.xml:

```xml
<dependency>
    <groupId>org.apache.maven</groupId>
    <artifactId>maven-core</artifactId>
    <version>${maven.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.apache.maven</groupId>
            <artifactId>maven-resolver-provider</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 2. Use Maven 4.0 Pure API Approach
Replace legacy dependencies with pure Maven 4.0 API modules:
- Remove `maven-resolver-provider`  
- Remove `maven-compat`
- Use only `maven-core` + `maven-embedder` + new `maven-api-*` modules

### 3. ClassLoader Isolation
Implement parent-first class loading to prioritize Maven 4.0 implementations:

```kotlin
val parentRealm = classWorld.getClassRealm("maven")
parentRealm?.addConstituent(/* Maven 4.0 core JARs only */)
```

### 4. Type Registry Binding Fix
Explicitly bind single TypeRegistry implementation in Guice module:

```kotlin
override fun configure() {
    bind(ArtifactTypeRegistry::class.java)
        .to(DefaultArtifactTypeRegistry::class.java)
        .`in`(Singleton::class.java)
}
```

## Impact Analysis

- **Current State**: "Duplicate key pom" errors prevent Maven execution
- **Fix Required**: Remove dependency conflicts in embedder-executor/pom.xml
- **Testing Needed**: Verify Maven 4.0 compatibility without legacy deps
- **Risk**: May break some Maven 3.x compatibility features