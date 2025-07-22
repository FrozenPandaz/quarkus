import {
    CreateDependencies,
    CreateNodesResultV2,
    CreateNodesV2,
    readJsonFile,
    workspaceRoot,
    writeJsonFile,
} from '@nx/devkit';
import {join} from 'path';
import {existsSync, rmSync, readFileSync} from 'fs';
import {spawn} from 'child_process';
import {workspaceDataDirectory} from 'nx/src/utils/cache-directory';
import {calculateHashForCreateNodes} from '@nx/devkit/src/utils/calculate-hash-for-create-nodes';

export interface MavenPluginOptions {
    verbose?: boolean;
}

const DEFAULT_OPTIONS: MavenPluginOptions = {};

// Global cache to avoid running Maven analysis multiple times
let globalAnalysisCache: any = null;
let globalCacheKey: string | null = null;

// Cache management functions
function readMavenCache(cachePath: string): Record<string, any> {
    try {
        return existsSync(cachePath) ? readJsonFile(cachePath) : {};
    } catch {
        return {};
    }
}

function writeMavenCache(cachePath: string, cache: Record<string, any>) {
    try {
        writeJsonFile(cachePath, cache);
    } catch (error) {
        console.warn('Failed to write Maven cache:', error.message);
    }
}

/**
 * Maven plugin that delegates to Java for analysis and returns results directly
 */
