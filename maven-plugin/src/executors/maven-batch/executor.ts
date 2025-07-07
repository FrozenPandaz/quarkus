import { ExecutorContext, logger, TaskGraph } from '@nx/devkit';
import { execSync } from 'child_process';
import { join } from 'path';
import { existsSync, writeFileSync } from 'fs';
import { createPseudoTerminal } from 'nx/src/tasks-runner/pseudo-terminal';

export interface MavenBatchExecutorOptions {
  goals: string[];
  projectRoot?: string;
  verbose?: boolean;
  mavenPluginPath?: string;
  outputFile?: string;
  failOnError?: boolean;
}

export interface MavenGoalResult {
  goal: string;
  success: boolean;
  durationMs: number;
  exitCode: number;
  output: string[];
  errors: string[];
}

export interface MavenBatchResult {
  overallSuccess: boolean;
  totalDurationMs: number;
  errorMessage?: string;
  goalResults: MavenGoalResult[];
}

export interface ExecutorResult {
  success: boolean;
  terminalOutput: string;
  output?: MavenBatchResult;
  error?: string;
}

// Regular executor for single task execution
export default async function runExecutor(
  options: MavenBatchExecutorOptions,
  context: ExecutorContext
): Promise<ExecutorResult> {
  const {
    goals,
    projectRoot = '.',
    verbose = true,
    mavenPluginPath = 'maven-plugin',
    outputFile,
    failOnError = true
  } = options;

  if (!goals || goals.length === 0) {
    const error = 'At least one Maven goal must be specified';
    logger.error(error);
    return { success: false, terminalOutput: error, error };
  }

  // Resolve paths
  const workspaceRoot = context.root;
  const pluginDir = join(workspaceRoot, mavenPluginPath);
  const projectDir = join(workspaceRoot, projectRoot);

  // Validate plugin directory
  if (!existsSync(pluginDir)) {
    const error = `Maven plugin directory not found: ${pluginDir}`;
    logger.error(error);
    return { success: false, terminalOutput: error, error };
  }

  // Validate project directory
  if (!existsSync(projectDir)) {
    const error = `Project directory not found: ${projectDir}`;
    logger.error(error);
    return { success: false, terminalOutput: error, error };
  }

  // Check for batch executor
  const batchExecutorClasspath = join(pluginDir, 'target/classes');
  const dependencyPath = join(pluginDir, 'target/dependency');

  if (!existsSync(batchExecutorClasspath)) {
    const error = `Maven plugin not compiled. Run 'mvn compile' in ${pluginDir}`;
    logger.error(error);
    return { success: false, terminalOutput: error, error };
  }

  if (!existsSync(dependencyPath)) {
    const error = `Maven dependencies not copied. Run 'mvn dependency:copy-dependencies' in ${pluginDir}`;
    logger.error(error);
    return { success: false, terminalOutput: error, error };
  }

  try {
    const goalsString = goals.join(',');
    const verboseFlag = verbose ? 'true' : 'false';

    // Build command with new signature: goals, workspaceRoot, projects, verbose
    const classpath = `${batchExecutorClasspath}:${dependencyPath}/*`;
    const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectRoot}" ${verboseFlag}`;

    // Always log the Java command being executed
    logger.info(`Maven Batch Java Command:`);
    logger.info(`  Goals: ${goals.join(', ')}`);
    logger.info(`  Project: ${projectRoot}`);
    logger.info(`  Working directory: ${pluginDir}`);
    logger.info(`  Java executable: java`);
    logger.info(`  System property: -Dmaven.multiModuleProjectDirectory="${workspaceRoot}"`);
    logger.info(`  Classpath: ${classpath}`);
    logger.info(`  Main class: NxMavenEmbedderBatchExecutor`);
    logger.info(`  Arguments: "${goalsString}" "${workspaceRoot}" "${projectRoot}" ${verboseFlag}`);
    logger.info(`  Full command: ${command}`);

    if (verbose) {
      logger.info(`Additional verbose logging enabled for Maven batch execution`);
    }

    // Execute the batch command with streaming
    const startTime = Date.now();
    const output = await executeWithStreaming(command, pluginDir, verbose);
    const duration = Date.now() - startTime;

    // Parse JSON output from batch executor
    // The output may contain Maven warnings before the JSON, so find the JSON part
    let result: MavenBatchResult;
    try {
      // Find the JSON output (starts with '{' and ends with '}')
      const lines = output.trim().split('\n');
      let jsonStart = -1;
      let jsonEnd = -1;

      // Find the start of JSON
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].trim().startsWith('{')) {
          jsonStart = i;
          break;
        }
      }

      // Find the end of JSON (last '}')
      for (let i = lines.length - 1; i >= 0; i--) {
        if (lines[i].trim().endsWith('}')) {
          jsonEnd = i;
          break;
        }
      }

      if (jsonStart === -1 || jsonEnd === -1) {
        throw new Error('No JSON output found');
      }

      const jsonOutput = lines.slice(jsonStart, jsonEnd + 1).join('\n');
      result = JSON.parse(jsonOutput);
    } catch (parseError: any) {
      const error = `Failed to parse batch executor output: ${parseError?.message || parseError}`;
      logger.error(error);
      logger.debug(`Raw output: ${output}`);
      return { success: false, terminalOutput: error, error };
    }

    // Log results
    if (verbose || !result.overallSuccess) {
      logger.info(`Maven batch execution completed in ${duration}ms`);
      logger.info(`Overall success: ${result.overallSuccess}`);

      if (result.errorMessage) {
        logger.error(`Error: ${result.errorMessage}`);
      }

      result.goalResults.forEach((goalResult, index) => {
        const status = goalResult.success ? '✅' : '❌';
        logger.info(`${status} Goal ${index + 1}: ${goalResult.goal} (${goalResult.durationMs}ms)`);

        if (!goalResult.success && goalResult.errors.length > 0) {
          goalResult.errors.forEach(error => logger.error(`  Error: ${error}`));
        }

        if (verbose && goalResult.output.length > 0) {
          logger.debug(`  Output: ${goalResult.output.slice(-5).join('\n  ')}`); // Last 5 lines
        }
      });
    }

    // Write output file if specified
    if (outputFile) {
      const outputPath = join(workspaceRoot, outputFile);
      writeFileSync(outputPath, JSON.stringify(result, null, 2));
      if (verbose) {
        logger.info(`Results written to: ${outputPath}`);
      }
    }

    // Determine success
    const success = result.overallSuccess || !failOnError;

    if (!success && failOnError) {
      logger.error(`Maven batch execution failed`);
      if (result.errorMessage) {
        logger.error(result.errorMessage);
      }
    }

    return {
      success,
      terminalOutput: result.goalResults.map(r => r.output.join('\n')).join('\n'),
      output: result,
      error: result.errorMessage
    };

  } catch (error: any) {
    const errorMessage = error?.message || String(error);
    logger.error(`Maven batch executor failed: ${errorMessage}`);

    if (verbose) {
      logger.debug(`Error details:`, error);
    }

    return {
      success: false,
      terminalOutput: errorMessage,
      error: errorMessage
    };
  }
}

