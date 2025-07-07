import com.google.gson.Gson
import com.google.gson.GsonBuilder
// import org.apache.maven.cli.MavenCli
import org.apache.maven.execution.*
// import org.apache.maven.execution.DefaultMavenSession
import org.apache.maven.Maven
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuildingRequest
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.plugin.PluginManager
import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.descriptor.MojoDescriptor
import org.apache.maven.lifecycle.LifecycleExecutor
import org.apache.maven.lifecycle.MavenExecutionPlan
import org.eclipse.aether.RepositorySystemSession
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.component.repository.exception.ComponentLookupException
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Nx Maven Embedder Batch Executor - Uses Maven Embedder API for direct integration
 * with Maven's execution engine, providing proper plugin resolution, simplified session
 * management, and per-task result tracking.
 */
object NxMavenEmbedderBatchExecutor {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var plexusContainer: PlexusContainer
    private lateinit var sessionContext: EmbedderSessionContext

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("Usage: java NxMavenEmbedderBatchExecutor <goals> <workspaceRoot> <projects> [verbose]")
            System.err.println("Example: java NxMavenEmbedderBatchExecutor \"compile,test\" \"/workspace\" \".,module1,module2\" true")
            exitProcess(1)
        }

        val goalsList = args[0]
        val workspaceRoot = args[1] 
        val projectsList = args[2]
        val verbose = args.size > 3 && args[3].toBoolean()

        try {
            val goals = goalsList.split(",")
            val projects = projectsList.split(",")
            val result = executeBatch(goals, workspaceRoot, projects, verbose)
            
            // Output JSON result for Nx to parse
            println(gson.toJson(result))
            
            // Exit with appropriate code
            exitProcess(if (result.values.all { it.success }) 0 else 1)
            
        } catch (e: Exception) {
            val errorResult = mapOf(
                "error" to "Embedder batch executor failed: ${e.message}",
                "success" to false
            )
            
            System.err.println(gson.toJson(errorResult))
            exitProcess(1)
        }
    }

    /**
     * Execute multiple Maven goals across multiple projects using Maven Embedder API
     */
    fun executeBatch(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean): Map<String, TaskExecutionResult> {
        return try {
            // Initialize Maven Embedder
            initializeEmbedder(workspaceRoot, verbose)
            
            // Build task executions
            val tasks = buildTaskExecutions(goals, workspaceRoot, projects, verbose)
            
            // Execute all tasks
            executeTasks(tasks, verbose)
            
        } catch (e: Exception) {
            if (verbose) {
                System.err.println("Batch execution failed: ${e.message}")
                e.printStackTrace()
            }
            throw e
        } finally {
            // Cleanup embedder resources
            cleanupEmbedder()
        }
    }

    /**
     * Initialize Maven Embedder and create session context
     */
    private fun initializeEmbedder(workspaceRoot: String, verbose: Boolean) {
        try {
            // Create Plexus container for Maven components
            plexusContainer = DefaultPlexusContainer()
            
            // Get Maven execution request builder
            val executionRequestPopulator = plexusContainer.lookup(MavenExecutionRequestPopulator::class.java)
            
            // Create Maven execution request
            val request = DefaultMavenExecutionRequest().apply {
                // Set base directory
                setBaseDirectory(File(workspaceRoot))
                
                // Set POM file
                setPom(File(workspaceRoot, "pom.xml"))
                
                // Configure logging level
                setLoggingLevel(if (verbose) MavenExecutionRequest.LOGGING_LEVEL_DEBUG else MavenExecutionRequest.LOGGING_LEVEL_INFO)
                
                // Set system properties
                systemProperties.setProperty("maven.multiModuleProjectDirectory", workspaceRoot)
                
                // Enable batch mode
                setInteractiveMode(false)
                
                // Set goals (will be overridden per task)
                setGoals(emptyList())
            }
            
            // Populate execution request with defaults
            // executionRequestPopulator.populateFromSettings(request, File(System.getProperty("user.home"), ".m2/settings.xml"))
            executionRequestPopulator.populateDefaults(request)
            
            // Create Maven session
            val maven = plexusContainer.lookup(Maven::class.java)
            val repositorySystemSession = plexusContainer.lookup(RepositorySystemSession::class.java)
            // TODO: Fix DefaultMavenSession constructor
            val mavenSession = null as MavenSession?
            
            // Initialize session context
            sessionContext = EmbedderSessionContext(mavenSession!!)
            
            if (verbose) {
                println("Maven Embedder initialized successfully")
                println("Workspace root: $workspaceRoot")
                println("Local repository: ${repositorySystemSession.localRepository.basedir}")
            }
            
        } catch (e: ComponentLookupException) {
            throw RuntimeException("Failed to initialize Maven Embedder: ${e.message}", e)
        }
    }

    /**
     * Build task executions from goals and projects
     */
    private fun buildTaskExecutions(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean): List<TaskExecution> {
        val tasks = mutableListOf<TaskExecution>()
        
        try {
            // Get project builder
            val projectBuilder = plexusContainer.lookup(ProjectBuilder::class.java)
            
            // Build project building request
            val projectBuildingRequest = sessionContext.mavenSession.projectBuildingRequest.apply {
                validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
                isProcessPlugins = true
                isResolveDependencies = true
            }
            
            for ((index, projectPath) in projects.withIndex()) {
                val projectDir = if (projectPath == ".") {
                    File(workspaceRoot)
                } else {
                    File(workspaceRoot, projectPath)
                }
                
                val pomFile = File(projectDir, "pom.xml")
                if (!pomFile.exists()) {
                    if (verbose) {
                        println("Skipping project $projectPath - no pom.xml found")
                    }
                    continue
                }
                
                try {
                    // Build Maven project
                    val projectBuildingResult = projectBuilder.build(pomFile, projectBuildingRequest)
                    val mavenProject = projectBuildingResult.project
                    
                    // Create task execution for each project
                    val taskId = "task-${index}-${mavenProject.artifactId}"
                    val task = TaskExecution(
                        taskId = taskId,
                        goals = goals,
                        project = mavenProject,
                        projectRoot = projectPath,
                        configuration = emptyMap()
                    )
                    
                    tasks.add(task)
                    
                    if (verbose) {
                        println("Created task: ${task.getDescription()}")
                    }
                    
                } catch (e: Exception) {
                    if (verbose) {
                        println("Failed to build project $projectPath: ${e.message}")
                    }
                    // Create a failed task execution
                    val taskId = "task-${index}-failed"
                    val failedResult = TaskExecutionResult(
                        taskId = taskId,
                        success = false,
                        duration = 0,
                        goalResults = emptyList(),
                        errorMessage = "Failed to build project: ${e.message}"
                    )
                    sessionContext.storeExecutionResult(taskId, failedResult)
                }
            }
            
        } catch (e: ComponentLookupException) {
            throw RuntimeException("Failed to build task executions: ${e.message}", e)
        }
        
        return tasks
    }

    /**
     * Execute all tasks using Maven Embedder
     */
    private fun executeTasks(tasks: List<TaskExecution>, verbose: Boolean): Map<String, TaskExecutionResult> {
        val results = mutableMapOf<String, TaskExecutionResult>()
        
        for (task in tasks) {
            val startTime = System.currentTimeMillis()
            
            try {
                if (verbose) {
                    println("Executing: ${task.getDescription()}")
                }
                
                val taskResult = executeTask(task, verbose)
                results[task.taskId] = taskResult
                sessionContext.storeExecutionResult(task.taskId, taskResult)
                
                val duration = System.currentTimeMillis() - startTime
                if (verbose) {
                    println("Completed: ${task.taskId} in ${duration}ms - ${if (taskResult.success) "SUCCESS" else "FAILURE"}")
                }
                
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                val failedResult = TaskExecutionResult(
                    taskId = task.taskId,
                    success = false,
                    duration = duration,
                    goalResults = emptyList(),
                    errorMessage = "Task execution failed: ${e.message}"
                )
                
                results[task.taskId] = failedResult
                sessionContext.storeExecutionResult(task.taskId, failedResult)
                
                if (verbose) {
                    println("Failed: ${task.taskId} in ${duration}ms - ${e.message}")
                }
            }
        }
        
        return results
    }

    /**
     * Execute a single task using Maven Embedder
     */
    private fun executeTask(task: TaskExecution, verbose: Boolean): TaskExecutionResult {
        val startTime = System.currentTimeMillis()
        val goalResults = mutableListOf<GoalExecutionResult>()
        
        try {
            // Get lifecycle executor
            val lifecycleExecutor = plexusContainer.lookup(LifecycleExecutor::class.java)
            
            // Create execution request for this task
            val executionRequest = DefaultMavenExecutionRequest().apply {
                setBaseDirectory(File(task.getEffectiveProjectRoot()))
                setPom(File(task.project.basedir, "pom.xml"))
                setGoals(task.goals)
                setLoggingLevel(if (verbose) MavenExecutionRequest.LOGGING_LEVEL_DEBUG else MavenExecutionRequest.LOGGING_LEVEL_INFO)
                setInteractiveMode(false)
                
                // Add any task-specific properties
                task.properties.forEach { (key, value) ->
                    userProperties.setProperty(key, value)
                }
            }
            
            // Update session with current project
            sessionContext.mavenSession.apply {
                currentProject = task.project
                projects = listOf(task.project)
                request.setGoals(task.goals)
            }
            
            // Execute each goal individually to get per-goal results
            for (goal in task.goals) {
                val goalStartTime = System.currentTimeMillis()
                
                try {
                    if (verbose) {
                        println("  Executing goal: $goal on project ${task.project.artifactId}")
                    }
                    
                    // Create single-goal execution plan
                    val singleGoalRequest = DefaultMavenExecutionRequest().apply {
                        setBaseDirectory(File(task.getEffectiveProjectRoot()))
                        setPom(executionRequest.pom)
                        setGoals(listOf(goal))
                        setLoggingLevel(executionRequest.loggingLevel)
                        setInteractiveMode(executionRequest.isInteractiveMode)
                    }
                    
                    // Capture output
                    val outputCapture = ByteArrayOutputStream()
                    val errorCapture = ByteArrayOutputStream()
                    val originalOut = System.out
                    val originalErr = System.err
                    
                    try {
                        System.setOut(PrintStream(outputCapture))
                        System.setErr(PrintStream(errorCapture))
                        
                        // Execute the goal
                        val executionPlan = lifecycleExecutor.calculateExecutionPlan(sessionContext.mavenSession, goal)
                        lifecycleExecutor.execute(sessionContext.mavenSession)
                        
                        val goalDuration = System.currentTimeMillis() - goalStartTime
                        
                        val goalResult = GoalExecutionResult(
                            goal = goal,
                            success = !sessionContext.mavenSession.result.hasExceptions(),
                            duration = goalDuration,
                            output = outputCapture.toString().lines().filter { it.isNotBlank() },
                            errors = errorCapture.toString().lines().filter { it.isNotBlank() },
                            exitCode = if (sessionContext.mavenSession.result.hasExceptions()) 1 else 0
                        )
                        
                        goalResults.add(goalResult)
                        
                    } finally {
                        System.setOut(originalOut)
                        System.setErr(originalErr)
                    }
                    
                } catch (e: Exception) {
                    val goalDuration = System.currentTimeMillis() - goalStartTime
                    val failedGoalResult = GoalExecutionResult(
                        goal = goal,
                        success = false,
                        duration = goalDuration,
                        output = emptyList(),
                        errors = listOf(e.message ?: "Unknown error"),
                        exitCode = 1
                    )
                    
                    goalResults.add(failedGoalResult)
                    
                    if (verbose) {
                        println("  Goal $goal failed: ${e.message}")
                    }
                }
            }
            
            val totalDuration = System.currentTimeMillis() - startTime
            val allGoalsSuccessful = goalResults.all { it.success }
            
            // Collect artifacts and dependencies
            val artifacts = task.project.artifacts?.map { ArtifactResult.fromMavenArtifact(it) } ?: emptyList()
            val dependencies = task.project.dependencies?.map { DependencyResult.fromMavenDependency(it) } ?: emptyList()
            
            return TaskExecutionResult(
                taskId = task.taskId,
                success = allGoalsSuccessful,
                duration = totalDuration,
                goalResults = goalResults,
                artifacts = artifacts,
                dependencies = dependencies,
                executionContext = mapOf(
                    "projectPath" to task.getEffectiveProjectRoot(),
                    "projectArtifactId" to task.project.artifactId,
                    "goalCount" to task.goals.size
                )
            )
            
        } catch (e: ComponentLookupException) {
            val duration = System.currentTimeMillis() - startTime
            throw RuntimeException("Failed to execute task ${task.taskId}: ${e.message}", e)
        }
    }

    /**
     * Cleanup Maven Embedder resources
     */
    private fun cleanupEmbedder() {
        try {
            if (::plexusContainer.isInitialized) {
                plexusContainer.dispose()
            }
            if (::sessionContext.isInitialized) {
                sessionContext.clearCaches()
            }
        } catch (e: Exception) {
            System.err.println("Warning: Failed to cleanup embedder resources: ${e.message}")
        }
    }
}