import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.maven.cli.MavenCli
import org.apache.maven.cli.CliRequest
import org.apache.maven.execution.*
// import org.apache.maven.execution.DefaultMavenSession // Not available in this Maven version
import org.apache.maven.Maven
// Removed ModelBuildingRequest import - no longer needed with direct construction
import org.apache.maven.project.MavenProject
// Removed ProjectBuildingRequest imports - no longer needed with direct construction
// Removed ProjectBuilder and RepositorySystem imports - no longer needed with direct construction
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.Model
import org.apache.maven.model.Build
import org.apache.maven.artifact.repository.ArtifactRepository
// Removed PluginManager import - handled differently in Maven 4.0
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
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.component.repository.exception.ComponentLookupException
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.PlexusConstants
import com.google.inject.Injector
import com.google.inject.AbstractModule
import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory
// Removed MavenRepositorySystem import - no longer needed with direct construction
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
    private lateinit var plexusContainer: org.codehaus.plexus.PlexusContainer
    private lateinit var mavenCli: MavenCli
    private var sessionContext: EmbedderSessionContext? = null
    private lateinit var sessionPersistence: EmbedderSessionPersistence
    private lateinit var sessionFactory: Maven4SessionFactory
    private lateinit var repositorySystemSession: RepositorySystemSession

    @JvmStatic
    fun main(args: Array<String>) {
        // Enhanced error handling to debug the exit code 1 issue
        try {
            // Write to both stdout and stderr to ensure visibility
            val debugMsg1 = "EMBEDDER DEBUG: Main method started with ${args.size} arguments"
            val debugMsg2 = "EMBEDDER DEBUG: Arguments: ${args.joinToString(", ") { "\"$it\"" }}"
            val debugMsg3 = "EMBEDDER DEBUG: Classpath: ${System.getProperty("java.class.path")}"
            val debugMsg4 = "EMBEDDER DEBUG: Maven multimodule dir: ${System.getProperty("maven.multiModuleProjectDirectory")}"
            
            System.err.println(debugMsg1)
            System.err.println(debugMsg2)
            System.err.println(debugMsg3)
            System.err.println(debugMsg4)
            
            // Also write to stdout to ensure PseudoTerminal captures it
            System.out.println(debugMsg1)
            System.out.println(debugMsg2)
            System.out.println(debugMsg3)  
            System.out.println(debugMsg4)
            
            if (args.size < 4) {
                System.err.println("EMBEDDER ERROR: Insufficient arguments - need at least 4, got ${args.size}")
                System.err.println("Usage: java NxMavenEmbedderBatchExecutor <goals> <workspaceRoot> <projects> <outputFile> [verbose] [additional-properties...]")
                System.err.println("Example: java NxMavenEmbedderBatchExecutor \"compile,test\" \"/workspace\" \".,module1,module2\" \"/tmp/results.json\" true -DskipTests")
                exitProcess(1)
            }

            val goalsList = args[0]
            val workspaceRoot = args[1] 
            val projectsList = args[2]
            val outputFile = args[3]
            val verbose = if (args.size > 4) args[4].toBoolean() else true
            val additionalProperties = if (args.size > 5) args.slice(5 until args.size) else emptyList()

            val parseMsg = "EMBEDDER DEBUG: Parsed arguments:\n  Goals: $goalsList\n  Workspace: $workspaceRoot\n  Projects: $projectsList\n  Output: $outputFile\n  Verbose: $verbose\n  Additional props: $additionalProperties"
            System.err.println(parseMsg)
            System.out.println(parseMsg)

            try {
                val goals = goalsList.split(",")
                val projects = projectsList.split(",")
                
                val startMsg = "EMBEDDER DEBUG: Starting executeBatch..."
                System.err.println(startMsg)
                System.out.println(startMsg)
                
                val result = executeBatch(goals, workspaceRoot, projects, verbose, additionalProperties)
                
                val completedMsg = "EMBEDDER DEBUG: executeBatch completed, writing results..."
                System.err.println(completedMsg)
                System.out.println(completedMsg)
                // Write JSON result to output file
                writeResultsToFile(result, outputFile, verbose)
                
                val allSuccess = result.values.all { it.success }
                System.err.println("EMBEDDER DEBUG: All tasks successful: $allSuccess")
                
                // Exit with appropriate code
                exitProcess(if (allSuccess) 0 else 1)
                
            } catch (e: Exception) {
                System.err.println("EMBEDDER ERROR: Exception in main execution: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                
                val errorResult = mapOf(
                    "error" to "Embedder batch executor failed: ${e.message}",
                    "success" to false,
                    "exceptionType" to e.javaClass.name,
                    "stackTrace" to e.stackTrace.take(10).map { it.toString() }
                )
                
                // Write error to output file if possible, otherwise stderr
                try {
                    writeResultsToFile(errorResult, outputFile, verbose)
                    System.err.println("EMBEDDER DEBUG: Error result written to output file")
                } catch (writeError: Exception) {
                    System.err.println("EMBEDDER ERROR: Failed to write error to output file: ${writeError.message}")
                    System.err.println("EMBEDDER ERROR: Original error JSON: ${gson.toJson(errorResult)}")
                }
                exitProcess(1)
            }
            
        } catch (e: Throwable) {
            System.err.println("EMBEDDER FATAL: Unhandled throwable in main: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    /**
     * Execute multiple Maven goals across multiple projects using Maven Embedder API
     */
    fun executeBatch(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean, additionalProperties: List<String> = emptyList()): Map<String, TaskExecutionResult> {
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
            
            initializeEmbedder(workspaceRoot, verbose, additionalProperties)
            
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
    private fun initializeEmbedder(workspaceRoot: String, verbose: Boolean, additionalProperties: List<String> = emptyList()) {
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
                // Create ClassWorld with proper realm setup (Maven's approach)
                val classWorld = ClassWorld("plexus.core", Thread.currentThread().contextClassLoader)
                
                // Get the core realm (mimic Maven's approach)
                val coreRealm = classWorld.getClassRealm("plexus.core")
                    ?: classWorld.realms.iterator().next()
                
                // Create Plexus container with Eclipse Sisu (exactly like Maven does)
                val configuration = DefaultContainerConfiguration()
                    .setAutoWiring(true)
                    .setJSR250Lifecycle(true)
                    .setClassPathScanning(PlexusConstants.SCANNING_ON)
                    .setComponentVisibility(PlexusConstants.REALM_VISIBILITY)
                    .setClassWorld(classWorld)
                    .setRealm(coreRealm)
                    .setName("maven")
                
                // Create Plexus container with Eclipse Sisu and AbstractModule (Maven's exact approach)
                plexusContainer = DefaultPlexusContainer(configuration, object : AbstractModule() {
                    override fun configure() {
                        // Bind ILoggerFactory - Maven binds this for dependency injection
                        val slf4jLoggerFactory = LoggerFactory.getILoggerFactory()
                        bind(ILoggerFactory::class.java).toInstance(slf4jLoggerFactory)
                        
                        // TODO: Add CoreExports binding when we have access to the proper implementation
                    }
                })
                
                if (verbose) {
                    println("✅ [INIT-CONTAINER] Eclipse Sisu injector created successfully")
                }
                
                val containerDuration = System.currentTimeMillis() - containerStartTime
                if (verbose) {
                    println("✅ [INIT-CONTAINER] Maven container initialized in ${containerDuration}ms")
                }
                
            } catch (e: Exception) {
                val containerDuration = System.currentTimeMillis() - containerStartTime
                if (verbose) {
                    println("❌ [INIT-CONTAINER] Failed to initialize Eclipse Sisu injector in ${containerDuration}ms")
                }
                throw RuntimeException("Failed to initialize Eclipse Sisu injector: ${e.message}", e)
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
            val executionRequestPopulator = getComponent<MavenExecutionRequestPopulator>()
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
                
                // Add additional properties from command line
                additionalProperties.forEach { property ->
                    if (property.startsWith("-D")) {
                        val propString = property.substring(2)
                        val eqIndex = propString.indexOf('=')
                        if (eqIndex > 0) {
                            val key = propString.substring(0, eqIndex)
                            val value = propString.substring(eqIndex + 1)
                            systemProperties.setProperty(key, value)
                        } else {
                            systemProperties.setProperty(propString, "true")
                        }
                        if (verbose) {
                            println("🔧 [PROPERTY] Added system property: $property")
                        }
                    }
                }
                
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
            val aetherRepositorySystem = getComponent<AetherRepositorySystem>()
            repositorySystemSession = createRepositorySystemSession(aetherRepositorySystem, settings, verbose)
            val repoDuration = System.currentTimeMillis() - repoStartTime
            if (verbose) {
                println("✅ [INIT-REPO] Repository system initialized in ${repoDuration}ms")
            }
            
            // 9. Initialize Maven session factory (Maven 4.0 compatible)
            val factoryStartTime = System.currentTimeMillis()
            if (verbose) {
                println("🔧 [INIT-FACTORY] Creating Maven 4.0 session factory...")
            }
            sessionFactory = Maven4SessionFactory(null, verbose)
            val factoryDuration = System.currentTimeMillis() - factoryStartTime
            if (verbose) {
                println("✅ [INIT-FACTORY] Maven 4.0 session factory created in ${factoryDuration}ms")
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
     * Create a MavenProject directly from project information, bypassing POM parsing
     * This approach eliminates the need for ProjectBuilder and RepositorySystem dependencies
     */
    private fun createMavenProjectFromGraphData(
        projectDir: File,
        projectPath: String,
        verbose: Boolean
    ): MavenProject {
        val pomFile = File(projectDir, "pom.xml")
        
        if (!pomFile.exists()) {
            throw RuntimeException("POM file not found: ${pomFile.absolutePath}")
        }
        
        try {
            // Parse only the essential information from POM
            val modelReader = MavenXpp3Reader()
            val model: Model = pomFile.inputStream().use { inputStream ->
                modelReader.read(inputStream)
            }
            
            // Create a new Model instance with the essential data
            val minimalModel = Model().apply {
                groupId = model.groupId ?: model.parent?.groupId ?: "unknown"
                artifactId = model.artifactId ?: "unknown"
                version = model.version ?: model.parent?.version ?: "1.0.0"
                packaging = model.packaging ?: "jar"
                name = model.name ?: artifactId
                description = model.description
                
                // Set up build configuration with standard directory layout
                build = Build().apply {
                    sourceDirectory = File(projectDir, "src/main/java").absolutePath
                    testSourceDirectory = File(projectDir, "src/test/java").absolutePath
                    outputDirectory = File(projectDir, "target/classes").absolutePath
                    testOutputDirectory = File(projectDir, "target/test-classes").absolutePath
                    directory = File(projectDir, "target").absolutePath
                }
            }
            
            // Create MavenProject using the minimal model
            val mavenProject = MavenProject(minimalModel)
            
            // Set the file and basedir through the model (this is the correct way)
            mavenProject.file = pomFile
            // Use reflection to set basedir since it's marked as val but has a setter
            try {
                val setBasedirMethod = MavenProject::class.java.getMethod("setBasedir", File::class.java)
                setBasedirMethod.invoke(mavenProject, projectDir)
            } catch (e: Exception) {
                // If reflection fails, the basedir will be derived from the file automatically
                if (verbose) {
                    println("Warning: Could not set basedir via reflection for ${model.artifactId}: ${e.message}")
                }
            }
            
            if (verbose) {
                println("Created MavenProject from graph data:")
                println("  - GroupId: ${mavenProject.groupId}")
                println("  - ArtifactId: ${mavenProject.artifactId}")
                println("  - Version: ${mavenProject.version}")
                println("  - Packaging: ${mavenProject.packaging}")
                println("  - BaseDir: ${mavenProject.basedir}")
            }
            
            return mavenProject
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to create MavenProject from graph data for $projectPath: ${e.message}", e)
        }
    }

    /**
     * Build task executions from goals and projects
     */
    private fun buildTaskExecutions(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean): List<TaskExecution> {
        val tasks = mutableListOf<TaskExecution>()
        
        try {
            // Use direct MavenProject creation from graph data
            // This eliminates the need for ProjectBuilder and RepositorySystem dependencies
            
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
                    // Create MavenProject directly from graph data
                    val mavenProject = createMavenProjectFromGraphData(projectDir, projectPath, verbose)
                    
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
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to build task executions: ${e.message}", e)
        }
        
        return tasks
    }

    /**
     * Execute all tasks using Maven Embedder in a single batch operation
     * This leverages Maven's reactor to execute all goals across all projects efficiently
     */
    private fun executeTasks(tasks: List<TaskExecution>, verbose: Boolean): Map<String, TaskExecutionResult> {
        val results = mutableMapOf<String, TaskExecutionResult>()
        val batchStartTime = System.currentTimeMillis()
        
        if (tasks.isEmpty()) {
            if (verbose) {
                println("🚫 [BATCH-EXEC] No tasks to execute")
            }
            return results
        }
        
        if (verbose) {
            println("🚀 [BATCH-EXEC] Starting batch execution of ${tasks.size} tasks")
            println("📋 [BATCH-EXEC] Task overview:")
            tasks.forEachIndexed { index, task ->
                println("    ${index + 1}. ${task.taskId} -> Goals: ${task.goals.joinToString(", ")} | Project: ${task.project.artifactId}")
            }
        }
        
        try {
            // Group tasks by goals to optimize batch execution
            val goalGroups = tasks.groupBy { it.goals }
            
            if (verbose) {
                println("📊 [BATCH-EXEC] Organized into ${goalGroups.size} goal groups:")
                goalGroups.forEach { (goals, tasksInGroup) ->
                    println("    Goals [${goals.joinToString(", ")}] -> ${tasksInGroup.size} projects")
                }
            }
            
            // Execute each goal group as a batch
            for ((goalGroup, tasksInGroup) in goalGroups) {
                val groupStartTime = System.currentTimeMillis()
                
                if (verbose) {
                    println("🔄 [BATCH-GROUP] Executing goal group: [${goalGroup.joinToString(", ")}]")
                    println("📦 [BATCH-GROUP] Projects in this group: ${tasksInGroup.map { it.project.artifactId }.joinToString(", ")}")
                }
                
                val groupResults = executeBatchGroup(goalGroup, tasksInGroup, verbose)
                results.putAll(groupResults)
                
                val groupDuration = System.currentTimeMillis() - groupStartTime
                val groupSuccess = groupResults.values.all { it.success }
                
                if (verbose) {
                    println("✅ [BATCH-GROUP] Goal group completed in ${groupDuration}ms - ${if (groupSuccess) "SUCCESS" else "PARTIAL SUCCESS"}")
                    println("📈 [BATCH-GROUP] Results: ${groupResults.values.count { it.success }}/${groupResults.size} tasks successful")
                }
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("❌ [BATCH-EXEC] Batch execution failed: ${e.message}")
                e.printStackTrace()
            }
            
            // Create failed results for all tasks
            tasks.forEach { task ->
                val failedResult = TaskExecutionResult(
                    taskId = task.taskId,
                    success = false,
                    duration = 0,
                    goalResults = emptyList(),
                    errorMessage = "Batch execution failed: ${e.message}"
                )
                results[task.taskId] = failedResult
                sessionContext?.storeExecutionResult(task.taskId, failedResult)
            }
        }
        
        val batchDuration = System.currentTimeMillis() - batchStartTime
        val batchSuccess = results.values.all { it.success }
        
        if (verbose) {
            println("🏁 [BATCH-EXEC] Batch execution completed in ${batchDuration}ms")
            println("📊 [BATCH-EXEC] Final results: ${results.values.count { it.success }}/${results.size} tasks successful")
            println("🎯 [BATCH-EXEC] Overall batch status: ${if (batchSuccess) "SUCCESS" else "PARTIAL SUCCESS"}")
        }
        
        return results
    }
    
    /**
     * Execute a group of tasks with the same goals using Maven reactor
     */
    private fun executeBatchGroup(goals: List<String>, tasks: List<TaskExecution>, verbose: Boolean): Map<String, TaskExecutionResult> {
        val results = mutableMapOf<String, TaskExecutionResult>()
        val groupStartTime = System.currentTimeMillis()
        
        if (verbose) {
            println("🔧 [REACTOR] Setting up Maven reactor for batch execution")
            println("🎯 [REACTOR] Goals: ${goals.joinToString(", ")}")
            println("📦 [REACTOR] Projects: ${tasks.map { "${it.project.groupId}:${it.project.artifactId}" }.joinToString(", ")}")
        }
        
        try {
            // Create Maven execution request for all projects in this group
            val allProjects = tasks.map { it.project }
            val batchRequest = DefaultMavenExecutionRequest().apply {
                setBaseDirectory(File(sessionContext?.mavenSession?.request?.baseDirectory?.toString() ?: "."))
                setPom(File(sessionContext?.mavenSession?.request?.baseDirectory?.toString() ?: ".", "pom.xml"))
                setGoals(goals)
                setLoggingLevel(if (verbose) MavenExecutionRequest.LOGGING_LEVEL_DEBUG else MavenExecutionRequest.LOGGING_LEVEL_INFO)
                setInteractiveMode(false)
                
                // Copy system properties from original request to batch request
                sessionContext?.mavenSession?.request?.systemProperties?.let { originalProps ->
                    originalProps.forEach { (key, value) ->
                        systemProperties.setProperty(key.toString(), value.toString())
                    }
                }
                
                // Enable parallel execution
                setDegreeOfConcurrency(Runtime.getRuntime().availableProcessors())
            }
            
            if (verbose) {
                println("⚙️  [REACTOR] Batch request configuration:")
                println("    Base directory: ${batchRequest.baseDirectory}")
                println("    Goals: ${batchRequest.goals.joinToString(", ")}")
                println("    Parallel threads: ${batchRequest.getDegreeOfConcurrency()}")
                println("    Logging level: ${batchRequest.loggingLevel}")
            }
            
            if (verbose) {
                println("🔧 [REACTOR] About to create Maven session for batch execution...")
                println("🔧 [REACTOR] Session factory: ${sessionFactory.javaClass.name}")
                println("🔧 [REACTOR] Projects count: ${allProjects.size}")
            }
            
            // Create Maven session for the entire batch
            val batchSession = sessionFactory.createMavenSession(
                batchRequest,
                repositorySystemSession,
                allProjects,
                null // No current project for batch execution
            )
            
            if (verbose) {
                println("🔧 [REACTOR] Successfully created Maven session")
            }
            
            if (verbose) {
                println("🔄 [REACTOR] Created Maven session with ${allProjects.size} projects in reactor")
                println("📋 [REACTOR] Reactor project list:")
                allProjects.forEachIndexed { index, project ->
                    println("    ${index + 1}. ${project.groupId}:${project.artifactId}:${project.version} (${project.packaging})")
                }
            }
            
            // Execute all goals for all projects using Maven's reactor
            val reactorStartTime = System.currentTimeMillis()
            
            if (verbose) {
                println("🚀 [REACTOR] Starting Maven reactor execution...")
                println("🔧 [REACTOR] About to get LifecycleExecutor component...")
            }
            
            try {
                if (verbose) {
                    println("🔧 [REACTOR] Inside try block, getting LifecycleExecutor...")
                }
                
                // Get lifecycle executor for proper Maven goal execution
                val lifecycleExecutor = getComponent<LifecycleExecutor>()
                
                if (verbose) {
                    println("🔧 [REACTOR] Successfully got LifecycleExecutor: ${lifecycleExecutor != null}")
                }
                
                if (verbose) {
                    println("🔧 [REACTOR] Using LifecycleExecutor: ${lifecycleExecutor.javaClass.name}")
                }
                
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
                        originalOut.println("🔄 [REACTOR] Executing reactor with ${goals.size} goals across ${allProjects.size} projects")
                        originalOut.println("🔄 [REACTOR] Session goals: ${batchSession.goals}")
                        originalOut.println("🔄 [REACTOR] Session projects: ${batchSession.projects.map { "${it.groupId}:${it.artifactId}" }}")
                        originalOut.println("🔄 [REACTOR] About to call lifecycleExecutor.execute()...")
                    }
                    
                    // Execute the reactor
                    lifecycleExecutor.execute(batchSession)
                    
                    if (verbose) {
                        originalOut.println("🔄 [REACTOR] lifecycleExecutor.execute() completed")
                    }
                    
                    // Check reactor execution result
                    val hasExceptions = MavenUtils.hasSessionExceptions(batchSession)
                    reactorSuccess = !hasExceptions
                    
                    if (verbose) {
                        originalOut.println("🔄 [REACTOR] Session has exceptions: $hasExceptions")
                        originalOut.println("🔄 [REACTOR] Reactor success: $reactorSuccess")
                        if (hasExceptions) {
                            originalOut.println("🔄 [REACTOR] Session exceptions: ${batchSession.result.exceptions}")
                        }
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
                        println("✅ [REACTOR] Maven reactor completed successfully in ${reactorDuration}ms")
                    } else {
                        println("❌ [REACTOR] Maven reactor failed in ${reactorDuration}ms")
                    }
                    
                    println("📊 [REACTOR] Output summary:")
                    println("    Output lines: ${outputLines.size}")
                    println("    Error lines: ${errorLines.size}")
                    
                    if (errorLines.isNotEmpty() && verbose) {
                        println("🚨 [REACTOR] Error output preview:")
                        errorLines.take(10).forEach { line ->
                            println("    ERROR: $line")
                        }
                    }
                }
                
                // Create individual task results from reactor execution
                tasks.forEach { task ->
                    val taskDuration = reactorDuration / tasks.size // Approximate per-task duration
                    
                    val goalResults = goals.map { goal ->
                        GoalExecutionResult(
                            goal = goal,
                            success = reactorSuccess,
                            duration = taskDuration / goals.size,
                            output = if (reactorSuccess) outputLines.take(20) else emptyList(), // Limit output size
                            errors = if (!reactorSuccess) errorLines.take(20) else emptyList(),
                            exitCode = if (reactorSuccess) 0 else 1
                        )
                    }
                    
                    // Calculate dependencies for this task
                    if (verbose) {
                        println("🔗 [DEPS] Starting dependency calculation for task: ${task.taskId}")
                    }
                    val taskDependencies = calculateTaskDependencies(task, allProjects, verbose)
                    if (verbose) {
                        println("🔗 [DEPS] Completed dependency calculation for ${task.taskId}: ${taskDependencies.size} dependencies found")
                    }
                    
                    val taskResult = TaskExecutionResult(
                        taskId = task.taskId,
                        success = reactorSuccess,
                        duration = taskDuration,
                        goalResults = goalResults,
                        artifacts = emptyList(), // TODO: Extract from reactor if needed
                        dependencies = taskDependencies,
                        executionContext = mapOf(
                            "projectPath" to (task.projectRoot ?: ""),
                            "projectArtifactId" to (task.project.artifactId ?: ""),
                            "goalCount" to goals.size,
                            "reactorExecution" to true,
                            "reactorProjectCount" to allProjects.size
                        )
                    )
                    
                    results[task.taskId] = taskResult
                    sessionContext?.storeExecutionResult(task.taskId, taskResult)
                    
                    if (verbose) {
                        println("📋 [TASK-RESULT] ${task.taskId}: ${if (taskResult.success) "SUCCESS" else "FAILURE"} (${taskDuration}ms)")
                    }
                }
                
            } catch (e: Exception) {
                if (verbose) {
                    println("❌ [REACTOR] Lifecycle executor failed: ${e.message}")
                    println("❌ [REACTOR] Exception type: ${e.javaClass.name}")
                    println("❌ [REACTOR] Exception stack trace:")
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
                                errors = listOf("Reactor execution failed: ${e.message}"),
                                exitCode = 1
                            )
                        },
                        errorMessage = "Reactor execution failed: ${e.message}"
                    )
                    results[task.taskId] = failedResult
                    sessionContext?.storeExecutionResult(task.taskId, failedResult)
                }
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("❌ [REACTOR] Session creation failed: ${e.message}")
                println("❌ [REACTOR] Session exception type: ${e.javaClass.name}")
                println("❌ [REACTOR] Session exception stack trace:")
                e.printStackTrace()
            }
            
            // Create failed results for all tasks in this group
            tasks.forEach { task ->
                val failedResult = TaskExecutionResult(
                    taskId = task.taskId,
                    success = false,
                    duration = 0,
                    goalResults = emptyList(),
                    errorMessage = "Session creation failed: ${e.message}"
                )
                results[task.taskId] = failedResult
                sessionContext?.storeExecutionResult(task.taskId, failedResult)
            }
        }
        
        val groupDuration = System.currentTimeMillis() - groupStartTime
        if (verbose) {
            println("🏁 [REACTOR] Batch group execution completed in ${groupDuration}ms")
        }
        
        return results
    }

    /**
     * Load Maven settings from user and global settings files
     */
    private fun loadMavenSettings(verbose: Boolean): Settings {
        val settingsBuilder = DefaultSettingsBuilderFactory().newInstance()
        val settingsRequest = DefaultSettingsBuildingRequest()
        
        // Set global settings file
        val globalSettingsFile = File(System.getProperty("maven.home", "/opt/apache-maven-3.9.10") + "/conf/settings.xml")
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
     * Create repository system session for Maven operations
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
     * Apply settings configuration to Maven execution request
     */
    private fun applySettingsConfiguration(request: MavenExecutionRequest, settings: Settings, verbose: Boolean) {
        // Apply settings to request
        request.localRepositoryPath = File(settings.localRepository ?: "${System.getProperty("user.home")}/.m2/repository")
        
        // Apply active profiles
        settings.activeProfiles?.let { activeProfiles ->
            request.activeProfiles = activeProfiles
            if (verbose) {
                println("Active profiles: ${activeProfiles.joinToString(", ")}")
            }
        }
        
        // Apply properties - settings doesn't have properties field directly
        // Properties are typically applied via system properties or profiles
        
        if (verbose) {
            println("Applied settings configuration to Maven request")
        }
    }

    /**
     * Setup environment variables for Maven execution
     */
    private fun setupEnvironmentVariables() {
        // Set Maven multimodule directory if not already set
        if (System.getProperty("maven.multiModuleProjectDirectory") == null) {
            System.setProperty("maven.multiModuleProjectDirectory", System.getProperty("user.dir"))
        }
        
        // Set Maven home if not already set
        if (System.getProperty("maven.home") == null) {
            System.setProperty("maven.home", "/opt/apache-maven-3.9.10")
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
                        properties = extractSessionProperties(tasks, results),
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
        
        // Apply session properties to task configuration
        sessionData.properties.forEach { (key, value) ->
            task.properties[key] = value
        }
        
        if (verbose) {
            println("✅ [SESSION] Applied ${sessionData.properties.size} session properties to ${task.taskId}")
        }
    }

    /**
     * Extract session properties from task results
     */
    private fun extractSessionProperties(tasks: List<TaskExecution>, results: Map<String, TaskExecutionResult>): Map<String, String> {
        val sessionProperties = mutableMapOf<String, String>()
        
        // Extract common execution properties
        sessionProperties["executionTimestamp"] = System.currentTimeMillis().toString()
        sessionProperties["taskCount"] = tasks.size.toString()
        sessionProperties["successfulTasks"] = results.values.count { it.success }.toString()
        sessionProperties["totalDuration"] = results.values.sumOf { it.duration }.toString()
        
        // Add goal-specific properties
        val allGoals = tasks.flatMap { it.goals }.distinct()
        sessionProperties["executedGoals"] = allGoals.joinToString(",")
        
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
     * Eclipse Sisu component lookup using Guice injector (same as Maven internal DI)
     */
    private inline fun <reified T> getComponent(): T {
        return try {
            plexusContainer.lookup(T::class.java)
        } catch (e: ComponentLookupException) {
            throw RuntimeException("Failed to lookup component ${T::class.java.name}: ${e.message}", e)
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
            // Eclipse Sisu injector doesn't need explicit disposal
            // The Guice injector will be garbage collected automatically
            sessionContext?.clearCaches()
        } catch (e: Exception) {
            System.err.println("Warning: Failed to cleanup embedder resources: ${e.message}")
        }
    }
    
    /**
     * Calculate goal dependencies for a task - simplified basic implementation
     */
    private fun calculateTaskDependencies(task: TaskExecution, allProjects: List<MavenProject>, verbose: Boolean): List<DependencyResult> {
        return try {
            if (verbose) {
                println("🔗 [DEPS] Analyzing project: ${task.project.artifactId}")
                println("🔗 [DEPS] Goals: ${task.goals}")
                println("🔗 [DEPS] Project dependencies: ${task.project.dependencies?.size ?: 0}")
            }
            
            if (task.goals.isEmpty()) {
                if (verbose) {
                    println("🔗 [DEPS] No goals to analyze, returning empty dependencies")
                }
                return emptyList()
            }
            
            val dependencies = mutableListOf<DependencyResult>()
            
            // Simple implementation: convert Maven project dependencies to DependencyResult
            task.project.dependencies?.forEach { mavenDep ->
                if (mavenDep != null) {
                    if (verbose) {
                        println("🔗 [DEPS] Processing dependency: ${mavenDep.groupId}:${mavenDep.artifactId}:${mavenDep.version}")
                    }
                    dependencies.add(DependencyResult.fromMavenDependency(mavenDep))
                }
            }
            
            if (verbose) {
                println("🔗 [DEPS] Total Maven dependencies processed: ${dependencies.size}")
            }
            
            return dependencies.distinctBy { "${it.groupId}:${it.artifactId}" }
            
        } catch (e: Exception) {
            if (verbose) {
                println("⚠️  [DEPS] Failed to calculate dependencies for ${task.taskId}: ${e.message}")
                e.printStackTrace()
            }
            emptyList()
        }
    }
}