export const createNodesV2: CreateNodesV2 = [
    '**/pom.xml',
    async (configFiles, options, context): Promise<CreateNodesResultV2> => {
        const opts: MavenPluginOptions = {...DEFAULT_OPTIONS, ...(options as MavenPluginOptions)};
        
        // Check for verbose logging from multiple sources
        const isVerbose = opts.verbose || process.env.NX_VERBOSE_LOGGING === 'true' || process.argv.includes('--verbose');

        if (isVerbose) {
            console.log(`\n🔍 [GRAPH-PLUGIN] ===========================================`);
            console.log(`🔍 [GRAPH-PLUGIN] Maven createNodesV2 starting...`);
            console.log(`🔍 [GRAPH-PLUGIN] Found ${configFiles.length} pom.xml files initially`);
            console.log(`🔍 [GRAPH-PLUGIN] Workspace root: ${context.workspaceRoot}`);
            console.log(`🔍 [GRAPH-PLUGIN] Plugin options:`, opts);
            console.log(`🔍 [GRAPH-PLUGIN] Context nxJsonConfiguration:`, context.nxJsonConfiguration?.plugins || 'none');
            console.log(`🔍 [GRAPH-PLUGIN] ===========================================`);
        }

        // Filter out unwanted pom.xml files
        const filteredFiles = configFiles.filter(file =>
            !file.includes('maven-script/') &&
            !file.includes('target/') &&
            !file.includes('node_modules/')
        );

        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] Filtering pom.xml files:`);
            configFiles.forEach(file => {
                const isFiltered = !filteredFiles.includes(file);
                console.log(`  ${isFiltered ? '❌ FILTERED' : '✅ INCLUDED'}: ${file}`);
                if (isFiltered) {
                    if (file.includes('maven-script/')) console.log(`      Reason: Contains 'maven-script/'`);
                    if (file.includes('target/')) console.log(`      Reason: Contains 'target/'`);
                    if (file.includes('node_modules/')) console.log(`      Reason: Contains 'node_modules/'`);
                }
            });
            console.log(`🔍 [GRAPH-PLUGIN] After filtering: ${filteredFiles.length} pom.xml files`);
        }

        if (filteredFiles.length === 0) {
            if (isVerbose) {
                console.log(`🔍 [GRAPH-PLUGIN] No valid pom.xml files found after filtering - returning empty result`);
            }
            return [];
        }

        // Generate cache key based on pom.xml files and options
        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] Generating cache key for analysis...`);
            console.log(`🔍 [GRAPH-PLUGIN] Options for hashing:`, options);
        }
        
        const projectHash = await calculateHashForCreateNodes(
            workspaceRoot,
            (options as object) ?? {},
            context,
            ['{projectRoot}/pom.xml', '{workspaceRoot}/**/pom.xml']
        );
        const cacheKey = projectHash;

        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] Generated cache key: ${cacheKey}`);
        }

        // OPTIMIZATION: Check global in-memory cache first
        if (globalAnalysisCache && globalCacheKey === cacheKey) {
            if (isVerbose) {
                console.log(`🔍 [GRAPH-PLUGIN] ✅ HIT: Using global in-memory cache for createNodes`);
                console.log(`🔍 [GRAPH-PLUGIN] Cached result contains ${globalAnalysisCache.createNodesResults?.length || 0} project nodes`);
            }
            return globalAnalysisCache.createNodesResults || [];
        }

        // Set up cache path
        const cachePath = join(workspaceDataDirectory, 'maven-analysis-cache.json');
        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] Cache file path: ${cachePath}`);
        }
        const cache = readMavenCache(cachePath);

        // Check if we have valid cached results
        if (cache[cacheKey]) {
            if (isVerbose) {
                console.log(`🔍 [GRAPH-PLUGIN] ✅ HIT: Using disk-cached Maven analysis results for createNodes`);
                console.log(`🔍 [GRAPH-PLUGIN] Cached result contains ${cache[cacheKey].createNodesResults?.length || 0} project nodes`);
            }
            // Store in global cache for faster subsequent access
            globalAnalysisCache = cache[cacheKey];
            globalCacheKey = cacheKey;
            return cache[cacheKey].createNodesResults || [];
        }

        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] ❌ MISS: No cache found - running fresh Maven analysis`);
        }

        // Run analysis if not cached
        const result = await runMavenAnalysis({...opts, verbose: isVerbose});

        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] Maven analysis completed - processing results...`);
            console.log(`🔍 [GRAPH-PLUGIN] Analysis result keys:`, Object.keys(result || {}));
            console.log(`🔍 [GRAPH-PLUGIN] CreateNodes results count: ${result?.createNodesResults?.length || 0}`);
            console.log(`🔍 [GRAPH-PLUGIN] CreateDependencies results count: ${result?.createDependencies?.length || 0}`);
        }

        // Cache the complete result
        cache[cacheKey] = result;
        writeMavenCache(cachePath, cache);

        // Store in global cache
        globalAnalysisCache = result;
        globalCacheKey = cacheKey;

        if (isVerbose) {
            console.log(`🔍 [GRAPH-PLUGIN] Results cached successfully`);
            console.log(`🔍 [GRAPH-PLUGIN] Returning ${result.createNodesResults?.length || 0} project nodes`);
            if (result.createNodesResults?.length > 0) {
                console.log(`🔍 [GRAPH-PLUGIN] Project nodes summary:`);
                result.createNodesResults.forEach((node, index) => {
                    console.log(`  ${index + 1}. ${node[0]} -> ${Object.keys(node[1]?.projects || {}).length} projects`);
                });
            }
        }

        return result.createNodesResults || [];
    },
];

/**
 * Create dependencies using Java analysis results
 */