// Simplified Nx batch executor - collect all goals and projects from task graph and execute in one batch
export async function batchMavenExecutor(
  taskGraph: TaskGraph,
  inputs: Record<string, MavenBatchExecutorOptions>
): Promise<Record<string, { success: boolean; terminalOutput: string }>> {
  const results: Record<string, { success: boolean; terminalOutput: string }> = {};

  try {
    // Collect ALL goals and projects from ALL tasks in the task graph
    const allGoals: string[] = [];
    const allProjects: string[] = [];
    const taskIds: string[] = [];
    const taskGoalMapping = new Map<string, string[]>();
    let verbose = true;
    let commonOptions: MavenBatchExecutorOptions | undefined;

    // Extract goals and project roots from each task in the task graph
    for (const [taskId, options] of Object.entries(inputs)) {
      const task = taskGraph.tasks[taskId];

      if (task) {
        // Get goals from the task's configuration options
        const taskGoals = options.goals || [];
        taskGoalMapping.set(taskId, taskGoals);
        allGoals.push(...taskGoals);

        // Get project root (use from options or task target project)
        const projectRoot = options.projectRoot || task.target.project || '.';
        allProjects.push(projectRoot);

        taskIds.push(taskId);

        // Use first task's options as base, enable verbose if any task requests it
        if (!commonOptions) commonOptions = options;
        if (options.verbose) verbose = true;
      }
    }

    // Remove duplicate goals and projects
    const uniqueGoals = Array.from(new Set(allGoals));
    const uniqueProjects = Array.from(new Set(allProjects));

    // If no tasks or goals, return empty results
    if (taskIds.length === 0 || uniqueGoals.length === 0) {
      return results;
    }

    // Execute ALL unique goals across ALL unique projects in a single batch
    const batchOptions = { ...commonOptions!, verbose };
    const batchResult = await executeMultiProjectMavenBatch(uniqueGoals, uniqueProjects, batchOptions, process.cwd());

    // Generate per-task results based on task-specific goal success
    for (const taskId of taskIds) {
      const taskSuccess = isTaskSuccessful(taskId, taskGoalMapping, batchResult);
      const taskOutput = getTaskSpecificOutput(taskId, taskGoalMapping, batchResult);
      
      results[taskId] = {
        success: taskSuccess,
        terminalOutput: taskOutput
      };
    }

    return results;

  } catch (error: any) {
    // If batch fails, mark ALL tasks as failed
    for (const taskId of Object.keys(inputs)) {
      results[taskId] = {
        success: false,
        terminalOutput: error?.message || String(error)
      };
    }
  }

  return results;
}

