
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.apache.maven.shared.invoker.*
import java.io.File
import kotlin.system.exitProcess

/**
 * Nx Maven Batch Executor - Executes multiple Maven goals in a single session
 * while maintaining proper artifact context and providing detailed per-goal results.
 */
object NxMavenBatchExecutor {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            System.err.println("Usage: java NxMavenBatchExecutor <goals> <workspaceRoot> <projects> [verbose]")
            System.err.println("Example: java NxMavenBatchExecutor \"maven-jar-plugin:jar,maven-install-plugin:install\" \"/workspace\" \".,module1,module2\" true")
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
            exitProcess(if (result.isOverallSuccess()) 0 else 1)
            
        } catch (e: Exception) {
            val errorResult = BatchExecutionResult().apply {
                setOverallSuccess(false)
                setErrorMessage("Batch executor failed: ${e.message}")
            }
            
            System.err.println(gson.toJson(errorResult))
            exitProcess(1)
        }
    }

    /**
     * Execute multiple Maven goals across multiple projects in a single session
     */
    fun executeBatch(goals: List<String>, workspaceRoot: String, projects: List<String>, verbose: Boolean): BatchExecutionResult {
        val batchResult = BatchExecutionResult().apply {
            setOverallSuccess(true)
        }
        
        try {
            val workspaceDir = File(workspaceRoot)
            val rootPomFile = File(workspaceDir, "pom.xml")
            
            if (!rootPomFile.exists()) {
                throw RuntimeException("Root pom.xml not found in workspace: $workspaceRoot")
            }

            val batchStartTime = System.currentTimeMillis()

            // Execute all goals across all projects in single Maven invoker session
            val batchGoalResult = executeMultiProjectGoalsWithInvoker(goals, projects, workspaceDir, rootPomFile, verbose)
            batchResult.addGoalResult(batchGoalResult)
            batchResult.setOverallSuccess(batchGoalResult.isSuccess())
            if (!batchGoalResult.isSuccess()) {
                batchResult.setErrorMessage("Multi-project batch goal execution failed")
            }
            
            val batchDuration = System.currentTimeMillis() - batchStartTime
            batchResult.setTotalDurationMs(batchDuration)
            
        } catch (e: Exception) {
            batchResult.setOverallSuccess(false)
            batchResult.setErrorMessage("Batch execution failed: ${e.message}")
        }
        
        return batchResult
    }

    /**
     * Execute multiple goals across multiple projects using Maven Invoker API
     */
    private fun executeMultiProjectGoalsWithInvoker(
        goals: List<String>, 
        projects: List<String>, 
        workspaceDir: File, 
        rootPomFile: File, 
        verbose: Boolean
    ): GoalExecutionResult {
        val goalResult = GoalExecutionResult().apply {
            setGoal("${goals.joinToString(",")} (across ${projects.size} projects)")
        }
        
        val startTime = System.currentTimeMillis()
        
        if (verbose) {
            System.out.println("Starting Maven batch execution...")
        }
        
        // Read previous goal results for smart execution decisions
        val previousGoalResults = readGoalResults(workspaceDir, projects)
        
        if (verbose && previousGoalResults.isNotEmpty()) {
            System.out.println("Found previous goal results for ${previousGoalResults.size} projects")
            previousGoalResults.forEach { (project, results) ->
                val buildSuccess = results["buildSuccess"] as? Boolean ?: false
                val previousGoals = results["requestedGoals"] as? List<*>
                val executionDate = results["executionDate"] as? String
                System.out.println("  $project: success=$buildSuccess, goals=$previousGoals, date=$executionDate")
            }
        }
        
        try {
            // Capture output
            val outputLines = mutableListOf<String>()
            val errorLines = mutableListOf<String>()
            
            val invoker = DefaultInvoker()
            
            // Configure Maven executable - prefer wrapper, then MAVEN_HOME, then system Maven
            val mavenExecutable = findMavenExecutable(workspaceDir)
            if (mavenExecutable != null) {
                val mvnFile = File(mavenExecutable)
                
                // If it's a wrapper file, use it directly
                if (mvnFile.name.startsWith("mvnw")) {
                    // Set the wrapper as the Maven executable
                    invoker.mavenExecutable = mvnFile
                } else {
                    // For system Maven, set MAVEN_HOME if we can determine it
                    val binDir = mvnFile.parentFile
                    if (binDir != null && binDir.name == "bin") {
                        val mavenHome = binDir.parentFile
                        if (mavenHome != null) {
                            invoker.mavenHome = mavenHome
                        }
                    }
                }
            } else {
                // Fallback to MAVEN_HOME environment variable
                val mavenHome = System.getenv("MAVEN_HOME")
                if (mavenHome != null) {
                    invoker.mavenHome = File(mavenHome)
                }
            }
            
            val request = DefaultInvocationRequest().apply {
                pomFile = rootPomFile
                baseDirectory = workspaceDir
                
                // Enable session management for Nx batch executor
                val props = java.util.Properties()
                props.setProperty("nx.session.enabled", "true")
                properties = props
                
                // Only add session goals if the plugin is available (to avoid test failures)
                val sessionAwareGoals = if (isSessionPluginAvailable()) {
                    listOf("io.quarkus:maven-plugin:999-SNAPSHOT:load-session") + goals + listOf("io.quarkus:maven-plugin:999-SNAPSHOT:save-session")
                } else {
                    goals
                }
                setGoals(sessionAwareGoals)
            }
            
            // Use Maven's -pl option to specify which projects to build
            // Convert project paths to Maven module identifiers
            val projectList = projects.map { project ->
                if ("." == project) {
                    // Root project - use the artifact ID from root pom
                    "."
                } else {
                    // Child module - use relative path
                    project
                }
            }
            
            if (projectList.isNotEmpty() && projectList != listOf(".")) {
                // Only use -pl if we're not building everything (i.e., not just root)
                val projectsArg = projectList.joinToString(",")
                request.projects = projectsArg.split(",")
            }
            
            // Set output handlers
            request.setOutputHandler { line ->
                outputLines.add(line)
                if (verbose) {
                    System.out.println("[MULTI-PROJECT] $line")
                }
            }
            
            request.setErrorHandler { line ->
                errorLines.add(line)
                if (verbose) {
                    System.err.println("[MULTI-PROJECT ERROR] $line")
                }
            }

            if (verbose) {
                val sessionEnabled = isSessionPluginAvailable()
                System.out.println("Executing goals: ${request.goals.joinToString(", ")}")
                System.out.println("Original user goals: ${goals.joinToString(", ")}")
                System.out.println("Across projects: ${projects.joinToString(", ")}")
                System.out.println("Working directory: ${workspaceDir.absolutePath}")
                System.out.println("Session management enabled: $sessionEnabled")
                if (sessionEnabled) {
                    System.out.println("Session goals added to execution")
                } else {
                    System.out.println("Session plugin not available, using original goals only")
                }
            }

            // Check if execution can be skipped based on previous results
            val canSkipExecution = projects.size == 1 && previousGoalResults.containsKey(projects[0])
            if (canSkipExecution) {
                val projectGoalResults = previousGoalResults[projects[0]]!!
                if (shouldSkipGoals(projectGoalResults, goals)) {
                    if (verbose) {
                        System.out.println("Skipping execution - goals already completed successfully")
                    }
                    
                    // Return successful result without executing
                    val duration = System.currentTimeMillis() - startTime
                    goalResult.apply {
                        setSuccess(true)
                        setDurationMs(duration)
                        setOutput(listOf("Goals skipped - already executed successfully in previous session"))
                        setErrors(emptyList())
                        setExitCode(0)
                    }
                    return goalResult
                }
            }

            // Execute all goals across all projects in single Maven reactor session
            val result = invoker.execute(request)
            
            val duration = System.currentTimeMillis() - startTime
            
            goalResult.apply {
                setSuccess(result.exitCode == 0)
                setDurationMs(duration)
                setOutput(outputLines)
                setErrors(errorLines)
                setExitCode(result.exitCode)
            }
            
            if (result.executionException != null) {
                goalResult.setErrorMessage(result.executionException.message)
            }
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            goalResult.apply {
                setSuccess(false)
                setDurationMs(duration)
                setErrorMessage("Multi-project goal execution exception: ${e.message}")
                setErrors(listOf(e.message ?: "Unknown error"))
            }
            
            if (verbose) {
                System.out.println("ERROR: Exception in Maven execution: ${e.message}")
            }
        }
        
        return goalResult
    }

    /**
     * Check if the session plugin is available to avoid test failures
     */
    private fun isSessionPluginAvailable(): Boolean {
        return try {
            // Check if we're in a local repository where the plugin is installed
            val userHome = System.getProperty("user.home")
            val localRepo = System.getProperty("maven.repo.local") ?: "$userHome/.m2/repository"
            val pluginPath = File(localRepo, "io/quarkus/maven-plugin/999-SNAPSHOT")
            pluginPath.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read goal results from session files for given projects
     */
    private fun readGoalResults(workspaceDir: File, projects: List<String>): Map<String, Map<String, Any?>> {
        val sessionDir = File(workspaceDir, ".nx-maven-sessions")
        val results = mutableMapOf<String, Map<String, Any?>>()
        
        if (!sessionDir.exists()) {
            return results
        }
        
        projects.forEach { project ->
            try {
                val sessionFileName = "${project.replace("/", "_")}.json"
                val sessionFile = File(sessionDir, sessionFileName)
                
                if (sessionFile.exists()) {
                    val sessionContent = sessionFile.readText()
                    val sessionData = gson.fromJson(sessionContent, Map::class.java) as? Map<String, Any?>
                    
                    sessionData?.get("goalResults")?.let { goalResults ->
                        if (goalResults is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            results[project] = goalResults as Map<String, Any?>
                        }
                    }
                }
            } catch (e: Exception) {
                System.err.println("Warning: Failed to read goal results for project $project: ${e.message}")
            }
        }
        
        return results
    }

    /**
     * Check if goals should be skipped based on previous execution results
     */
    private fun shouldSkipGoals(goalResults: Map<String, Any?>, requestedGoals: List<String>): Boolean {
        // Check if the same goals were already executed successfully
        val previousGoals = goalResults["requestedGoals"] as? List<*>
        val buildSuccess = goalResults["buildSuccess"] as? Boolean ?: false
        
        if (!buildSuccess) {
            // Don't skip if previous execution failed
            return false
        }
        
        if (previousGoals != null) {
            val previousGoalsSet = previousGoals.map { it.toString() }.toSet()
            val requestedGoalsSet = requestedGoals.toSet()
            
            // Skip if requested goals are a subset of previously executed goals
            return requestedGoalsSet.all { it in previousGoalsSet }
        }
        
        return false
    }

    /**
     * Find Maven executable, preferring wrapper files over system installations
     */
    private fun findMavenExecutable(workspaceDir: File): String? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        
        // First, look for Maven wrapper in workspace and parent directories
        var currentDir: File? = workspaceDir
        while (currentDir != null) {
            val wrapperFile = if (isWindows) {
                File(currentDir, "mvnw.cmd")
            } else {
                File(currentDir, "mvnw")
            }
            
            if (wrapperFile.exists() && wrapperFile.canExecute()) {
                return wrapperFile.absolutePath
            }
            
            // Move up one directory level, but check for null parent
            currentDir = currentDir.parentFile
        }
        
        // Fallback to system Maven installation in PATH
        val pathEnv = System.getenv("PATH") ?: return null
        val pathSeparator = System.getProperty("path.separator")
        val paths = pathEnv.split(pathSeparator)
        
        val mvnCommand = if (isWindows) "mvn.cmd" else "mvn"
        
        for (path in paths) {
            val mvnFile = File(path, mvnCommand)
            if (mvnFile.exists() && mvnFile.canExecute()) {
                return mvnFile.absolutePath
            }
        }
        
        return null
    }

    /**
     * Result of executing a batch of Maven goals
     */
    class BatchExecutionResult {
        @SerializedName("overallSuccess")
        private var _overallSuccess: Boolean = false
        
        @SerializedName("totalDurationMs")
        private var _totalDurationMs: Long = 0
        
        @SerializedName("errorMessage")
        private var _errorMessage: String? = null
        
        @SerializedName("goalResults")
        private var _goalResults: MutableList<GoalExecutionResult> = mutableListOf()
        
        fun addGoalResult(goalResult: GoalExecutionResult) {
            _goalResults.add(goalResult)
        }
        
        // Java-compatible getters and setters
        fun isOverallSuccess() = _overallSuccess
        fun setOverallSuccess(success: Boolean) { _overallSuccess = success }
        fun getTotalDurationMs() = _totalDurationMs
        fun setTotalDurationMs(duration: Long) { _totalDurationMs = duration }
        fun getErrorMessage() = _errorMessage
        fun setErrorMessage(message: String?) { _errorMessage = message }
        fun getGoalResults() = _goalResults
        fun setGoalResults(results: MutableList<GoalExecutionResult>) { _goalResults = results }
    }

    /**
     * Result of executing a single Maven goal
     */
    class GoalExecutionResult {
        @SerializedName("goal")
        private var _goal: String? = null
        
        @SerializedName("success")
        private var _success: Boolean = false
        
        @SerializedName("durationMs")
        private var _durationMs: Long = 0
        
        @SerializedName("exitCode")
        private var _exitCode: Int = 0
        
        @SerializedName("errorMessage")
        private var _errorMessage: String? = null
        
        @SerializedName("output")
        private var _output: List<String> = mutableListOf()
        
        @SerializedName("errors")
        private var _errors: List<String> = mutableListOf()
        
        // Java-compatible getters and setters
        fun getGoal() = _goal
        fun setGoal(goalName: String?) { _goal = goalName }
        fun isSuccess() = _success
        fun setSuccess(isSuccessful: Boolean) { _success = isSuccessful }
        fun getDurationMs() = _durationMs
        fun setDurationMs(duration: Long) { _durationMs = duration }
        fun getExitCode() = _exitCode
        fun setExitCode(code: Int) { _exitCode = code }
        fun getErrorMessage() = _errorMessage
        fun setErrorMessage(message: String?) { _errorMessage = message }
        fun getOutput() = _output
        fun setOutput(outputLines: List<String>) { _output = outputLines }
        fun getErrors() = _errors
        fun setErrors(errorLines: List<String>) { _errors = errorLines }
    }
}