export const createDependencies: CreateDependencies = async (options, context) => {
    const opts: MavenPluginOptions = {...DEFAULT_OPTIONS, ...(options as MavenPluginOptions)};
    
    // Check for verbose logging from multiple sources
    const isVerbose = opts.verbose || process.env.NX_VERBOSE_LOGGING === 'true' || process.argv.includes('--verbose');

    if (isVerbose) {
        console.log(`\n📊 [DEPENDENCIES-PLUGIN] ===============================`);
        console.log(`📊 [DEPENDENCIES-PLUGIN] Maven createDependencies starting...`);
        console.log(`📊 [DEPENDENCIES-PLUGIN] Workspace root: ${context.workspaceRoot}`);
        console.log(`📊 [DEPENDENCIES-PLUGIN] Plugin options:`, opts);
        console.log(`📊 [DEPENDENCIES-PLUGIN] ===============================`);
    }

    try {
        // Generate cache key based on pom.xml files and options
        if (isVerbose) {
            console.log(`📊 [DEPENDENCIES-PLUGIN] Generating cache key for dependency analysis...`);
        }
        
        const projectHash = await calculateHashForCreateNodes(
            workspaceRoot,
            (options as object) ?? {},
            context,
            ['{projectRoot}/pom.xml', '{workspaceRoot}/**/pom.xml']
        );
        const cacheKey = projectHash;

        if (isVerbose) {
            console.log(`📊 [DEPENDENCIES-PLUGIN] Generated cache key: ${cacheKey}`);
        }

        // OPTIMIZATION: Check global in-memory cache first
        if (globalAnalysisCache && globalCacheKey === cacheKey) {
            if (isVerbose) {
                console.log(`📊 [DEPENDENCIES-PLUGIN] ✅ HIT: Using global in-memory cache for createDependencies`);
                console.log(`📊 [DEPENDENCIES-PLUGIN] Cached dependencies count: ${globalAnalysisCache.createDependencies?.length || 0}`);
            }
            return globalAnalysisCache.createDependencies || [];
        }

        // Set up cache path
        const cachePath = join(workspaceDataDirectory, 'maven-analysis-cache.json');
        if (isVerbose) {
            console.log(`📊 [DEPENDENCIES-PLUGIN] Cache file path: ${cachePath}`);
        }
        const cache = readMavenCache(cachePath);

        // Check if we have valid cached results
        if (cache[cacheKey]) {
            if (isVerbose) {
                console.log(`📊 [DEPENDENCIES-PLUGIN] ✅ HIT: Using disk-cached Maven analysis results for createDependencies`);
                console.log(`📊 [DEPENDENCIES-PLUGIN] Cached dependencies count: ${cache[cacheKey].createDependencies?.length || 0}`);
            }
            // Store in global cache for faster subsequent access
            globalAnalysisCache = cache[cacheKey];
            globalCacheKey = cacheKey;
            return cache[cacheKey].createDependencies || [];
        }

        if (isVerbose) {
            console.log(`📊 [DEPENDENCIES-PLUGIN] ❌ MISS: No cache found - this should rarely happen since createNodesV2 runs first`);
            console.log(`📊 [DEPENDENCIES-PLUGIN] Running fresh Maven analysis for dependencies...`);
        }

        // Run analysis if not cached - this should rarely happen since createNodesV2 runs first
        const result = await runMavenAnalysis({...opts, verbose: isVerbose});

        // Cache the complete result
        cache[cacheKey] = result;
        writeMavenCache(cachePath, cache);

        // Store in global cache
        globalAnalysisCache = result;
        globalCacheKey = cacheKey;

        if (isVerbose) {
            console.log(`📊 [DEPENDENCIES-PLUGIN] Fresh analysis completed for dependencies`);
            console.log(`📊 [DEPENDENCIES-PLUGIN] Dependencies count: ${result.createDependencies?.length || 0}`);
            if (result.createDependencies?.length > 0) {
                console.log(`📊 [DEPENDENCIES-PLUGIN] Dependencies summary:`);
                result.createDependencies.forEach((dep, index) => {
                    console.log(`  ${index + 1}. ${dep.source} -> ${dep.target} (${dep.type})`);
                });
            }
        }

        return result.createDependencies || [];

    } catch (error) {
        if (isVerbose) {
            console.error(`📊 [DEPENDENCIES-PLUGIN] ❌ ERROR: Maven dependency analysis failed:`, error.message);
            console.error(`📊 [DEPENDENCIES-PLUGIN] Error stack:`, error.stack);
        } else {
            console.error(`Maven dependency analysis failed:`, error.message);
        }
        return [];
    }
};

/**
 * Run Maven analysis using Java plugin
 */