// Execute Maven goals across multiple projects in a single batch
async function executeMultiProjectMavenBatch(
  goals: string[],
  projects: string[],
  options: MavenBatchExecutorOptions,
  workspaceRoot: string
): Promise<MavenBatchResult> {
  const {
    verbose = true,
    mavenPluginPath = 'maven-plugin'
  } = options;

  // Resolve paths
  const pluginDir = join(workspaceRoot, mavenPluginPath);

  // Validate plugin directory
  if (!existsSync(pluginDir)) {
    throw new Error(`Maven plugin directory not found: ${pluginDir}`);
  }

  // Check for batch executor
  const batchExecutorClasspath = join(pluginDir, 'target/classes');
  const dependencyPath = join(pluginDir, 'target/dependency');

  if (!existsSync(batchExecutorClasspath)) {
    throw new Error(`Maven plugin not compiled. Run 'mvn compile' in ${pluginDir}`);
  }

  if (!existsSync(dependencyPath)) {
    throw new Error(`Maven dependencies not copied. Run 'mvn dependency:copy-dependencies' in ${pluginDir}`);
  }

  const goalsString = goals.join(',');
  const projectsString = projects.join(',');
  const verboseFlag = verbose ? 'true' : 'false';

  // Build command with new signature: goals, workspaceRoot, projects, verbose
  const classpath = `${batchExecutorClasspath}:${dependencyPath}/*`;
  const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`;

  // Always log the Java command being executed for multi-project batch
  logger.info(`Maven Multi-Project Batch Java Command:`);
  logger.info(`  Goals: ${goals.join(', ')}`);
  logger.info(`  Projects: ${projects.join(', ')}`);
  logger.info(`  Working directory: ${pluginDir}`);
  logger.info(`  Java executable: java`);
  logger.info(`  System property: -Dmaven.multiModuleProjectDirectory="${workspaceRoot}"`);
  logger.info(`  Classpath: ${classpath}`);
  logger.info(`  Main class: NxMavenEmbedderBatchExecutor`);
  logger.info(`  Arguments: "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`);
  logger.info(`  Full command: ${command}`);

  // Execute the batch command with streaming
  const startTime = Date.now();
  const output = await executeWithStreaming(command, pluginDir, verbose);

  // Parse JSON output from batch executor
  // The output may contain Maven warnings before the JSON, so find the JSON part
  const lines = output.trim().split('\n');
  let jsonStart = -1;
  let jsonEnd = -1;

  // Find the start of JSON
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].trim().startsWith('{')) {
      jsonStart = i;
      break;
    }
  }

  // Find the end of JSON (last '}')
  for (let i = lines.length - 1; i >= 0; i--) {
    if (lines[i].trim().endsWith('}')) {
      jsonEnd = i;
      break;
    }
  }

  if (jsonStart === -1 || jsonEnd === -1) {
    throw new Error('No JSON output found');
  }

  const jsonOutput = lines.slice(jsonStart, jsonEnd + 1).join('\n');
  const result: MavenBatchResult = JSON.parse(jsonOutput);

  if (verbose) {
    logger.info(`Multi-project Maven batch execution completed`);
    logger.info(`Overall success: ${result.overallSuccess}`);

    if (result.errorMessage) {
      logger.error(`Error: ${result.errorMessage}`);
    }

    result.goalResults.forEach((goalResult, index) => {
      const status = goalResult.success ? '✅' : '❌';
      logger.info(`${status} Goal ${index + 1}: ${goalResult.goal} (${goalResult.durationMs}ms)`);
    });
  }

  return result;
}

