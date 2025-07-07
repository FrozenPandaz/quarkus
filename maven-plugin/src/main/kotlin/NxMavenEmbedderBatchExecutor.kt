import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.maven.cli.MavenCli
import org.apache.maven.cli.CliRequest
import org.apache.maven.execution.*
// import org.apache.maven.execution.DefaultMavenSession // Not available in this Maven version
import org.apache.maven.Maven
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuildingRequest
import org.apache.maven.project.DefaultProjectBuildingRequest
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
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem as AetherRepositorySystem
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.apache.maven.settings.Settings
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest
import org.apache.maven.settings.building.SettingsBuilder
import org.apache.maven.settings.building.SettingsBuildingRequest
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.ContainerConfiguration
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.component.repository.exception.ComponentLookupException
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

// Import model classes
import model.*

/**
 * Nx Maven Embedder Batch Executor - Uses Maven Embedder API for direct integration
 * with Maven's execution engine, providing proper plugin resolution, simplified session
 * management, and per-task result tracking.
 */
object NxMavenEmbedderBatchExecutor {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var plexusContainer: PlexusContainer
    private lateinit var mavenCli: MavenCli
    private var sessionContext: EmbedderSessionContext? = null
    private lateinit var sessionPersistence: EmbedderSessionPersistence
    private lateinit var sessionFactory: MavenSessionFactory
    private lateinit var repositorySystemSession: RepositorySystemSession

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 4) {
            System.err.println("Usage: java NxMavenEmbedderBatchExecutor <goals> <workspaceRoot> <projects> <outputFile> [verbose]")
            System.err.println("Example: java NxMavenEmbedderBatchExecutor \"compile,test\" \"/workspace\" \".,module1,module2\" \"/tmp/results.json\" true")
            exitProcess(1)
        }

        val goalsList = args[0]
        val workspaceRoot = args[1] 
        val projectsList = args[2]
        val outputFile = args[3]
        val verbose = if (args.size > 4) args[4].toBoolean() else true

        try {
            val goals = goalsList.split(",")
            val projects = projectsList.split(",")
            val result = executeBatch(goals, workspaceRoot, projects, verbose)
            
            // Write JSON result to output file
            writeResultsToFile(result, outputFile, verbose)
            
            // Exit with appropriate code
            exitProcess(if (result.values.all { it.success }) 0 else 1)
            
        } catch (e: Exception) {
            val errorResult = mapOf(
                "error" to "Embedder batch executor failed: ${e.message}",
                "success" to false
            )
            
            // Write error to output file if possible, otherwise stderr
            try {
                writeResultsToFile(errorResult, outputFile, verbose)
            } catch (writeError: Exception) {
                System.err.println("Failed to write error to output file: ${writeError.message}")
                System.err.println(gson.toJson(errorResult))
            }
            exitProcess(1)
        }
    }

    /**
     * Execute multiple Maven goals across multiple projects using Maven Embedder API
     */
    fun executeBatch(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean): Map<String, TaskExecutionResult> {
        val totalStartTime = System.currentTimeMillis()
        
        if (verbose) {
            println("=== EMBEDDER BATCH EXECUTOR PERFORMANCE TIMING ===")
            println("🚀 Starting batch execution at ${java.time.Instant.ofEpochMilli(totalStartTime)}")
            println("📋 Goals: ${goals.joinToString(", ")}")
            println("📂 Projects: ${projects.size} projects (${projects.joinToString(", ")})")
            println("📍 Workspace: $workspaceRoot")
            println()
        }
        
        return try {
            // 1. Initialize Maven Embedder and session persistence
            val initStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT] Starting Maven Embedder initialization...")
            }
            
            initializeEmbedder(workspaceRoot, verbose)
            
            val initDuration = System.currentTimeMillis() - initStartTime
            if (verbose) {
                println("✅ [INIT] Completed in ${initDuration}ms")
                println()
            }
            
            // 2. Build task executions
            val taskBuildStartTime = System.currentTimeMillis()
            if (verbose) {
                println("📝 [TASK BUILD] Building task executions...")
            }
            
            val tasks = buildTaskExecutions(goals, workspaceRoot, projects, verbose)
            
            val taskBuildDuration = System.currentTimeMillis() - taskBuildStartTime
            if (verbose) {
                println("✅ [TASK BUILD] Built ${tasks.size} tasks in ${taskBuildDuration}ms")
                println()
            }
            
            // 3. Load previous session data if available
            val sessionLoadStartTime = System.currentTimeMillis()
            if (verbose) {
                println("💾 [SESSION LOAD] Loading previous session data...")
            }
            
            loadPreviousSessionData(tasks, verbose)
            
            val sessionLoadDuration = System.currentTimeMillis() - sessionLoadStartTime
            if (verbose) {
                println("✅ [SESSION LOAD] Completed in ${sessionLoadDuration}ms")
                println()
            }
            
            // 4. Execute all tasks
            val taskExecutionStartTime = System.currentTimeMillis()
            if (verbose) {
                println("⚡ [TASK EXECUTION] Starting execution of ${tasks.size} tasks...")
                println("=".repeat(60))
            }
            
            val results = executeTasks(tasks, verbose)
            
            val taskExecutionDuration = System.currentTimeMillis() - taskExecutionStartTime
            if (verbose) {
                println("=".repeat(60))
                println("✅ [TASK EXECUTION] All tasks completed in ${taskExecutionDuration}ms")
                val successCount = results.values.count { it.success }
                val failureCount = results.size - successCount
                println("📊 Task Results: ${successCount} successful, ${failureCount} failed")
                println()
            }
            
            // 5. Save session data to disk after execution
            val sessionSaveStartTime = System.currentTimeMillis()
            if (verbose) {
                println("💾 [SESSION SAVE] Saving session data to disk...")
            }
            
            saveSessionData(tasks, results, verbose)
            
            val sessionSaveDuration = System.currentTimeMillis() - sessionSaveStartTime
            if (verbose) {
                println("✅ [SESSION SAVE] Completed in ${sessionSaveDuration}ms")
                println()
            }
            
            // 6. Performance Summary
            val totalDuration = System.currentTimeMillis() - totalStartTime
            val preTaskDuration = initDuration + taskBuildDuration + sessionLoadDuration
            val postTaskDuration = sessionSaveDuration
            
            if (verbose) {
                println("🏁 [SUMMARY] Batch execution completed!")
                println("⏱️  Total execution time: ${totalDuration}ms")
                println("📈 Performance breakdown:")
                println("   • Pre-task setup: ${preTaskDuration}ms (${String.format("%.1f", preTaskDuration * 100.0 / totalDuration)}%)")
                println("     - Initialization: ${initDuration}ms")
                println("     - Task building: ${taskBuildDuration}ms") 
                println("     - Session loading: ${sessionLoadDuration}ms")
                println("   • Task execution: ${taskExecutionDuration}ms (${String.format("%.1f", taskExecutionDuration * 100.0 / totalDuration)}%)")
                println("   • Post-task cleanup: ${postTaskDuration}ms (${String.format("%.1f", postTaskDuration * 100.0 / totalDuration)}%)")
                println("     - Session saving: ${sessionSaveDuration}ms")
                println()
            }
            
            results
            
        } catch (e: Exception) {
            val totalDuration = System.currentTimeMillis() - totalStartTime
            if (verbose) {
                println("❌ [ERROR] Batch execution failed after ${totalDuration}ms: ${e.message}")
                e.printStackTrace()
            }
            throw e
        } finally {
            // Cleanup embedder resources
            val cleanupStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🧹 [CLEANUP] Starting cleanup...")
            }
            
            cleanupEmbedder()
            
            val cleanupDuration = System.currentTimeMillis() - cleanupStartTime
            val totalDuration = System.currentTimeMillis() - totalStartTime
            if (verbose) {
                println("✅ [CLEANUP] Completed in ${cleanupDuration}ms")
                println("🏆 [FINAL] Total time including cleanup: ${totalDuration}ms")
                println("=".repeat(60))
            }
        }
    }

    /**
     * Initialize Maven Embedder and create session context
     */
    private fun initializeEmbedder(workspaceRoot: String, verbose: Boolean) {
        try {
            // 1. Setup environment variables first (like mvn command does)
            val envStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-ENV] Setting up environment variables...")
            }
            setupEnvironmentVariables()
            val envDuration = System.currentTimeMillis() - envStartTime
            if (verbose) {
                println("✅ [INIT-ENV] Environment setup completed in ${envDuration}ms")
            }
            
            // 2. Create Maven container with proper component scanning
            val containerStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-CONTAINER] Creating Maven Plexus container...")
            }
            System.setProperty("maven.multiModuleProjectDirectory", workspaceRoot)
            
            try {
                // Create PlexusContainer with Maven component scanning enabled
                val configuration = DefaultContainerConfiguration()
                    .setAutoWiring(true)
                    .setComponentVisibility(PlexusConstants.REALM_VISIBILITY)
                    .setClassPathScanning(PlexusConstants.SCANNING_ON)  // Enable component scanning
                
                plexusContainer = DefaultPlexusContainer(configuration)
                
                val containerDuration = System.currentTimeMillis() - containerStartTime
                if (verbose) {
                    println("✅ [INIT-CONTAINER] Maven container initialized in ${containerDuration}ms")
                    
                    // Test component lookup
                    val lookupStartTime = System.currentTimeMillis()
                    try {
                        val maven = getComponent<Maven>("org.apache.maven.Maven")
                        val lookupDuration = System.currentTimeMillis() - lookupStartTime
                        println("✅ [INIT-CONTAINER] Maven component lookup successful in ${lookupDuration}ms")
                    } catch (e: Exception) {
                        val lookupDuration = System.currentTimeMillis() - lookupStartTime
                        println("❌ [INIT-CONTAINER] Maven component lookup failed in ${lookupDuration}ms: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                val containerDuration = System.currentTimeMillis() - containerStartTime
                if (verbose) {
                    println("❌ [INIT-CONTAINER] Failed to initialize Maven container in ${containerDuration}ms")
                }
                throw RuntimeException("Failed to initialize Maven container: ${e.message}", e)
            }
            
            // 3. Load Maven settings (user and global) early
            val settingsStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-SETTINGS] Loading Maven settings...")
            }
            val settings = loadMavenSettings(verbose)
            val settingsDuration = System.currentTimeMillis() - settingsStartTime
            if (verbose) {
                println("✅ [INIT-SETTINGS] Maven settings loaded in ${settingsDuration}ms")
            }
            
            // 4. Get Maven execution request builder (using robust lookup like IntelliJ)
            val popStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-POPULATOR] Getting execution request populator...")
            }
            val executionRequestPopulator = getComponent<MavenExecutionRequestPopulator>("org.apache.maven.execution.MavenExecutionRequestPopulator")
            val popDuration = System.currentTimeMillis() - popStartTime
            if (verbose) {
                println("✅ [INIT-POPULATOR] Execution request populator obtained in ${popDuration}ms")
            }
            
            // 5. Create Maven execution request
            val requestStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-REQUEST] Creating Maven execution request...")
            }
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
                
                // Enable parallel execution - use available CPU cores
                val availableCores = Runtime.getRuntime().availableProcessors()
                val parallelThreads = maxOf(1, availableCores - 1) // Leave one core for system
                setDegreeOfConcurrency(parallelThreads)
                
                // Set Maven parallel execution properties that Maven uses internally
                systemProperties.setProperty("maven.threads", parallelThreads.toString())
                systemProperties.setProperty("maven.parallel", "true")
                
                if (verbose) {
                    println("🔄 [PARALLEL] Enabling parallel execution with $parallelThreads threads (${availableCores} cores available)")
                }
                
                // Set goals (will be overridden per task)
                setGoals(emptyList())
            }
            val requestDuration = System.currentTimeMillis() - requestStartTime
            if (verbose) {
                println("✅ [INIT-REQUEST] Maven execution request created in ${requestDuration}ms")
            }
            
            // 6. Populate execution request with settings and defaults
            val populateStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-POPULATE] Populating execution request with settings...")
            }
            executionRequestPopulator.populateFromSettings(request, settings)
            executionRequestPopulator.populateDefaults(request)
            val populateDuration = System.currentTimeMillis() - populateStartTime
            if (verbose) {
                println("✅ [INIT-POPULATE] Execution request populated in ${populateDuration}ms")
            }
            
            // 7. Apply profiles, mirrors, and proxies from settings
            val configStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-CONFIG] Applying settings configuration...")
            }
            applySettingsConfiguration(request, settings, verbose)
            val configDuration = System.currentTimeMillis() - configStartTime
            if (verbose) {
                println("✅ [INIT-CONFIG] Settings configuration applied in ${configDuration}ms")
            }
            
            // 8. Initialize repository system and session
            val repoStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-REPO] Initializing repository system...")
            }
            val aetherRepositorySystem = getComponent<AetherRepositorySystem>("org.eclipse.aether.RepositorySystem")
            repositorySystemSession = createRepositorySystemSession(aetherRepositorySystem, settings, verbose)
            val repoDuration = System.currentTimeMillis() - repoStartTime
            if (verbose) {
                println("✅ [INIT-REPO] Repository system initialized in ${repoDuration}ms")
            }
            
            // 9. Initialize Maven session factory
            val factoryStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-FACTORY] Creating Maven session factory...")
            }
            sessionFactory = MavenSessionFactory(plexusContainer, verbose)
            val factoryDuration = System.currentTimeMillis() - factoryStartTime
            if (verbose) {
                println("✅ [INIT-FACTORY] Maven session factory created in ${factoryDuration}ms")
            }
            
            // 10. Initialize session persistence for disk-based session handling
            val persistenceStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-PERSIST] Initializing session persistence...")
            }
            sessionPersistence = EmbedderSessionPersistence(workspaceRoot, verbose)
            val persistenceDuration = System.currentTimeMillis() - persistenceStartTime
            if (verbose) {
                println("✅ [INIT-PERSIST] Session persistence initialized in ${persistenceDuration}ms")
            }
            
            // 11. Create a root Maven session for the workspace
            val rootSessionStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-ROOT-SESSION] Creating root Maven session...")
            }
            val rootMavenSession = createRootMavenSession(request, repositorySystemSession, verbose)
            val rootSessionDuration = System.currentTimeMillis() - rootSessionStartTime
            if (verbose) {
                println("✅ [INIT-ROOT-SESSION] Root Maven session created in ${rootSessionDuration}ms")
            }
            
            // 12. Initialize session context with real Maven session
            val contextStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-CONTEXT] Initializing session context...")
            }
            sessionContext = EmbedderSessionContext(rootMavenSession)
            val contextDuration = System.currentTimeMillis() - contextStartTime
            if (verbose) {
                println("✅ [INIT-CONTEXT] Session context initialized in ${contextDuration}ms")
            }
            
            if (verbose) {
                println("🎉 [INIT-COMPLETE] Maven Embedder initialized successfully")
                println("📁 Workspace root: $workspaceRoot")
                println("📦 Local repository: ${repositorySystemSession.localRepository.basedir}")
                println("⚙️  User settings: ${settings.localRepository ?: "default"}")
                println("💾 Session persistence directory: ${sessionPersistence.getSessionDirectory().absolutePath}")
                
                // Performance summary
                val totalInitTime = envDuration + (System.currentTimeMillis() - containerStartTime)
                println("⏱️  Initialization timing breakdown:")
                println("   • Environment setup: ${envDuration}ms")
                println("   • Container creation: ${System.currentTimeMillis() - containerStartTime}ms")
                println("   • Settings loading: ${settingsDuration}ms")
                println("   • Request populator: ${popDuration}ms")
                println("   • Request creation: ${requestDuration}ms")
                println("   • Request population: ${populateDuration}ms")
                println("   • Settings config: ${configDuration}ms")
                println("   • Repository system: ${repoDuration}ms")
                println("   • Session factory: ${factoryDuration}ms")
                println("   • Session persistence: ${persistenceDuration}ms")
                println("   • Root session: ${rootSessionDuration}ms")
                println("   • Session context: ${contextDuration}ms")
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
            val projectBuilder = getComponent<ProjectBuilder>("org.apache.maven.project.ProjectBuilder")
            
            // Create basic project building request
            val projectBuildingRequest = org.apache.maven.project.DefaultProjectBuildingRequest().apply {
                validationLevel = ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL
                isProcessPlugins = false  // Simplified for now
                isResolveDependencies = false  // Simplified for now
                repositorySession = repositorySystemSession  // Set the repository session
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
                    sessionContext?.storeExecutionResult(taskId, failedResult)
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
                sessionContext?.storeExecutionResult(task.taskId, taskResult)
                
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
                sessionContext?.storeExecutionResult(task.taskId, failedResult)
                
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
            val lifecycleExecutor = getComponent<LifecycleExecutor>("org.apache.maven.lifecycle.LifecycleExecutor")
            
            // Create execution request for this task
            val executionRequest = DefaultMavenExecutionRequest().apply {
                setBaseDirectory(File(task.getEffectiveProjectRoot()))
                setPom(File(task.project.basedir, "pom.xml"))
                setGoals(task.goals)
                setLoggingLevel(if (verbose) MavenExecutionRequest.LOGGING_LEVEL_DEBUG else MavenExecutionRequest.LOGGING_LEVEL_INFO)
                setInteractiveMode(false)
                
                // Enable parallel execution for goals within this task
                val availableCores = Runtime.getRuntime().availableProcessors()
                val parallelThreads = maxOf(1, availableCores - 1) // Leave one core for system
                setDegreeOfConcurrency(parallelThreads)
                
                // Set Maven parallel execution properties that Maven uses internally
                systemProperties.setProperty("maven.threads", parallelThreads.toString())
                systemProperties.setProperty("maven.parallel", "true")
                
                // Add any task-specific properties
                task.properties.forEach { (key, value) ->
                    userProperties.setProperty(key, value)
                }
            }
            
            // TODO: Update session with current project when session is available
            // sessionContext?.mavenSession?.apply {
            //     currentProject = task.project
            //     projects = listOf(task.project)
            //     request.setGoals(task.goals)
            // }
            
            // Execute all goals in parallel using Maven's built-in parallel execution
            if (verbose) {
                println("      🚀 [PARALLEL] Executing ${task.goals.size} goals in parallel: ${task.goals.joinToString(", ")}")
            }
            
            try {
                // Create batch execution request with all goals
                val batchGoalRequest = DefaultMavenExecutionRequest().apply {
                    setBaseDirectory(File(task.getEffectiveProjectRoot()))
                    setPom(executionRequest.pom)
                    setGoals(task.goals) // Execute all goals together
                    setLoggingLevel(executionRequest.loggingLevel)
                    setInteractiveMode(executionRequest.isInteractiveMode)
                    
                    // Copy parallel execution settings from main request
                    setDegreeOfConcurrency(executionRequest.getDegreeOfConcurrency())
                }
                
                // Capture output for all goals
                val outputCapture = ByteArrayOutputStream()
                val errorCapture = ByteArrayOutputStream()
                val originalOut = System.out
                val originalErr = System.err
                
                var batchSuccess = false
                var batchExitCode = 0
                
                try {
                    System.setOut(PrintStream(outputCapture))
                    System.setErr(PrintStream(errorCapture))
                    
                    // Execute all goals in parallel using Maven lifecycle executor
                    val batchStartTime = System.currentTimeMillis()
                    
                    try {
                        // Get Maven session for this project with all goals
                        val projectSession = sessionFactory.createProjectSession(
                            batchGoalRequest,
                            repositorySystemSession,
                            task.project,
                            task.goals
                        )
                        
                        // Get lifecycle executor
                        val lifecycleExecutor = getComponent<LifecycleExecutor>("org.apache.maven.lifecycle.LifecycleExecutor")
                        
                        if (verbose) {
                            println("        🔄 [PARALLEL] Executing goals with ${batchGoalRequest.getDegreeOfConcurrency()} threads")
                        }
                        
                        // Execute all goals in parallel
                        lifecycleExecutor.execute(projectSession)
                        
                        // Check if execution was successful
                        batchSuccess = !MavenUtils.hasSessionExceptions(projectSession)
                        batchExitCode = if (batchSuccess) 0 else 1
                        
                        val batchDuration = System.currentTimeMillis() - batchStartTime
                        
                        if (verbose) {
                            if (batchSuccess) {
                                println("        ✅ [PARALLEL] All goals completed successfully in ${batchDuration}ms")
                            } else {
                                println("        ❌ [PARALLEL] Some goals failed in ${batchDuration}ms")
                            }
                        }
                        
                        // Create individual goal results (Maven parallel execution doesn't provide per-goal timing)
                        val avgDurationPerGoal = batchDuration / task.goals.size
                        for ((index, goal) in task.goals.withIndex()) {
                            val goalResult = GoalExecutionResult(
                                goal = goal,
                                success = batchSuccess, // All goals share the same success status in parallel execution
                                duration = avgDurationPerGoal, // Approximate duration per goal
                                output = if (index == 0) outputCapture.toString().lines().filter { it.isNotBlank() } else emptyList(), // Only include output once
                                errors = if (!batchSuccess && index == 0) errorCapture.toString().lines().filter { it.isNotBlank() } else emptyList(),
                                exitCode = batchExitCode
                            )
                            goalResults.add(goalResult)
                        }
                        
                    } catch (e: Exception) {
                        batchSuccess = false
                        batchExitCode = 1
                        
                        if (verbose) {
                            println("        ❌ [PARALLEL] Batch execution failed: ${e.message}")
                        }
                        
                        // Create failed goal results for all goals
                        for (goal in task.goals) {
                            val failedGoalResult = GoalExecutionResult(
                                goal = goal,
                                success = false,
                                duration = 0,
                                output = emptyList(),
                                errors = listOf(e.message ?: "Parallel execution failed"),
                                exitCode = 1
                            )
                            goalResults.add(failedGoalResult)
                        }
                        
                        throw e
                    }
                    
                } finally {
                    System.setOut(originalOut)
                    System.setErr(originalErr)
                }
                
            } catch (e: Exception) {
                if (verbose) {
                    println("      ❌ [PARALLEL] Failed to execute goals: ${e.message}")
                }
                // Goal results already added in inner catch block
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
     * Load Maven settings from user and global settings files
     */
    private fun loadMavenSettings(verbose: Boolean): Settings {
        val settingsBuilder = DefaultSettingsBuilderFactory().newInstance()
        
        val request = DefaultSettingsBuildingRequest().apply {
            // User settings file
            val userSettingsFile = File(System.getProperty("user.home"), ".m2/settings.xml")
            if (userSettingsFile.exists()) {
                setUserSettingsFile(userSettingsFile)
                if (verbose) {
                    println("Loading user settings from: ${userSettingsFile.absolutePath}")
                }
            }
            
            // Global settings file
            val mavenHome = System.getenv("MAVEN_HOME") ?: System.getProperty("maven.home")
            if (mavenHome != null) {
                val globalSettingsFile = File(mavenHome, "conf/settings.xml")
                if (globalSettingsFile.exists()) {
                    setGlobalSettingsFile(globalSettingsFile)
                    if (verbose) {
                        println("Loading global settings from: ${globalSettingsFile.absolutePath}")
                    }
                }
            }
            
            // Set system properties
            systemProperties = System.getProperties()
        }
        
        return try {
            settingsBuilder.build(request).effectiveSettings
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Failed to load Maven settings: ${e.message}")
            }
            Settings() // Return default settings
        }
    }
    
    /**
     * Create and configure repository system session - exactly like Maven CLI
     */
    private fun createRepositorySystemSession(repositorySystem: AetherRepositorySystem, settings: Settings, verbose: Boolean): RepositorySystemSession {
        val session = DefaultRepositorySystemSession()
        
        // 1. Configure local repository (Maven CLI order of precedence)
        val localRepoPath = System.getProperty("maven.repo.local")  // Command line -Dmaven.repo.local
            ?: settings.localRepository                              // Settings.xml
            ?: "${System.getProperty("user.home")}/.m2/repository"  // Default
        
        val localRepo = LocalRepository(localRepoPath)
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, localRepo)
        
        // 2. Configure offline mode (like Maven CLI)
        val offline = System.getProperty("maven.offline", "false").toBoolean() || settings.isOffline
        session.isOffline = offline
        
        // 3. Configure transfer and repository listeners (like Maven CLI does)
        // These would normally handle progress reporting, but we'll keep it simple for batch mode
        
        // 4. Configure checksums policy (like Maven CLI)
        val checksumPolicy = System.getProperty("maven.checksums.policy")
        if (checksumPolicy != null) {
            // Apply checksum policy if specified
        }
        
        // 5. Configure cache policies
        session.setConfigProperty("aether.updateCheckManager.sessionState", "bypass")
        
        // 6. Configure user agent (like Maven CLI)
        val userAgent = "Maven Embedder/${getMavenVersion()}"
        session.setConfigProperty("aether.connector.userAgent", userAgent)
        
        // 7. Apply workspace reader for reactor resolution
        // This is important for multi-module builds
        session.setWorkspaceReader(null) // Will be set up during project building
        
        if (verbose) {
            println("Local repository: $localRepoPath")
            println("Offline mode: $offline")
            println("User agent: $userAgent")
        }
        
        return session
    }
    
    /**
     * Get Maven version (like Maven CLI shows)
     */
    private fun getMavenVersion(): String {
        return try {
            // Try to get Maven version from runtime
            val mavenVersion = Maven::class.java.getPackage().implementationVersion
            mavenVersion ?: "3.8.8" // fallback version
        } catch (e: Exception) {
            "3.8.8" // fallback version
        }
    }
    
    /**
     * Apply settings configuration including profiles, mirrors, and proxies - exactly like Maven CLI
     */
    private fun applySettingsConfiguration(request: MavenExecutionRequest, settings: Settings, verbose: Boolean) {
        // 1. Apply active profiles (Maven CLI precedence order)
        val activeProfiles = mutableListOf<String>()
        
        // From settings.xml
        activeProfiles.addAll(settings.activeProfiles ?: emptyList())
        
        // From system properties (-P flag equivalent)
        val systemActiveProfiles = System.getProperty("maven.profiles.active")
        if (systemActiveProfiles != null) {
            activeProfiles.addAll(systemActiveProfiles.split(",").map { it.trim() })
        }
        
        // From environment variable
        val envActiveProfiles = System.getenv("MAVEN_ACTIVE_PROFILES")
        if (envActiveProfiles != null) {
            activeProfiles.addAll(envActiveProfiles.split(",").map { it.trim() })
        }
        
        if (activeProfiles.isNotEmpty()) {
            request.activeProfiles = activeProfiles.distinct()
            if (verbose) {
                println("Active profiles: ${activeProfiles.distinct().joinToString(", ")}")
            }
        }
        
        // 2. Apply inactive profiles (like Maven CLI -P !profile)
        val inactiveProfiles = mutableListOf<String>()
        val systemInactiveProfiles = System.getProperty("maven.profiles.inactive")
        if (systemInactiveProfiles != null) {
            inactiveProfiles.addAll(systemInactiveProfiles.split(",").map { it.trim() })
        }
        
        if (inactiveProfiles.isNotEmpty()) {
            request.inactiveProfiles = inactiveProfiles
            if (verbose) {
                println("Inactive profiles: ${inactiveProfiles.joinToString(", ")}")
            }
        }
        
        // 3. Apply mirrors (handled internally by Maven but log them)
        val mirrors = settings.mirrors ?: emptyList()
        if (mirrors.isNotEmpty() && verbose) {
            println("Configured mirrors: ${mirrors.size}")
            mirrors.forEach { mirror ->
                println("  Mirror: ${mirror.id} -> ${mirror.url} (for: ${mirror.mirrorOf})")
            }
        }
        
        // 4. Apply proxies (handled internally by Maven but log them)
        val proxies = settings.proxies?.filter { it.isActive } ?: emptyList()
        if (proxies.isNotEmpty() && verbose) {
            println("Active proxies: ${proxies.size}")
            proxies.forEach { proxy ->
                println("  Proxy: ${proxy.protocol}://${proxy.host}:${proxy.port}")
            }
        }
        
        // 5. Apply servers (for authentication)
        val servers = settings.servers ?: emptyList()
        if (servers.isNotEmpty() && verbose) {
            println("Configured servers: ${servers.size}")
        }
        
        // 6. Set global settings path for Maven to use
        if (verbose) {
            val profiles = settings.profiles ?: emptyList()
            println("Available profiles: ${profiles.size}")
            if (profiles.isNotEmpty()) {
                profiles.forEach { profile ->
                    println("  Profile: ${profile.id} (active by default: ${profile.activation?.isActiveByDefault ?: false})")
                }
            }
        }
    }
    
    /**
     * Setup environment variables that mvn command sets - exactly like Maven CLI
     */
    private fun setupEnvironmentVariables() {
        // 1. Set maven.multiModuleProjectDirectory (critical for Maven 3.3+)
        if (System.getProperty("maven.multiModuleProjectDirectory") == null) {
            val workspaceRoot = System.getProperty("user.dir")
            System.setProperty("maven.multiModuleProjectDirectory", workspaceRoot)
        }
        
        // 2. Set MAVEN_HOME if not already set
        if (System.getenv("MAVEN_HOME") == null) {
            val mavenHome = System.getProperty("maven.home")
            if (mavenHome != null) {
                System.setProperty("maven.home", mavenHome)
            }
        }
        
        // 3. Set JAVA_HOME property from environment
        val javaHome = System.getenv("JAVA_HOME")
        if (javaHome != null) {
            System.setProperty("java.home", javaHome)
        }
        
        // 4. Apply MAVEN_OPTS exactly like Maven CLI
        val mavenOpts = System.getenv("MAVEN_OPTS")
        if (mavenOpts != null) {
            // Parse MAVEN_OPTS and apply as system properties (like mvn script does)
            val opts = mavenOpts.split("\\s+".toRegex())
            for (opt in opts) {
                when {
                    opt.startsWith("-D") -> {
                        // System property
                        val prop = opt.substring(2)
                        val eq = prop.indexOf('=')
                        if (eq > 0) {
                            val key = prop.substring(0, eq)
                            val value = prop.substring(eq + 1)
                            System.setProperty(key, value)
                        } else {
                            System.setProperty(prop, "true")
                        }
                    }
                    opt.startsWith("-X") -> {
                        // JVM options are typically handled at JVM startup
                        // We can only log them here
                        if (System.getProperty("maven.verbose", "false") == "true") {
                            println("Note: JVM option $opt should be set at JVM startup")
                        }
                    }
                }
            }
        }
        
        // 5. Set batch mode indicators (like Maven CLI)
        System.setProperty("maven.batch.mode", "true")
        System.setProperty("maven.terminal", "false")
        
        // 6. Disable color output by default in batch mode
        if (System.getProperty("maven.color") == null) {
            System.setProperty("maven.color", "false")
        }
    }
    
    /**
     * Load previous session data for tasks
     */
    private fun loadPreviousSessionData(tasks: List<TaskExecution>, verbose: Boolean) {
        try {
            if (!::sessionPersistence.isInitialized) {
                if (verbose) {
                    println("Session persistence not initialized, skipping load")
                }
                return
            }
            
            val projects = tasks.map { it.project.artifactId }
            val sessionDataMap = sessionPersistence.loadBatchSession(projects)
            
            if (sessionDataMap.isNotEmpty()) {
                if (verbose) {
                    println("Loaded session data for ${sessionDataMap.size} projects")
                }
                
                // Apply loaded session data to tasks
                for (task in tasks) {
                    val sessionData = sessionDataMap[task.project.artifactId]
                    if (sessionData != null) {
                        applySessionDataToTask(task, sessionData, verbose)
                    }
                }
            } else {
                if (verbose) {
                    println("No previous session data found")
                }
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Failed to load previous session data: ${e.message}")
            }
        }
    }
    
    /**
     * Save session data to disk after execution
     */
    private fun saveSessionData(tasks: List<TaskExecution>, results: Map<String, TaskExecutionResult>, verbose: Boolean) {
        try {
            if (!::sessionPersistence.isInitialized) {
                if (verbose) {
                    println("Session persistence not initialized, skipping save")
                }
                return
            }
            
            val projects = tasks.map { it.project.artifactId }
            val sessionProperties = extractSessionProperties(tasks, results)
            
            sessionPersistence.saveBatchSession(projects, results.values.toList(), sessionProperties, null)
            
            if (verbose) {
                println("Saved session data for ${projects.size} projects to disk")
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Failed to save session data: ${e.message}")
            }
        }
    }
    
    /**
     * Apply loaded session data to a task
     */
    private fun applySessionDataToTask(task: TaskExecution, sessionData: EmbedderSessionPersistence.SessionData, verbose: Boolean) {
        try {
            // Apply build directories if available
            sessionData.buildDirectory?.let { buildDir ->
                task.properties["nx.build.directory"] = buildDir
            }
            sessionData.outputDirectory?.let { outputDir ->
                task.properties["nx.build.outputDirectory"] = outputDir
            }
            
            // Apply artifact information
            if (sessionData.artifacts.isNotEmpty()) {
                sessionData.artifacts.forEachIndexed { index, artifact ->
                    artifact["file"]?.let { file ->
                        task.properties["nx.artifact.$index.file"] = file
                    }
                    artifact["type"]?.let { type ->
                        task.properties["nx.artifact.$index.type"] = type
                    }
                }
                task.properties["nx.artifact.count"] = sessionData.artifacts.size.toString()
            }
            
            // Apply session properties
            sessionData.sessionProperties.forEach { (key: String, value: Any) ->
                if (key.startsWith("nx.") && value != null) {
                    task.properties[key] = value.toString()
                }
            }
            
            if (verbose) {
                println("Applied session data to task ${task.taskId}: ${sessionData.sessionProperties.size} properties, ${sessionData.artifacts.size} artifacts")
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Failed to apply session data to task ${task.taskId}: ${e.message}")
            }
        }
    }
    
    /**
     * Extract session properties from tasks and results
     */
    private fun extractSessionProperties(tasks: List<TaskExecution>, results: Map<String, TaskExecutionResult>): Map<String, Any> {
        val sessionProperties = mutableMapOf<String, Any>()
        
        // Extract task-level properties
        for (task in tasks) {
            val taskPrefix = "nx.task.${task.taskId}"
            sessionProperties["$taskPrefix.goals"] = task.goals.joinToString(",")
            sessionProperties["$taskPrefix.projectRoot"] = task.projectRoot ?: task.getEffectiveProjectRoot()
            sessionProperties["$taskPrefix.project.artifactId"] = task.project.artifactId ?: ""
            sessionProperties["$taskPrefix.project.groupId"] = task.project.groupId ?: ""
            sessionProperties["$taskPrefix.project.version"] = task.project.version ?: ""
            
            // Include custom task properties
            task.properties.forEach { (key, value) ->
                sessionProperties["$taskPrefix.property.$key"] = value
            }
        }
        
        // Extract result-level properties
        for ((taskId, result) in results) {
            val resultPrefix = "nx.result.$taskId"
            sessionProperties["$resultPrefix.success"] = result.success
            sessionProperties["$resultPrefix.duration"] = result.duration
            sessionProperties["$resultPrefix.goalCount"] = result.goalResults?.size ?: 0
            result.errorMessage?.let { error ->
                sessionProperties["$resultPrefix.errorMessage"] = error
            }
        }
        
        // Global session properties
        sessionProperties["nx.session.timestamp"] = System.currentTimeMillis()
        sessionProperties["nx.session.taskCount"] = tasks.size
        sessionProperties["nx.session.resultCount"] = results.size
        sessionProperties["nx.session.successfulTasks"] = results.values.count { it.success }
        sessionProperties["nx.session.failedTasks"] = results.values.count { !it.success }
        
        return sessionProperties
    }
    
    /**
     * Create a root Maven session for the workspace
     */
    private fun createRootMavenSession(
        executionRequest: MavenExecutionRequest,
        repositorySystemSession: RepositorySystemSession,
        verbose: Boolean
    ): MavenSession {
        return try {
            // Create an empty project list for the root session
            val emptyProjects = emptyList<MavenProject>()
            
            sessionFactory.createMavenSession(
                executionRequest,
                repositorySystemSession,
                emptyProjects,
                null
            )
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Failed to create root Maven session: ${e.message}")
            }
            throw RuntimeException("Failed to create root Maven session", e)
        }
    }
    

    /**
     * IntelliJ-style robust component lookup with error handling
     */
    private inline fun <reified T> getComponent(role: String): T {
        return try {
            plexusContainer.lookup(role) as T
        } catch (e: ComponentLookupException) {
            throw RuntimeException("Failed to lookup component $role: ${e.message}", e)
        }
    }
    
    /**
     * IntelliJ-style component lookup by class only
     */
    private inline fun <reified T> getComponent(clazz: Class<T>): T {
        return try {
            plexusContainer.lookup(clazz)
        } catch (e: ComponentLookupException) {
            throw RuntimeException("Failed to lookup component ${clazz.name}: ${e.message}", e)
        }
    }
    
    /**
     * Write results to JSON file
     */
    private fun writeResultsToFile(result: Any, outputFile: String, verbose: Boolean) {
        try {
            val json = gson.toJson(result)
            val file = File(outputFile)
            
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            
            // Write JSON to file
            file.writeText(json)
            
            if (verbose) {
                println("✅ Results written to: ${file.absolutePath}")
                println("📊 Result size: ${json.length} characters")
            }
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to write results to file '$outputFile': ${e.message}", e)
        }
    }

    /**
     * Cleanup Maven Embedder resources
     */
    private fun cleanupEmbedder() {
        try {
            // Dispose the Plexus container
            if (::plexusContainer.isInitialized) {
                plexusContainer.dispose()
            }
            sessionContext?.clearCaches()
        } catch (e: Exception) {
            System.err.println("Warning: Failed to cleanup embedder resources: ${e.message}")
        }
    }
}