async function runMavenAnalysis(options: MavenPluginOptions): Promise<any> {
    const outputFile = join(workspaceDataDirectory, 'maven-analysis.json');

    // Check if verbose mode is enabled
    const isVerbose = options.verbose || process.env.NX_VERBOSE_LOGGING === 'true' || process.argv.includes('--verbose');

    if (isVerbose) {
        console.log(`\n🔧 [MAVEN-ANALYSIS] =========================================`);
        console.log(`🔧 [MAVEN-ANALYSIS] Starting Maven analysis process...`);
        console.log(`🔧 [MAVEN-ANALYSIS] Workspace root: ${workspaceRoot}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Workspace data directory: ${workspaceDataDirectory}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Output file: ${outputFile}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Options:`, options);
        console.log(`🔧 [MAVEN-ANALYSIS] Environment variables:`);
        console.log(`  - NX_MAVEN_COMPLEX_ANALYZER: ${process.env.NX_MAVEN_COMPLEX_ANALYZER || 'false'}`);
        console.log(`  - NX_VERBOSE_LOGGING: ${process.env.NX_VERBOSE_LOGGING || 'false'}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Command line args:`, process.argv.slice(2));
        console.log(`🔧 [MAVEN-ANALYSIS] =========================================`);
    }

    // Determine which analyzer to use based on environment variable
    const useComplexAnalyzer = process.env.NX_MAVEN_COMPLEX_ANALYZER === 'true';
    const analyzerType = useComplexAnalyzer ? 'complex' : 'simple';

    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] Analyzer selection: ${analyzerType} (${useComplexAnalyzer ? 'graph-analyzer' : 'simple-graph-analyzer'})`);
    }

    // Check if Java analyzer is available
    const availableAnalyzer = findJavaAnalyzer(useComplexAnalyzer);
    if (!availableAnalyzer) {
        const analyzerName = useComplexAnalyzer ? 'complex (graph-analyzer)' : 'simple (simple-graph-analyzer)';
        const compilationHint = `
To compile the Maven plugin, run:
  npm run compile-java:fresh

This will compile both simple and complex analyzers.

