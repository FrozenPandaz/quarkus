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

export interface TaskExecutionResult {
  taskId: string;
  success: boolean;
  duration: number;
  goalResults: GoalExecutionResult[];
  artifacts?: ArtifactResult[];
  dependencies?: DependencyResult[];
  errorMessage?: string;
  executionContext?: Record<string, any>;
}

export interface GoalExecutionResult {
  goal: string;
  success: boolean;
  duration: number;
  output: string[];
  errors: string[];
  exitCode: number;
  pluginInfo?: PluginInfo;
  executionId?: string;
}

export interface PluginInfo {
  groupId: string;
  artifactId: string;
  version: string;
  goalName: string;
  executionId?: string;
}

export interface ArtifactResult {
  groupId: string;
  artifactId: string;
  version: string;
  type: string;
  classifier?: string;
  scope?: string;
  file?: string;
  resolved: boolean;
}

export interface DependencyResult {
  groupId: string;
  artifactId: string;
  version: string;
  type: string;
  classifier?: string;
  scope: string;
  optional: boolean;
  file?: string;
}

export interface ExecutorResult {
  success: boolean;
  terminalOutput: string;
  output?: TaskExecutionResult | Record<string, TaskExecutionResult>;
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
    verbose = false,
    mavenPluginPath = 'maven-plugin',
    outputFile,
    failOnError = true
  } = options;

  if (!goals || goals.length === 0) {
    const error = 'At least one Maven goal must be specified';
    logger.error(error);
    return { success: false, terminalOutput: error, error };
  }

  // Check environment variable to decide which executor to use
  // Default to embedder unless explicitly disabled
  const useEmbedder = process.env.NX_MAVEN_USE_EMBEDDER !== 'false';
  
  if (verbose) {
    logger.info(`Using ${useEmbedder ? 'Embedder' : 'Invoker'} implementation (NX_MAVEN_USE_EMBEDDER=${process.env.NX_MAVEN_USE_EMBEDDER || 'default'})`);
  }

  if (useEmbedder) {
    return await runEmbedderExecutor(options, context);
  } else {
    return await runInvokerExecutor(options, context);
  }
}

