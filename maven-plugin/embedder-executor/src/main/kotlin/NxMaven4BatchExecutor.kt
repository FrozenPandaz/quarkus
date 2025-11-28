import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.maven.cli.MavenCli
import org.apache.maven.execution.*
import org.apache.maven.Maven
import org.apache.maven.project.MavenProject
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.Model
import org.apache.maven.model.Build
import org.apache.maven.lifecycle.LifecycleExecutor
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem as AetherRepositorySystem
import org.eclipse.aether.repository.LocalRepository
import org.apache.maven.settings.Settings
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Maven 4.0 compatible batch executor using JSR-330 dependency injection.
 * This eliminates PlexusContainer dependencies and uses modern Maven 4.0 patterns.
 */
@Named
@Singleton
class NxMaven4BatchExecutor @Inject constructor(
    private val lifecycleExecutor: LifecycleExecutor,
    private val repositorySystem: AetherRepositorySystem,
    private val executionRequestPopulator: MavenExecutionRequestPopulator,
    private val sessionFactory: Maven4SessionFactory
) {

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
        
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 4) {
                System.err.println("Usage: java NxMaven4BatchExecutor <goals> <workspaceRoot> <projects> <outputFile> [verbose] [additional-properties...]")
                System.err.println("Example: java NxMaven4BatchExecutor \"compile,test\" \"/workspace\" \".,module1,module2\" \"/tmp/results.json\" true -DskipTests")
                exitProcess(1)
            }

            val goalsList = args[0]
            val workspaceRoot = args[1] 
            val projectsList = args[2]
            val outputFile = args[3]
            val verbose = if (args.size > 4) args[4].toBoolean() else true
            val additionalProperties = if (args.size > 5) args.slice(5 until args.size) else emptyList()

            try {
                // Create executor with dependency injection (this would be done by Maven 4.0 container)
                val executor = createExecutorWithDI(verbose)
                
                val goals = goalsList.split(",")
                val projects = projectsList.split(",")
                val result = executor.executeBatch(goals, workspaceRoot, projects, verbose, additionalProperties)
                
                // Write JSON result to output file
                writeResultsToFile(result, outputFile, verbose)
                
                // Exit with appropriate code
                exitProcess(if (result.values.all { it.success }) 0 else 1)
                
            } catch (e: Exception) {
                val errorResult = mapOf(
                    "error" to "Maven 4.0 batch executor failed: ${e.message}",
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
         * Create executor with dependency injection (placeholder for Maven 4.0 DI container)
         */
        private fun createExecutorWithDI(verbose: Boolean): NxMaven4BatchExecutor {
            // This would normally be handled by Maven 4.0's DI container
            // For now, create dependencies manually
            
            val lifecycleExecutor = createLifecycleExecutor()
            val repositorySystem = createRepositorySystem()
            val executionRequestPopulator = createExecutionRequestPopulator()
            val sessionFactory = Maven4SessionFactory(null, verbose)
            
            return NxMaven4BatchExecutor(
                lifecycleExecutor,
                repositorySystem,
                executionRequestPopulator,
                sessionFactory
            )
        }
        
        private fun createLifecycleExecutor(): LifecycleExecutor {
            // Placeholder - would be injected by Maven 4.0 container
            throw UnsupportedOperationException("LifecycleExecutor injection not yet implemented")
        }
        
        private fun createRepositorySystem(): AetherRepositorySystem {
            // Placeholder - would be injected by Maven 4.0 container
            throw UnsupportedOperationException("RepositorySystem injection not yet implemented")
        }
        
        private fun createExecutionRequestPopulator(): MavenExecutionRequestPopulator {
            // Placeholder - would be injected by Maven 4.0 container
            throw UnsupportedOperationException("MavenExecutionRequestPopulator injection not yet implemented")
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
    }

    private lateinit var repositorySystemSession: RepositorySystemSession
    private var sessionContext: EmbedderSessionContext? = null
    private lateinit var sessionPersistence: EmbedderSessionPersistence

    /**
     * Execute multiple Maven goals across multiple projects using Maven 4.0 APIs
     */
    fun executeBatch(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean, additionalProperties: List<String> = emptyList()): Map<String, TaskExecutionResult> {
        val totalStartTime = System.currentTimeMillis()
        
        if (verbose) {
            println("=== MAVEN 4.0 BATCH EXECUTOR PERFORMANCE TIMING ===")
            println("🚀 Starting Maven 4.0 batch execution at ${java.time.Instant.ofEpochMilli(totalStartTime)}")
            println("📋 Goals: ${goals.joinToString(", ")}")
            println("📂 Projects: ${projects.size} projects (${projects.joinToString(", ")})")
            println("📍 Workspace: $workspaceRoot")
            println()
        }
        
        return try {
            // 1. Initialize Maven 4.0 session and components
            val initStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT] Starting Maven 4.0 initialization...")
            }
            
            initializeMaven4Components(workspaceRoot, verbose, additionalProperties)
            
            val initDuration = System.currentTimeMillis() - initStartTime
            if (verbose) {
                println("✅ [INIT] Maven 4.0 initialization completed in ${initDuration}ms")
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
            
            // 4. Execute all tasks using Maven 4.0 lifecycle
            val taskExecutionStartTime = System.currentTimeMillis()
            if (verbose) {
                println("⚡ [TASK EXECUTION] Starting Maven 4.0 execution of ${tasks.size} tasks...")
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
                println("🏁 [SUMMARY] Maven 4.0 batch execution completed!")
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
                println("❌ [ERROR] Maven 4.0 batch execution failed after ${totalDuration}ms: ${e.message}")
                e.printStackTrace()
            }
            throw e
        }
    }

    /**
     * Initialize Maven 4.0 components using JSR-330 dependency injection
     */
    private fun initializeMaven4Components(workspaceRoot: String, verbose: Boolean, additionalProperties: List<String> = emptyList()) {
        try {
            if (verbose) {
                println("🔧 [MAVEN4-INIT] Initializing Maven 4.0 components...")
                println("🔧 [MAVEN4-INIT] Using JSR-330 dependency injection")
                println("🔧 [MAVEN4-INIT] No PlexusContainer required")
            }
            
            // Setup environment variables
            setupEnvironmentVariables(workspaceRoot)
            
            // Load Maven settings
            val settings = loadMavenSettings(verbose)
            
            // Create repository system session
            repositorySystemSession = createRepositorySystemSession(repositorySystem, settings, verbose)
            
            // Initialize session persistence
            sessionPersistence = EmbedderSessionPersistence(workspaceRoot, verbose)
            
            if (verbose) {
                println("✅ [MAVEN4-INIT] Maven 4.0 components initialized successfully")
                println("📁 Workspace root: $workspaceRoot")
                println("📦 Local repository: ${repositorySystemSession.localRepository.basedir}")
                println("💾 Session persistence directory: ${sessionPersistence.getSessionDirectory().absolutePath}")
            }
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Maven 4.0 components: ${e.message}", e)
        }
    }

    /**
     * Build task executions from goals and projects
     */
    private fun buildTaskExecutions(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean): List<TaskExecution> {
        val tasks = mutableListOf<TaskExecution>()
        
        try {
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
                    // Create MavenProject directly from POM data
                    val mavenProject = createMavenProjectFromPom(projectDir, projectPath, verbose)
                    
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
                }
            }
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to build task executions: ${e.message}", e)
        }
        
        return tasks
    }

    /**
     * Execute all tasks using Maven 4.0 lifecycle executor
     */
    private fun executeTasks(tasks: List<TaskExecution>, verbose: Boolean): Map<String, TaskExecutionResult> {
        val results = mutableMapOf<String, TaskExecutionResult>()
        
        if (tasks.isEmpty()) {
            if (verbose) {
                println("🚫 [MAVEN4-EXEC] No tasks to execute")
            }
            return results
        }
        
        if (verbose) {
            println("🚀 [MAVEN4-EXEC] Starting Maven 4.0 execution of ${tasks.size} tasks")
        }
        
        try {
            // Group tasks by goals for batch execution
            val goalGroups = tasks.groupBy { it.goals }
            
            // Execute each goal group using Maven 4.0 lifecycle
            for ((goalGroup, tasksInGroup) in goalGroups) {
                val groupStartTime = System.currentTimeMillis()
                
                if (verbose) {
                    println("🔄 [MAVEN4-GROUP] Executing goal group: [${goalGroup.joinToString(", ")}]")
                }
                
                val groupResults = executeMaven4Group(goalGroup, tasksInGroup, verbose)
                results.putAll(groupResults)
                
                val groupDuration = System.currentTimeMillis() - groupStartTime
                val groupSuccess = groupResults.values.all { it.success }
                
                if (verbose) {
                    println("✅ [MAVEN4-GROUP] Goal group completed in ${groupDuration}ms - ${if (groupSuccess) "SUCCESS" else "PARTIAL SUCCESS"}")
                }
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("❌ [MAVEN4-EXEC] Maven 4.0 execution failed: ${e.message}")
                e.printStackTrace()
            }
            
            // Create failed results for all tasks
            tasks.forEach { task ->
                val failedResult = TaskExecutionResult(
                    taskId = task.taskId,
                    success = false,
                    duration = 0,
                    goalResults = emptyList(),
                    errorMessage = "Maven 4.0 execution failed: ${e.message}"
                )
                results[task.taskId] = failedResult
            }
        }
        
        return results
    }

    /**
     * Execute a group of tasks using Maven 4.0 lifecycle executor
     */
    private fun executeMaven4Group(goals: List<String>, tasks: List<TaskExecution>, verbose: Boolean): Map<String, TaskExecutionResult> {
        val results = mutableMapOf<String, TaskExecutionResult>()
        val groupStartTime = System.currentTimeMillis()
        
        if (verbose) {
            println("🔧 [MAVEN4-LIFECYCLE] Setting up Maven 4.0 lifecycle execution")
            println("🎯 [MAVEN4-LIFECYCLE] Goals: ${goals.joinToString(", ")}")
        }
        
        try {
            // Create Maven execution request
            val allProjects = tasks.map { it.project }
            val executionRequest = DefaultMavenExecutionRequest().apply {
                setGoals(goals)
                setLoggingLevel(if (verbose) MavenExecutionRequest.LOGGING_LEVEL_DEBUG else MavenExecutionRequest.LOGGING_LEVEL_INFO)
                setInteractiveMode(false)
                setDegreeOfConcurrency(Runtime.getRuntime().availableProcessors())
            }
            
            // Create Maven 4.0 session
            val maven4Session = sessionFactory.createMavenSession(
                executionRequest,
                repositorySystemSession,
                allProjects,
                null
            )
            
            if (verbose) {
                println("🔄 [MAVEN4-LIFECYCLE] Created Maven 4.0 session with ${allProjects.size} projects")
            }
            
            // Execute using Maven 4.0 lifecycle executor (JSR-330 injected)
            val reactorStartTime = System.currentTimeMillis()
            
            // Capture output
            val outputCapture = ByteArrayOutputStream()
            val errorCapture = ByteArrayOutputStream()
            val originalOut = System.out
            val originalErr = System.err
            
            var reactorSuccess = false
            
            try {
                System.setOut(PrintStream(outputCapture))
                System.setErr(PrintStream(errorCapture))
                
                if (verbose) {
                    originalOut.println("🔄 [MAVEN4-LIFECYCLE] Executing Maven 4.0 lifecycle...")
                }
                
                // Execute the lifecycle using JSR-330 injected component
                lifecycleExecutor.execute(maven4Session)
                
                // Check execution result
                val hasExceptions = MavenUtils.hasSessionExceptions(maven4Session)
                reactorSuccess = !hasExceptions
                
                if (verbose) {
                    originalOut.println("🔄 [MAVEN4-LIFECYCLE] Execution success: $reactorSuccess")
                }
                
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }
            
            val reactorDuration = System.currentTimeMillis() - reactorStartTime
            val outputLines = outputCapture.toString().lines().filter { it.isNotBlank() }
            val errorLines = errorCapture.toString().lines().filter { it.isNotBlank() }
            
            if (verbose) {
                if (reactorSuccess) {
                    println("✅ [MAVEN4-LIFECYCLE] Maven 4.0 lifecycle completed successfully in ${reactorDuration}ms")
                } else {
                    println("❌ [MAVEN4-LIFECYCLE] Maven 4.0 lifecycle failed in ${reactorDuration}ms")
                }
            }
            
            // Create individual task results
            tasks.forEach { task ->
                val taskDuration = reactorDuration / tasks.size
                
                val goalResults = goals.map { goal ->
                    GoalExecutionResult(
                        goal = goal,
                        success = reactorSuccess,
                        duration = taskDuration / goals.size,
                        output = if (reactorSuccess) outputLines.take(20) else emptyList(),
                        errors = if (!reactorSuccess) errorLines.take(20) else emptyList(),
                        exitCode = if (reactorSuccess) 0 else 1
                    )
                }
                
                val taskResult = TaskExecutionResult(
                    taskId = task.taskId,
                    success = reactorSuccess,
                    duration = taskDuration,
                    goalResults = goalResults,
                    artifacts = emptyList(),
                    dependencies = emptyList(), // Simplified for Maven 4.0 demo
                    executionContext = mapOf(
                        "projectPath" to (task.projectRoot ?: ""),
                        "projectArtifactId" to (task.project.artifactId ?: ""),
                        "goalCount" to goals.size,
                        "maven4Execution" to true,
                        "lifecycleExecutor" to lifecycleExecutor.javaClass.name
                    )
                )
                
                results[task.taskId] = taskResult
                
                if (verbose) {
                    println("📋 [TASK-RESULT] ${task.taskId}: ${if (taskResult.success) "SUCCESS" else "FAILURE"} (${taskDuration}ms)")
                }
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("❌ [MAVEN4-LIFECYCLE] Maven 4.0 lifecycle execution failed: ${e.message}")
                e.printStackTrace()
            }
            
            // Create failed results for all tasks in this group
            tasks.forEach { task ->
                val failedResult = TaskExecutionResult(
                    taskId = task.taskId,
                    success = false,
                    duration = 0,
                    goalResults = goals.map { goal ->
                        GoalExecutionResult(
                            goal = goal,
                            success = false,
                            duration = 0,
                            output = emptyList(),
                            errors = listOf("Maven 4.0 lifecycle execution failed: ${e.message}"),
                            exitCode = 1
                        )
                    },
                    errorMessage = "Maven 4.0 lifecycle execution failed: ${e.message}"
                )
                results[task.taskId] = failedResult
            }
        }
        
        return results
    }

    /**
     * Create a MavenProject from POM file
     */
    private fun createMavenProjectFromPom(
        projectDir: File,
        projectPath: String,
        verbose: Boolean
    ): MavenProject {
        val pomFile = File(projectDir, "pom.xml")
        
        if (!pomFile.exists()) {
            throw RuntimeException("POM file not found: ${pomFile.absolutePath}")
        }
        
        try {
            val modelReader = MavenXpp3Reader()
            val model: Model = pomFile.inputStream().use { inputStream ->
                modelReader.read(inputStream)
            }
            
            // Create a minimal model with essential data
            val minimalModel = Model().apply {
                groupId = model.groupId ?: model.parent?.groupId ?: "unknown"
                artifactId = model.artifactId ?: "unknown"
                version = model.version ?: model.parent?.version ?: "1.0.0"
                packaging = model.packaging ?: "jar"
                name = model.name ?: artifactId
                description = model.description
                
                // Set up build configuration
                build = Build().apply {
                    sourceDirectory = File(projectDir, "src/main/java").absolutePath
                    testSourceDirectory = File(projectDir, "src/test/java").absolutePath
                    outputDirectory = File(projectDir, "target/classes").absolutePath
                    testOutputDirectory = File(projectDir, "target/test-classes").absolutePath
                    directory = File(projectDir, "target").absolutePath
                }
            }
            
            // Create MavenProject
            val mavenProject = MavenProject(minimalModel)
            mavenProject.file = pomFile
            
            // Set basedir
            try {
                val setBasedirMethod = MavenProject::class.java.getMethod("setBasedir", File::class.java)
                setBasedirMethod.invoke(mavenProject, projectDir)
            } catch (e: Exception) {
                if (verbose) {
                    println("Warning: Could not set basedir for ${model.artifactId}: ${e.message}")
                }
            }
            
            if (verbose) {
                println("Created Maven 4.0 project:")
                println("  - GroupId: ${mavenProject.groupId}")
                println("  - ArtifactId: ${mavenProject.artifactId}")
                println("  - Version: ${mavenProject.version}")
                println("  - Packaging: ${mavenProject.packaging}")
                println("  - BaseDir: ${mavenProject.basedir}")
            }
            
            return mavenProject
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to create MavenProject for $projectPath: ${e.message}", e)
        }
    }

    /**
     * Load Maven settings from user and global settings files
     */
    private fun loadMavenSettings(verbose: Boolean): Settings {
        val settingsBuilder = DefaultSettingsBuilderFactory().newInstance()
        val settingsRequest = DefaultSettingsBuildingRequest()
        
        // Set global settings file
        val globalSettingsFile = File(System.getProperty("maven.home", "/opt/apache-maven-4.0") + "/conf/settings.xml")
        if (globalSettingsFile.exists()) {
            settingsRequest.globalSettingsFile = globalSettingsFile
            if (verbose) {
                println("Loading global settings from: ${globalSettingsFile.absolutePath}")
            }
        }
        
        // Set user settings file
        val userSettingsFile = File(System.getProperty("user.home") + "/.m2/settings.xml")
        if (userSettingsFile.exists()) {
            settingsRequest.userSettingsFile = userSettingsFile
            if (verbose) {
                println("Loading user settings from: ${userSettingsFile.absolutePath}")
            }
        }
        
        try {
            val settingsResult = settingsBuilder.build(settingsRequest)
            return settingsResult.effectiveSettings
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Failed to load Maven settings: ${e.message}")
            }
            return Settings()
        }
    }

    /**
     * Create repository system session
     */
    private fun createRepositorySystemSession(
        repositorySystem: AetherRepositorySystem,
        settings: Settings,
        verbose: Boolean
    ): RepositorySystemSession {
        val session = DefaultRepositorySystemSession()
        
        // Set local repository
        val localRepoPath = settings.localRepository ?: "${System.getProperty("user.home")}/.m2/repository"
        val localRepo = LocalRepository(localRepoPath)
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, localRepo)
        
        if (verbose) {
            println("Local repository: ${localRepoPath}")
        }
        
        return session
    }

    /**
     * Setup environment variables for Maven execution
     */
    private fun setupEnvironmentVariables(workspaceRoot: String) {
        System.setProperty("maven.multiModuleProjectDirectory", workspaceRoot)
        if (System.getProperty("maven.home") == null) {
            System.setProperty("maven.home", "/opt/apache-maven-4.0")
        }
    }

    /**
     * Load previous session data for all tasks
     */
    private fun loadPreviousSessionData(tasks: List<TaskExecution>, verbose: Boolean) {
        if (verbose) {
            println("📂 [SESSION] Loading previous session data for ${tasks.size} tasks")
        }
        
        tasks.forEach { task ->
            try {
                val sessionData = sessionPersistence.loadSession(task.taskId)
                sessionData?.let { data ->
                    applySessionDataToTask(task, data, verbose)
                }
            } catch (e: Exception) {
                if (verbose) {
                    println("⚠️  [SESSION] Failed to load session data for ${task.taskId}: ${e.message}")
                }
            }
        }
    }

    /**
     * Save session data for all tasks
     */
    private fun saveSessionData(tasks: List<TaskExecution>, results: Map<String, TaskExecutionResult>, verbose: Boolean) {
        if (verbose) {
            println("💾 [SESSION] Saving session data for ${tasks.size} tasks")
        }
        
        tasks.forEach { task ->
            try {
                val result = results[task.taskId]
                if (result != null) {
                    val sessionData = EmbedderSessionPersistence.SessionData(
                        sessionId = task.taskId,
                        timestamp = System.currentTimeMillis(),
                        projects = listOf(task.project.artifactId ?: ""),
                        goals = task.goals,
                        artifacts = emptyList(),
                        dependencies = emptyList(),
                        properties = mapOf(
                            "maven4Execution" to "true",
                            "lifecycleExecutor" to lifecycleExecutor.javaClass.name
                        ),
                        buildDirectory = task.project.build?.directory,
                        outputDirectory = task.project.build?.outputDirectory,
                        sessionProperties = mapOf(
                            "success" to result.success,
                            "duration" to result.duration
                        )
                    )
                    sessionPersistence.saveSession(sessionData)
                }
            } catch (e: Exception) {
                if (verbose) {
                    println("⚠️  [SESSION] Failed to save session data for ${task.taskId}: ${e.message}")
                }
            }
        }
    }

    /**
     * Apply session data to a task
     */
    private fun applySessionDataToTask(task: TaskExecution, sessionData: EmbedderSessionPersistence.SessionData, verbose: Boolean) {
        if (verbose) {
            println("📂 [SESSION] Applying session data to task: ${task.taskId}")
        }
        
        sessionData.properties.forEach { (key, value) ->
            task.properties[key] = value
        }
        
        if (verbose) {
            println("✅ [SESSION] Applied ${sessionData.properties.size} session properties to ${task.taskId}")
        }
    }
}