Alternatively, you can compile manually:
  cd maven-plugin && ../mvnw clean install -T6 -DskipTests -Ddevelocity.cache.local.enabled=false`;
        
        const errorMsg = `Maven ${analyzerName} analyzer not found. Please ensure maven-plugin is compiled.${compilationHint}`;
        
        if (isVerbose) {
            console.error(`🔧 [MAVEN-ANALYSIS] ❌ ERROR: ${errorMsg}`);
        }
        
        throw new Error(errorMsg);
    }

    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] ✅ Found analyzer at: ${availableAnalyzer}`);
    }

    // Detect Maven wrapper or fallback to 'mvn'
    const mavenExecutable = detectMavenWrapper();

    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] Maven executable detection:`);
        console.log(`  - Maven wrapper found: ${mavenExecutable.includes('mvnw') ? 'Yes' : 'No'}`);
        console.log(`  - Maven executable: ${mavenExecutable}`);
        console.log(`  - Platform: ${process.platform}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Analysis configuration:`);
        console.log(`  - Analyzer type: ${analyzerType}`);
        console.log(`  - Use complex analyzer: ${useComplexAnalyzer}`);
        console.log(`  - Verbose logging: ${isVerbose}`);
    }

    // Build Maven command arguments based on analyzer type
    const mavenArgs = useComplexAnalyzer ? [
        'io.quarkus:graph-analyzer:999-SNAPSHOT:analyze',
        `-Dnx.outputFile=${outputFile}`,
        `-Dnx.verbose=${isVerbose}`,
        '--batch-mode',
        '--no-transfer-progress'
    ] : [
        'io.quarkus:simple-graph-analyzer:999-SNAPSHOT:simple-analyze',
        `-Dnx.outputFile=${outputFile}`,
        `-Dnx.verbose=${isVerbose}`,
        '--batch-mode',
        '--no-transfer-progress'
    ];

    // Always use quiet mode to suppress expected reactor dependency warnings
    // These warnings are normal in large multi-module projects and don't affect functionality

    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] Maven command construction:`);
        console.log(`  - Goal: ${useComplexAnalyzer ? 'graph-analyzer:analyze' : 'simple-graph-analyzer:simple-analyze'}`);
        console.log(`  - Output file parameter: -Dnx.outputFile=${outputFile}`);
        console.log(`  - Verbose parameter: -Dnx.verbose=${isVerbose}`);
        console.log(`  - Additional flags: --batch-mode, --no-transfer-progress`);
        console.log(`🔧 [MAVEN-ANALYSIS] Full Maven command: ${mavenExecutable} ${mavenArgs.join(' ')}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Working directory: ${workspaceRoot}`);
        console.log(`🔧 [MAVEN-ANALYSIS] Starting Maven process execution...`);
    } else {
        mavenArgs.push('-q');
    }

    // Run Maven plugin
    await new Promise<void>((resolve, reject) => {
        if (isVerbose) {
            console.log(`🔧 [MAVEN-ANALYSIS] Spawning Maven process...`);
        }
        
        const child = spawn(mavenExecutable, mavenArgs, {
            cwd: workspaceRoot,
            stdio: isVerbose ? 'inherit' : 'pipe',
            detached: false
        });

        if (isVerbose) {
            console.log(`🔧 [MAVEN-ANALYSIS] Maven process spawned with PID: ${child.pid}`);
            console.log(`🔧 [MAVEN-ANALYSIS] Process configuration:`);
            console.log(`  - Working directory: ${workspaceRoot}`);
            console.log(`  - Stdio mode: ${isVerbose ? 'inherit (streaming)' : 'pipe (captured)'}`);
            console.log(`  - Detached: false`);
        }

        let stdout = '';
        let stderr = '';

        // Collect output if not in verbose mode
        if (!isVerbose) {
            child.stdout?.on('data', (data) => {
                stdout += data.toString();
            });
            child.stderr?.on('data', (data) => {
                stderr += data.toString();
            });
        } else {
            // In verbose mode, we can still monitor the streams even though they inherit
            child.stdout?.on('data', (data) => {
                // Maven output is already being displayed due to inherit, but we can track it
            });
            child.stderr?.on('data', (data) => {
                // Maven errors are already being displayed due to inherit
            });
        }

        // Set a reasonable timeout for the Maven process
        const timeoutMs = 300000; // 5 minutes
        const timeout = setTimeout(() => {
            if (isVerbose) {
                console.log(`🔧 [MAVEN-ANALYSIS] ⏰ TIMEOUT: Maven process timed out after 5 minutes - terminating...`);
            }
            child.kill('SIGTERM');
            reject(new Error(`Maven analysis timed out after 5 minutes`));
        }, timeoutMs);

        if (isVerbose) {
            console.log(`🔧 [MAVEN-ANALYSIS] Process timeout set to ${timeoutMs}ms (5 minutes)`);
        }

        const cleanup = () => {
            if (!child.killed) {
                if (isVerbose) {
                    console.log(`🔧 [MAVEN-ANALYSIS] Cleaning up Maven process (PID: ${child.pid})...`);
                }
                child.kill('SIGTERM');
                setTimeout(() => {
                    if (!child.killed) {
                        if (isVerbose) {
                            console.log(`🔧 [MAVEN-ANALYSIS] Force killing Maven process (PID: ${child.pid})...`);
                        }
                        child.kill('SIGKILL');
                    }
                }, 5000);
            }
        };

        child.on('close', (code) => {
            clearTimeout(timeout);
            
            // Remove our specific cleanup listeners
            process.removeListener('exit', cleanup);
            process.removeListener('SIGINT', cleanup);
            process.removeListener('SIGTERM', cleanup);
            
            if (isVerbose) {
                console.log(`🔧 [MAVEN-ANALYSIS] Maven process completed with exit code: ${code}`);
                console.log(`🔧 [MAVEN-ANALYSIS] Process cleanup completed`);
            }
            
            if (code === 0) {
                if (isVerbose) {
                    console.log(`🔧 [MAVEN-ANALYSIS] ✅ Maven analysis completed successfully`);
                }
                resolve();
            } else {
                const analyzerName = useComplexAnalyzer ? 'complex (graph-analyzer)' : 'simple (simple-graph-analyzer)';
                let errorMsg = `Maven ${analyzerName} process exited with code ${code}`;
                
                if (stderr) {
                    errorMsg += `\nStderr: ${stderr}`;
                }
                if (stdout && !isVerbose) {
                    errorMsg += `\nStdout: ${stdout}`;
                }
                
                errorMsg += `\nMaven command: ${mavenExecutable} ${mavenArgs.join(' ')}`;
                errorMsg += `\nWorking directory: ${workspaceRoot}`;
                
                if (isVerbose) {
                    console.error(`🔧 [MAVEN-ANALYSIS] ❌ Maven analysis failed:`);
                    console.error(`🔧 [MAVEN-ANALYSIS] Error message: ${errorMsg}`);
                } else {
                    console.error('Maven analysis failed:');
                    console.error(errorMsg);
                }
                reject(new Error(errorMsg));
            }
        });

        child.on('error', (error) => {
            clearTimeout(timeout);
            
            // Remove our specific cleanup listeners
            process.removeListener('exit', cleanup);
            process.removeListener('SIGINT', cleanup);
            process.removeListener('SIGTERM', cleanup);
            
            const errorMsg = `Failed to spawn Maven process: ${error.message}`;
            if (isVerbose) {
                console.error(`🔧 [MAVEN-ANALYSIS] ❌ Process spawn error: ${errorMsg}`);
            }
            
            reject(new Error(errorMsg));
        });

        // Register cleanup handlers
        process.on('exit', cleanup);
        process.on('SIGINT', cleanup);
        process.on('SIGTERM', cleanup);
        
        if (isVerbose) {
            console.log(`🔧 [MAVEN-ANALYSIS] Process cleanup handlers registered`);
        }
    });

    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] Maven process execution completed - reading results...`);
        console.log(`🔧 [MAVEN-ANALYSIS] Looking for output file: ${outputFile}`);
    }

    // Read and return JSON result
    if (!existsSync(outputFile)) {
        const errorMsg = `Output file not found: ${outputFile}`;
        if (isVerbose) {
            console.error(`🔧 [MAVEN-ANALYSIS] ❌ ${errorMsg}`);
        }
        throw new Error(errorMsg);
    }

    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] ✅ Output file found - reading and parsing JSON...`);
    }

    const jsonContent = readFileSync(outputFile, 'utf8');
    const result = JSON.parse(jsonContent);
    
    if (isVerbose) {
        console.log(`🔧 [MAVEN-ANALYSIS] ✅ JSON parsed successfully`);
        console.log(`🔧 [MAVEN-ANALYSIS] Result summary:`);
        console.log(`  - Total keys in result: ${Object.keys(result).length}`);
        console.log(`  - Result keys: [${Object.keys(result).join(', ')}]`);
        if (result.createNodesResults) {
            console.log(`  - CreateNodes results: ${result.createNodesResults.length} entries`);
        }
        if (result.createDependencies) {
            console.log(`  - CreateDependencies results: ${result.createDependencies.length} entries`);
        }
        console.log(`🔧 [MAVEN-ANALYSIS] Analysis process completed successfully`);
    }
    
    return result;
}

