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
            exitProcess(if (result.overallSuccess) 0 else 1)
            
        } catch (e: Exception) {
            val errorResult = BatchExecutionResult().apply {
                overallSuccess = false
                errorMessage = "Batch executor failed: ${e.message}"
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
            overallSuccess = true
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
            batchResult.overallSuccess = batchGoalResult.success
            if (!batchGoalResult.success) {
                batchResult.errorMessage = "Multi-project batch goal execution failed"
            }
            
            val batchDuration = System.currentTimeMillis() - batchStartTime
            batchResult.totalDurationMs = batchDuration
            
        } catch (e: Exception) {
            batchResult.overallSuccess = false
            batchResult.errorMessage = "Batch execution failed: ${e.message}"
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
            goal = "${goals.joinToString(",")} (across ${projects.size} projects)"
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            // Capture output
            val outputLines = mutableListOf<String>()
            val errorLines = mutableListOf<String>()
            
            val invoker = DefaultInvoker()
            
            // Find Maven executable - try MAVEN_HOME first, then PATH
            val mavenHome = System.getenv("MAVEN_HOME")
            if (mavenHome != null) {
                invoker.mavenHome = File(mavenHome)
            } else {
                // Try to find Maven in PATH
                val mavenExecutable = findMavenExecutable()
                if (mavenExecutable != null) {
                    invoker.mavenExecutable = File(mavenExecutable)
                }
            }
            
            val request = DefaultInvocationRequest().apply {
                pomFile = rootPomFile
                baseDirectory = workspaceDir
                setGoals(goals) // Execute all goals in single Maven session
                
                // Configure Maven properties to handle test failures gracefully
                val properties = mutableMapOf<String, String>()
                
                // If this batch includes test goals, don't fail the build on test failures
                val hasTestGoals = goals.any { goal -> 
                    goal.contains("surefire") || goal.contains("test") 
                }
                if (hasTestGoals) {
                    properties["maven.test.failure.ignore"] = "true"
                    properties["skipTests"] = "false"  // Run tests but don't fail on errors
                    if (verbose) {
                        println("Setting Maven properties for test failure handling: ${properties}")
                    }
                }
                
                if (properties.isNotEmpty()) {
                    val props = java.util.Properties()
                    properties.forEach { (key, value) -> props.setProperty(key, value) }
                    setProperties(props)
                }
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
                    println("[MULTI-PROJECT] $line")
                }
            }
            
            request.setErrorHandler { line ->
                errorLines.add(line)
                if (verbose) {
                    System.err.println("[MULTI-PROJECT ERROR] $line")
                }
            }

            if (verbose) {
                println("Executing goals: ${goals.joinToString(", ")}")
                println("Across projects: ${projects.joinToString(", ")}")
                println("Working directory: ${workspaceDir.absolutePath}")
            }

            // Execute all goals across all projects in single Maven reactor session
            val result = invoker.execute(request)
            
            val duration = System.currentTimeMillis() - startTime
            
            goalResult.apply {
                success = result.exitCode == 0
                durationMs = duration
                output = outputLines
                errors = errorLines
                exitCode = result.exitCode
            }
            
            if (result.executionException != null) {
                goalResult.errorMessage = result.executionException.message
            }
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            goalResult.apply {
                success = false
                durationMs = duration
                errorMessage = "Multi-project goal execution exception: ${e.message}"
                errors = listOf(e.message ?: "Unknown error")
            }
        }
        
        return goalResult
    }

    /**
     * Find Maven executable in system PATH
     */
    private fun findMavenExecutable(): String? {
        val possibleNames = arrayOf("mvn", "mvn.cmd", "mvn.bat")
        val pathEnv = System.getenv("PATH")
        
        if (pathEnv != null) {
            val paths = pathEnv.split(File.pathSeparator)
            for (path in paths) {
                for (name in possibleNames) {
                    val candidate = File(path, name)
                    if (candidate.exists() && candidate.canExecute()) {
                        return candidate.absolutePath
                    }
                }
            }
        }
        
        return null
    }

    /**
     * Result of executing a batch of Maven goals
     */
    class BatchExecutionResult {
        @SerializedName("overallSuccess")
        var overallSuccess: Boolean = false
        
        @SerializedName("totalDurationMs")
        var totalDurationMs: Long = 0
        
        @SerializedName("errorMessage")
        var errorMessage: String? = null
        
        @SerializedName("goalResults")
        var goalResults: MutableList<GoalExecutionResult> = mutableListOf()
        
        fun addGoalResult(goalResult: GoalExecutionResult) {
            goalResults.add(goalResult)
        }
    }

    /**
     * Result of executing a single Maven goal
     */
    class GoalExecutionResult {
        @SerializedName("goal")
        var goal: String? = null
        
        @SerializedName("success")
        var success: Boolean = false
        
        @SerializedName("durationMs")
        var durationMs: Long = 0
        
        @SerializedName("exitCode")
        var exitCode: Int = 0
        
        @SerializedName("errorMessage")
        var errorMessage: String? = null
        
        @SerializedName("output")
        var output: List<String> = mutableListOf()
        
        @SerializedName("errors")
        var errors: List<String> = mutableListOf()
    }
}