// Embedder-based executor implementation
async function runEmbedderExecutor(
  options: MavenBatchExecutorOptions,
  context: ExecutorContext
): Promise<ExecutorResult> {
  const {
    goals,
    projectRoot = '.',
    verbose = false,
    mavenPluginPath = 'maven-plugin',
    outputFile,
    failOnError = true
  } = options;

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

  // Check for embedder executor
  const embedderExecutorClasspath = join(pluginDir, 'target/classes');
  const dependencyPath = join(pluginDir, 'target/dependency');

  if (!existsSync(embedderExecutorClasspath)) {
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

    // Build command for embedder: goals, workspaceRoot, projects, verbose
    const classpath = `${embedderExecutorClasspath}:${dependencyPath}/*`;
    const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectRoot}" ${verboseFlag}`;

    if (verbose) {
      logger.info(`Executing Maven Embedder batch command:`);
      logger.info(`  Goals: ${goals.join(', ')}`);
      logger.info(`  Project: ${projectRoot}`);
      logger.info(`  Working directory: ${pluginDir}`);
      logger.info(`  Command: ${command}`);
    }

    // Execute the embedder command with streaming
    const startTime = Date.now();
    const output = await executeWithStreaming(command, pluginDir, verbose);
    const duration = Date.now() - startTime;

    // Parse JSON output from embedder executor
    let result: Record<string, TaskExecutionResult>;
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
      const error = `Failed to parse embedder executor output: ${parseError?.message || parseError}`;
      logger.error(error);
      logger.debug(`Raw output: ${output}`);
      return { success: false, terminalOutput: error, error };
    }

    // Extract the first (and likely only) task result for single task execution
    const taskResults = Object.values(result);
    const mainResult = taskResults.length > 0 ? taskResults[0] : null;

    if (!mainResult) {
      const error = 'No task results found in embedder output';
      logger.error(error);
      return { success: false, terminalOutput: error, error };
    }

    // Log results
    if (verbose || !mainResult.success) {
      logger.info(`Maven Embedder execution completed in ${duration}ms`);
      logger.info(`Task success: ${mainResult.success}`);

      if (mainResult.errorMessage) {
        logger.error(`Error: ${mainResult.errorMessage}`);
      }

      mainResult.goalResults.forEach((goalResult, index) => {
        const status = goalResult.success ? '✅' : '❌';
        logger.info(`${status} Goal ${index + 1}: ${goalResult.goal} (${goalResult.duration}ms)`);

        if (!goalResult.success && goalResult.errors.length > 0) {
          goalResult.errors.forEach(error => logger.error(`  Error: ${error}`));
        }

        if (verbose && goalResult.output.length > 0) {
          logger.debug(`  Output: ${goalResult.output.slice(-5).join('\n  ')}`); // Last 5 lines
        }
      });

      // Log artifact and dependency info
      if (verbose && mainResult.artifacts && mainResult.artifacts.length > 0) {
        logger.info(`Artifacts: ${mainResult.artifacts.length} resolved`);
      }

      if (verbose && mainResult.dependencies && mainResult.dependencies.length > 0) {
        logger.info(`Dependencies: ${mainResult.dependencies.length} resolved`);
      }
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
    const success = mainResult.success || !failOnError;

    if (!success && failOnError) {
      logger.error(`Maven Embedder execution failed`);
      if (mainResult.errorMessage) {
        logger.error(mainResult.errorMessage);
      }
    }

    return {
      success,
      terminalOutput: mainResult.goalResults.map(r => r.output.join('\n')).join('\n'),
      output: mainResult,
      error: mainResult.errorMessage
    };

  } catch (error: any) {
    const errorMessage = error?.message || String(error);
    logger.error(`Maven Embedder executor failed: ${errorMessage}`);

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

// Legacy invoker-based executor implementation
async function runInvokerExecutor(
  options: MavenBatchExecutorOptions,
  context: ExecutorContext
): Promise<ExecutorResult> {
  // Import the original executor functionality
  const originalExecutor = await import('./executor');
  return originalExecutor.default(options, context);
}

// Enhanced batch executor for both embedder and invoker
export async function batchMavenExecutor(
  taskGraph: TaskGraph,
  inputs: Record<string, MavenBatchExecutorOptions>
): Promise<Record<string, { success: boolean; terminalOutput: string }>> {
  // Check environment variable to decide which executor to use
  // Default to embedder unless explicitly disabled
  const useEmbedder = process.env.NX_MAVEN_USE_EMBEDDER !== 'false';

  if (useEmbedder) {
    return await batchEmbedderExecutor(taskGraph, inputs);
  } else {
    // Use original batch executor
    const originalExecutor = await import('./executor');
    return originalExecutor.batchMavenExecutor(taskGraph, inputs);
  }
}

// Embedder-based batch executor
async function batchEmbedderExecutor(
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
    let verbose = false;
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

    // Execute ALL unique goals across ALL unique projects in a single embedder batch
    const batchOptions = { ...commonOptions!, verbose };
    const batchResult = await executeMultiProjectEmbedderBatch(uniqueGoals, uniqueProjects, batchOptions, process.cwd());

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

// Execute Maven goals across multiple projects using embedder in a single batch
async function executeMultiProjectEmbedderBatch(
  goals: string[],
  projects: string[],
  options: MavenBatchExecutorOptions,
  workspaceRoot: string
): Promise<Record<string, TaskExecutionResult>> {
  const {
    verbose = false,
    mavenPluginPath = 'maven-plugin'
  } = options;

  // Resolve paths
  const pluginDir = join(workspaceRoot, mavenPluginPath);

  // Validate plugin directory
  if (!existsSync(pluginDir)) {
    throw new Error(`Maven plugin directory not found: ${pluginDir}`);
  }

  // Check for embedder executor
  const embedderExecutorClasspath = join(pluginDir, 'target/classes');
  const dependencyPath = join(pluginDir, 'target/dependency');

  if (!existsSync(embedderExecutorClasspath)) {
    throw new Error(`Maven plugin not compiled. Run 'mvn compile' in ${pluginDir}`);
  }

  if (!existsSync(dependencyPath)) {
    throw new Error(`Maven dependencies not copied. Run 'mvn dependency:copy-dependencies' in ${pluginDir}`);
  }

  const goalsString = goals.join(',');
  const projectsString = projects.join(',');
  const verboseFlag = verbose ? 'true' : 'false';

  // Build command for embedder: goals, workspaceRoot, projects, verbose
  const classpath = `${embedderExecutorClasspath}:${dependencyPath}/*`;
  const command = `java -Dmaven.multiModuleProjectDirectory="${workspaceRoot}" -cp "${classpath}" NxMavenEmbedderBatchExecutor "${goalsString}" "${workspaceRoot}" "${projectsString}" ${verboseFlag}`;

  // Execute the embedder command with streaming
  const startTime = Date.now();
  const output = await executeWithStreaming(command, pluginDir, verbose);

  // Parse JSON output from embedder executor
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
  const result: Record<string, TaskExecutionResult> = JSON.parse(jsonOutput);

  if (verbose) {
    logger.info(`Multi-project Maven Embedder batch execution completed`);
    const taskCount = Object.keys(result).length;
    const successfulTasks = Object.values(result).filter(r => r.success).length;
    logger.info(`Tasks: ${taskCount} total, ${successfulTasks} successful`);
  }

  return result;
}

// Check if a task's goals were successful based on embedder batch results
function isTaskSuccessful(taskId: string, taskGoalMapping: Map<string, string[]>, batchResult: Record<string, TaskExecutionResult>): boolean {
  const taskGoals = taskGoalMapping.get(taskId) || [];
  
  // Look for a task result that matches this task's goals
  for (const taskResult of Object.values(batchResult)) {
    const taskResultGoals = taskResult.goalResults.map(gr => gr.goal);
    
    // Check if this task result contains all requested goals
    const hasAllGoals = taskGoals.every(requestedGoal => 
      taskResultGoals.some(resultGoal => 
        resultGoal === requestedGoal ||
        resultGoal.includes(requestedGoal) ||
        resultGoal.endsWith(`:${requestedGoal}`)
      )
    );
    
    if (hasAllGoals) {
      // Check if all matching goals were successful
      return taskGoals.every(requestedGoal => 
        taskResult.goalResults.some(gr => 
          gr.success && (
            gr.goal === requestedGoal ||
            gr.goal.includes(requestedGoal) ||
            gr.goal.endsWith(`:${requestedGoal}`)
          )
        )
      );
    }
  }
  
  return false;
}

// Get task-specific output from embedder batch results
function getTaskSpecificOutput(taskId: string, taskGoalMapping: Map<string, string[]>, batchResult: Record<string, TaskExecutionResult>): string {
  const taskGoals = taskGoalMapping.get(taskId) || [];
  const relevantResults: string[] = [];
  
  // Find output from goals that match this task
  for (const taskResult of Object.values(batchResult)) {
    for (const requestedGoal of taskGoals) {
      const matchingGoalResults = taskResult.goalResults.filter(gr => 
        gr.goal === requestedGoal ||
        gr.goal.includes(requestedGoal) ||
        gr.goal.endsWith(`:${requestedGoal}`)
      );
      
      matchingGoalResults.forEach(gr => {
        relevantResults.push(...gr.output);
        if (!gr.success && gr.errors.length > 0) {
          relevantResults.push(...gr.errors);
        }
      });
    }
  }
  
  // If no specific output found, return all batch output
  if (relevantResults.length === 0) {
    return Object.values(batchResult).map(tr => 
      tr.goalResults.map(gr => gr.output.join('\n')).join('\n')
    ).join('\n');
  }
  
  return relevantResults.join('\n');
}

// Execute Maven command with streaming output using PseudoTerminal
async function executeWithStreaming(command: string, cwd: string, verbose: boolean): Promise<string> {
  // Create PseudoTerminal with skipSupportCheck=true to bypass TTY requirement
  const pseudoTerminal = createPseudoTerminal(true);
  let terminalOutput = '';

  if (verbose) {
    logger.info(`Executing with PseudoTerminal: ${command}`);
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