/**
 * Detect Maven wrapper in workspace root, fallback to 'mvn'
 */
function detectMavenWrapper(): string {
    const isWindows = process.platform === 'win32';
    const wrapperFile = isWindows ? 'mvnw.cmd' : 'mvnw';
    const wrapperPath = join(workspaceRoot, wrapperFile);

    if (existsSync(wrapperPath)) {
        return wrapperPath;
    }

    // Fallback to 'mvn' if no wrapper found
    return 'mvn';
}

/**
 * Find the compiled Java Maven analyzer
 * @param useComplex - If true, prioritize complex analyzer; if false, prioritize simple analyzer
 */
function findJavaAnalyzer(useComplex: boolean = false): string | null {
    const mavenPluginPath = join(workspaceRoot, 'maven-plugin');
    
    // Check for verbose logging from multiple sources
    const isVerbose = process.env.NX_VERBOSE_LOGGING === 'true' || process.argv.includes('--verbose');
    
    if (isVerbose) {
        console.log(`\n🔍 [ANALYZER-FINDER] ===============================`);
        console.log(`🔍 [ANALYZER-FINDER] Searching for Java analyzer...`);
        console.log(`🔍 [ANALYZER-FINDER] Maven plugin path: ${mavenPluginPath}`);
        console.log(`🔍 [ANALYZER-FINDER] Preferred analyzer: ${useComplex ? 'complex' : 'simple'}`);
    }
    
    // Define analyzer paths based on preference
    const complexPaths = [
        join(mavenPluginPath, 'graph-analyzer/target/classes'),
        join(mavenPluginPath, 'graph-analyzer/target/graph-analyzer-999-SNAPSHOT.jar'),
    ];
    
    const simplePaths = [
        join(mavenPluginPath, 'simple-graph-analyzer/target/classes'),
        join(mavenPluginPath, 'simple-graph-analyzer/target/simple-graph-analyzer-999-SNAPSHOT.jar'),
    ];

    // Check paths in order of preference
    const orderedPaths = useComplex ? [...complexPaths, ...simplePaths] : [...simplePaths, ...complexPaths];

    if (isVerbose) {
        console.log(`🔍 [ANALYZER-FINDER] Checking analyzer paths in preference order:`);
        console.log(`🔍 [ANALYZER-FINDER] Complex analyzer paths:`);
        complexPaths.forEach((path, index) => {
            const exists = existsSync(path);
            const pathType = path.includes('.jar') ? 'JAR' : 'classes';
            console.log(`  ${exists ? '✅' : '❌'} [${pathType}] ${path}`);
        });
        console.log(`🔍 [ANALYZER-FINDER] Simple analyzer paths:`);
        simplePaths.forEach((path, index) => {
            const exists = existsSync(path);
            const pathType = path.includes('.jar') ? 'JAR' : 'classes';
            console.log(`  ${exists ? '✅' : '❌'} [${pathType}] ${path}`);
        });
        console.log(`🔍 [ANALYZER-FINDER] Ordered paths to check (${useComplex ? 'complex first' : 'simple first'}):`);
        orderedPaths.forEach((path, index) => {
            console.log(`  ${index + 1}. ${existsSync(path) ? '✅' : '❌'} ${path}`);
        });
    }

    for (const path of orderedPaths) {
        if (existsSync(path)) {
            if (isVerbose) {
                const pathType = path.includes('.jar') ? 'JAR' : 'classes directory';
                const analyzerType = path.includes('graph-analyzer') ? 'complex' : 'simple';
                console.log(`🔍 [ANALYZER-FINDER] ✅ Found ${analyzerType} analyzer (${pathType}): ${path}`);
                console.log(`🔍 [ANALYZER-FINDER] ===============================`);
            }
            return path;
        }
    }

    if (isVerbose) {
        console.log(`🔍 [ANALYZER-FINDER] ❌ No analyzer found in any of the checked paths`);
        console.log(`🔍 [ANALYZER-FINDER] This likely means the Maven plugin hasn't been compiled yet`);
        console.log(`🔍 [ANALYZER-FINDER] ===============================`);
    }

    return null;
}

/**
 * Post-task execution hook to clean up Maven session files
 */
export async function postTasksExecution(options: any, context: any) {
    const sessionDir = join(context.workspaceRoot, '.nx-maven-sessions');

    if (existsSync(sessionDir)) {
        try {
            rmSync(sessionDir, {recursive: true, force: true});
            if (options?.verbose) {
                console.log('Maven session files cleaned up successfully');
            }
        } catch (error) {
            console.warn('Failed to clean up Maven session files:', error.message);
        }
    }
}

/**
 * Plugin configuration
 */
export default {
    name: 'maven-plugin',
    createNodesV2,
    createDependencies,
    postTasksExecution,
};
