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

        if (opts.verbose) {
            console.log(`Maven plugin found ${configFiles.length} pom.xml files`);
        }

        // Filter out unwanted pom.xml files
        const filteredFiles = configFiles.filter(file =>
            !file.includes('maven-script/') &&
            !file.includes('target/') &&
            !file.includes('node_modules/')
        );

        if (filteredFiles.length === 0) {
            return [];
        }

        // Generate cache key based on pom.xml files and options
        const projectHash = await calculateHashForCreateNodes(
            workspaceRoot,
            (options as object) ?? {},
            context,
            ['{projectRoot}/pom.xml', '{workspaceRoot}/**/pom.xml']
        );
        const cacheKey = projectHash;

        // OPTIMIZATION: Check global in-memory cache first
        if (globalAnalysisCache && globalCacheKey === cacheKey) {
            if (opts.verbose) {
                console.log('Using global in-memory cache for createNodes');
            }
            return globalAnalysisCache.createNodesResults || [];
        }

        // Set up cache path
        const cachePath = join(workspaceDataDirectory, 'maven-analysis-cache.json');
        const cache = readMavenCache(cachePath);

        // Check if we have valid cached results
        if (cache[cacheKey]) {
            if (opts.verbose) {
                console.log('Using cached Maven analysis results for createNodes');
            }
            // Store in global cache for faster subsequent access
            globalAnalysisCache = cache[cacheKey];
            globalCacheKey = cacheKey;
            return cache[cacheKey].createNodesResults || [];
        }

        // Run analysis if not cached
        const result = await runMavenAnalysis(opts);

        // Cache the complete result
        cache[cacheKey] = result;
        writeMavenCache(cachePath, cache);

        // Store in global cache
        globalAnalysisCache = result;
        globalCacheKey = cacheKey;

        return result.createNodesResults || [];
    },
];

/**
 * Create dependencies using Java analysis results
 */
export const createDependencies: CreateDependencies = async (options, context) => {
    const opts: MavenPluginOptions = {...DEFAULT_OPTIONS, ...(options as MavenPluginOptions)};

    try {
        // Generate cache key based on pom.xml files and options
        const projectHash = await calculateHashForCreateNodes(
            workspaceRoot,
            (options as object) ?? {},
            context,
            ['{projectRoot}/pom.xml', '{workspaceRoot}/**/pom.xml']
        );
        const cacheKey = projectHash;

        // OPTIMIZATION: Check global in-memory cache first
        if (globalAnalysisCache && globalCacheKey === cacheKey) {
            if (opts.verbose) {
                console.log('Using global in-memory cache for createDependencies');
            }
            return globalAnalysisCache.createDependencies || [];
        }

        // Set up cache path
        const cachePath = join(workspaceDataDirectory, 'maven-analysis-cache.json');
        const cache = readMavenCache(cachePath);

        // Check if we have valid cached results
        if (cache[cacheKey]) {
            if (opts.verbose) {
                console.log('Using cached Maven analysis results for createDependencies');
            }
            // Store in global cache for faster subsequent access
            globalAnalysisCache = cache[cacheKey];
            globalCacheKey = cacheKey;
            return cache[cacheKey].createDependencies || [];
        }

        // Run analysis if not cached - this should rarely happen since createNodesV2 runs first
        const result = await runMavenAnalysis(opts);

        // Cache the complete result
        cache[cacheKey] = result;
        writeMavenCache(cachePath, cache);

        // Store in global cache
        globalAnalysisCache = result;
        globalCacheKey = cacheKey;

        return result.createDependencies || [];

    } catch (error) {
        console.error(`Maven dependency analysis failed:`, error.message);
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

    // Determine which analyzer to use based on environment variable
    const useComplexAnalyzer = process.env.NX_MAVEN_COMPLEX_ANALYZER === 'true';
    const analyzerType = useComplexAnalyzer ? 'complex' : 'simple';

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
        
        throw new Error(`Maven ${analyzerName} analyzer not found. Please ensure maven-plugin is compiled.${compilationHint}`);
    }

    // Detect Maven wrapper or fallback to 'mvn'
    const mavenExecutable = detectMavenWrapper();

    if (isVerbose) {
        console.log(`Running Maven analysis with verbose logging enabled...`);
        console.log(`Analyzer type: ${analyzerType} (NX_MAVEN_COMPLEX_ANALYZER=${process.env.NX_MAVEN_COMPLEX_ANALYZER || 'false'})`);
        console.log(`Maven executable: ${mavenExecutable}`);
        console.log(`Output file: ${outputFile}`);
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
        console.log(`Executing Maven command: ${mavenExecutable} ${mavenArgs.join(' ')}`);
        console.log(`Working directory: ${workspaceRoot}`);
    } else {
        mavenArgs.push('-q');
    }

    // Run Maven plugin
    await new Promise<void>((resolve, reject) => {
        const child = spawn(mavenExecutable, mavenArgs, {
            cwd: workspaceRoot,
            stdio: isVerbose ? 'inherit' : 'pipe',
            detached: false
        });

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
        }

        // Set a reasonable timeout for the Maven process
        const timeout = setTimeout(() => {
            child.kill('SIGTERM');
            reject(new Error(`Maven analysis timed out after 5 minutes`));
        }, 300000); // 5 minutes

        const cleanup = () => {
            if (!child.killed) {
                child.kill('SIGTERM');
                setTimeout(() => {
                    if (!child.killed) {
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
                console.log(`Maven process completed with exit code: ${code}`);
            }
            
            if (code === 0) {
                if (isVerbose) {
                    console.log(`Maven analysis completed successfully`);
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
                
                console.error('Maven analysis failed:');
                console.error(errorMsg);
                reject(new Error(errorMsg));
            }
        });

        child.on('error', (error) => {
            clearTimeout(timeout);
            
            // Remove our specific cleanup listeners
            process.removeListener('exit', cleanup);
            process.removeListener('SIGINT', cleanup);
            process.removeListener('SIGTERM', cleanup);
            
            reject(new Error(`Failed to spawn Maven process: ${error.message}`));
        });

        // Register cleanup handlers
        process.on('exit', cleanup);
        process.on('SIGINT', cleanup);
        process.on('SIGTERM', cleanup);
    });

    // Read and return JSON result
    if (!existsSync(outputFile)) {
        throw new Error(`Output file not found: ${outputFile}`);
    }

    const jsonContent = readFileSync(outputFile, 'utf8');
    return JSON.parse(jsonContent);
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

    // Debug: Log which paths we're checking
    if (process.env.NX_VERBOSE_LOGGING === 'true' || process.argv.includes('--verbose')) {
        console.log(`Looking for ${useComplex ? 'complex' : 'simple'} analyzer in paths:`);
        orderedPaths.forEach(path => {
            console.log(`  ${existsSync(path) ? '✅' : '❌'} ${path}`);
        });
    }

    for (const path of orderedPaths) {
        if (existsSync(path)) {
            return path;
        }
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