// Check if a task's goals were successful based on batch results
function isTaskSuccessful(taskId: string, taskGoalMapping: Map<string, string[]>, batchResult: MavenBatchResult): boolean {
  const taskGoals = taskGoalMapping.get(taskId) || [];
  
  // A task is successful if ALL its goals succeeded
  return taskGoals.every(requestedGoal => {
    // Find if any executed goal matches this requested goal
    return batchResult.goalResults.some(result => 
      result.success && (
        result.goal === requestedGoal ||                    // Exact match
        result.goal.includes(requestedGoal) ||              // Goal contains requested
        result.goal.endsWith(`:${requestedGoal}`)           // Plugin:goal format
      )
    );
  });
}

// Get task-specific output from batch results
function getTaskSpecificOutput(taskId: string, taskGoalMapping: Map<string, string[]>, batchResult: MavenBatchResult): string {
  const taskGoals = taskGoalMapping.get(taskId) || [];
  const relevantResults: string[] = [];
  
  // Find output from goals that match this task
  for (const requestedGoal of taskGoals) {
    const matchingResults = batchResult.goalResults.filter(result => 
      result.goal === requestedGoal ||
      result.goal.includes(requestedGoal) ||
      result.goal.endsWith(`:${requestedGoal}`)
    );
    
    matchingResults.forEach(result => {
      relevantResults.push(...result.output);
      if (!result.success && result.errors.length > 0) {
        relevantResults.push(...result.errors);
      }
    });
  }
  
  // If no specific output found, return all batch output
  if (relevantResults.length === 0) {
    return batchResult.goalResults.map(r => r.output.join('\n')).join('\n');
  }
  
  return relevantResults.join('\n');
}

// Execute Maven command with streaming output using PseudoTerminal
async function executeWithStreaming(command: string, cwd: string, verbose: boolean): Promise<string> {
  // Create PseudoTerminal with skipSupportCheck=true to bypass TTY requirement
  const pseudoTerminal = createPseudoTerminal(true);
  let terminalOutput = '';

  // Always log command execution start
  logger.info(`Starting Java command execution via PseudoTerminal`);
  logger.info(`  Working directory: ${cwd}`);
  logger.info(`  Command: ${command}`);
  
  if (verbose) {
    logger.info(`Verbose mode enabled - additional execution details will be logged`);
  }

  try {
    // Initialize the pseudo terminal
    await pseudoTerminal.init();

    // Run the Maven command
    const process = pseudoTerminal.runCommand(command, {
      cwd,
      quiet: false, // Always stream output
      tty: false
    });

    // Collect output for JSON parsing
    process.onOutput((output: string) => {
      terminalOutput += output;
      // Output is automatically streamed to console by PseudoTerminal
    });

    // Wait for process completion using async/await
    const { code } = await process.getResults();

    // Shutdown the terminal
    pseudoTerminal.shutdown(code);

    if (code === 0) {
      return terminalOutput;
    } else {
      throw new Error(`Maven command failed with exit code ${code}. Terminal output:\n${terminalOutput}`);
    }

  } catch (error: any) {
    pseudoTerminal.shutdown(1);
    throw new Error(`Failed to execute Maven command: ${error.message}\nTerminal output:\n${terminalOutput}`);
  }
}
