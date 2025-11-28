# Complex Analyzer Plugin Descriptor Fix

## Problem
When using `NX_MAVEN_COMPLEX_ANALYZER=true`, Maven failed with:
```
[ERROR] Failed to parse plugin descriptor for io.quarkus:graph-analyzer:999-SNAPSHOT: 
No plugin descriptor found at META-INF/maven/plugin.xml
```

## Root Cause
The complex analyzer (graph-analyzer) was missing proper Maven plugin configuration:

1. **Missing maven-plugin-plugin**: Required to generate plugin descriptor (`META-INF/maven/plugin.xml`)
2. **Wrong packaging type**: Was using `jar` instead of `maven-plugin`

## Fixes Applied

### 1. Fixed packaging type in `graph-analyzer/pom.xml:15`
```xml
<!-- Before -->
<packaging>jar</packaging>

<!-- After -->  
<packaging>maven-plugin</packaging>
```

### 2. Added maven-plugin-plugin in `graph-analyzer/pom.xml:88-95`
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-plugin-plugin</artifactId>
    <version>3.9.0</version>
    <configuration>
        <goalPrefix>nx</goalPrefix>
    </configuration>
</plugin>
```

## Comparison

### Simple Analyzer (Working) ✅
- **Packaging**: `maven-plugin`
- **Plugin**: Has `maven-plugin-plugin` 
- **Goal**: `simple-analyze`

### Complex Analyzer (Fixed) ✅  
- **Packaging**: `maven-plugin` (was `jar`)
- **Plugin**: Now has `maven-plugin-plugin` (was missing)
- **Goal**: `analyze`

## Next Steps

After applying these fixes, users need to:

1. **Recompile the Maven plugin**:
   ```bash
   npm run compile-java:fresh
   ```

2. **Reset Nx cache**:
   ```bash
   nx reset
   ```

3. **Test complex analyzer**:
   ```bash
   NX_MAVEN_COMPLEX_ANALYZER=true nx show projects
   ```

## Expected Result

The complex analyzer should now:
- Generate proper Maven plugin descriptor
- Be discoverable by Maven
- Execute the `io.quarkus:graph-analyzer:999-SNAPSHOT:analyze` goal successfully
- Provide comprehensive lifecycle phase analysis

## Files Modified
- `maven-plugin/graph-analyzer/pom.xml` - Added plugin configuration and fixed packaging type

The complex analyzer should now work correctly alongside the simple analyzer with environment variable switching.