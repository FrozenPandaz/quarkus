# Analyzer Switching Implementation

## Overview

Implemented environment variable-based switching between simple and complex Maven analyzers, with simple analyzer as default.

## Implementation Details

### Environment Variable: `NX_MAVEN_COMPLEX_ANALYZER`
- **Default**: `false` (simple analyzer)
- **Complex Mode**: Set to `'true'` to use complex analyzer
- **Detection**: `process.env.NX_MAVEN_COMPLEX_ANALYZER === 'true'`

### Code Changes in `maven-plugin.ts`

#### 1. Analyzer Selection Logic (`runMavenAnalysis:179-181`)
```typescript
const useComplexAnalyzer = process.env.NX_MAVEN_COMPLEX_ANALYZER === 'true';
const analyzerType = useComplexAnalyzer ? 'complex' : 'simple';
```

#### 2. Dynamic Maven Goal Selection (`lines 200-209`)
```typescript
const mavenArgs = useComplexAnalyzer ? [
    'io.quarkus:graph-analyzer:999-SNAPSHOT:analyze',
    `-Dnx.outputFile=${outputFile}`,
    `-Dnx.verbose=${isVerbose}`
] : [
    'io.quarkus:simple-graph-analyzer:999-SNAPSHOT:simple-analyze',
    `-Dnx.outputFile=${outputFile}`,
    `-Dnx.verbose=${isVerbose}`
];
```

#### 3. Enhanced Analyzer Detection (`findJavaAnalyzer:277`)
- **Parameters**: `useComplex: boolean = false`
- **Logic**: Prioritizes requested analyzer type, falls back to available alternative
- **Simple Paths**: `simple-graph-analyzer/target/classes`, `simple-graph-analyzer/target/*.jar`
- **Complex Paths**: `graph-analyzer/target/classes`, `graph-analyzer/target/*.jar`

#### 4. Enhanced Logging
```typescript
if (isVerbose) {
    console.log(`Analyzer type: ${analyzerType} (NX_MAVEN_COMPLEX_ANALYZER=${process.env.NX_MAVEN_COMPLEX_ANALYZER || 'false'})`);
}
```

### Usage Examples

#### Simple Analyzer (Default)
```bash
nx show projects
nx graph
```

#### Complex Analyzer (Temporary)
```bash
NX_MAVEN_COMPLEX_ANALYZER=true nx show projects
NX_MAVEN_COMPLEX_ANALYZER=true nx graph
```

#### Complex Analyzer (Session)
```bash
export NX_MAVEN_COMPLEX_ANALYZER=true
nx show projects
nx graph
```

## Analyzer Comparison

### Simple Analyzer
- **Plugin**: `simple-graph-analyzer`
- **Goal**: `simple-analyze`
- **Features**: Basic run-commands targets
- **Performance**: Fast
- **Use Case**: Standard Maven project analysis

### Complex Analyzer  
- **Plugin**: `graph-analyzer`
- **Goal**: `analyze`
- **Features**: Comprehensive lifecycle analysis, advanced target generation
- **Performance**: Slower but more detailed
- **Use Case**: Complex Maven projects requiring detailed analysis

## Documentation Updates

Updated `CLAUDE.md` with:
- Environment variable documentation
- Usage examples for both analyzers
- Performance and feature comparisons
- Clear default behavior explanation

## Benefits

1. **Easy Switching**: Single environment variable controls analyzer type
2. **Backward Compatibility**: Simple analyzer remains default
3. **Development Friendly**: Can test both analyzers without code changes  
4. **Clear Feedback**: Verbose logging shows which analyzer is active
5. **Graceful Fallback**: Falls back to available analyzer if preferred not compiled

This implementation makes it easy to compare analyzer behaviors and choose the appropriate one for different use cases.