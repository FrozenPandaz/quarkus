import com.google.gson.Gson
import com.google.gson.GsonBuilder
import model.*
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Simplified Maven plugin that analyzes Maven projects for Nx integration.
 * Uses the same project discovery logic as the complex analyzer but generates simple run-commands targets.
 */
@Mojo(name = "simple-analyze", aggregator = true, requiresDependencyResolution = ResolutionScope.NONE)
class SimpleAnalyzerMojo : AbstractMojo() {
    
    @Parameter(defaultValue = "\${session}", readonly = true, required = true)
    private lateinit var session: MavenSession
    
    @Parameter(defaultValue = "\${reactorProjects}", readonly = true, required = true)
    private lateinit var reactorProjects: List<MavenProject>
    
    @Parameter(property = "nx.outputFile")
    private var outputFile: String? = null
    
    @Parameter(property = "nx.verbose", defaultValue = "false")
    private var verboseStr: String? = null
    
    private val verbose: Boolean
        get() = verboseStr?.toBoolean() ?: false
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    
    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        logInfo("=== Simple Maven Graph Analyzer Starting ===")
        logInfo("Projects to analyze: ${reactorProjects.size}")
        logInfo("Output file: ${outputFile ?: "Not specified"}")
        logInfo("Verbose logging: $verbose")
        
        try {
            // Use the same project discovery logic as the complex analyzer
            val createNodesResultGenerator = CreateNodesResultGenerator(session, reactorProjects, verbose, log)
            val createDependenciesGenerator = CreateDependenciesGenerator(session, reactorProjects, verbose, log)
            
            // Generate create nodes results with simple target generation
            val createNodesResults = createNodesResultGenerator.generateCreateNodesResults { projectConfig ->
                // Replace the complex target generation with simple run-commands targets
                generateSimpleTargets(projectConfig)
            }
            
            logInfo("Generated ${createNodesResults.size} create nodes results")
            
            // Generate dependencies (same as complex analyzer)
            val createDependencies = createDependenciesGenerator.generateCreateDependencies()
            logInfo("Generated ${createDependencies.size} project dependencies")
            
            // Convert create nodes results to the expected tuple format [file, result]
            val createNodesResultTuples = createNodesResults.map { entry ->
                arrayOf(entry.pomFilePath, entry.result)
            }
            
            // Create final result structure
            val result = mapOf(
                "createNodesResults" to createNodesResultTuples,
                "createDependencies" to createDependencies
            )
            
            // Write output
            writeOutput(result)
            
            logInfo("=== Simple Maven Graph Analyzer Completed Successfully ===")
            
        } catch (e: Exception) {
            log.error("Simple Maven Graph Analyzer failed", e)
            throw MojoExecutionException("Analysis failed: ${e.message}", e)
        }
    }
    
    /**
     * Generate simple run-commands targets for Maven lifecycle phases
     */
    private fun generateSimpleTargets(projectConfig: ProjectConfiguration): Map<String, TargetConfiguration> {
        logInfo("Generating simple targets for project: ${projectConfig.name}")
        
        val targets = mutableMapOf<String, TargetConfiguration>()
        val projectRoot = projectConfig.root ?: "."
        
        // Standard Maven lifecycle phases
        val lifecyclePhases = listOf(
            "validate" to emptyList(),
            "compile" to listOf("validate"),
            "test" to listOf("compile"),
            "package" to listOf("test"),
            "verify" to listOf("package"),
            "install" to listOf("verify"),
            "deploy" to listOf("install")
        )
        
        lifecyclePhases.forEach { (phase, dependsOn) ->
            val target = TargetConfiguration("nx:run-commands").apply {
                options = mutableMapOf(
                    "command" to "mvn $phase",
                    "cwd" to projectRoot
                )
                this.dependsOn = dependsOn.toMutableList()
                metadata = TargetMetadata(
                    description = "Run Maven $phase lifecycle phase"
                ).apply {
                    technologies = mutableListOf("maven")
                }
            }
            targets[phase] = target
        }
        
        // Common Maven goals
        val commonGoals = mapOf(
            "clean" to "Clean project build artifacts",
            "dependency-tree" to "Display project dependency tree",
            "dependency-analyze" to "Analyze project dependencies",
            "help-effective-pom" to "Display effective POM"
        )
        
        commonGoals.forEach { (goalName, description) ->
            val actualGoal = goalName.replace("-", ":")
            val target = TargetConfiguration("nx:run-commands").apply {
                options = mutableMapOf(
                    "command" to "mvn $actualGoal",
                    "cwd" to projectRoot
                )
                this.dependsOn = mutableListOf()
                metadata = TargetMetadata(
                    description = description
                ).apply {
                    technologies = mutableListOf("maven")
                }
            }
            targets[goalName] = target
        }
        
        logInfo("Generated ${targets.size} simple targets for ${projectConfig.name}")
        return targets
    }
    
    /**
     * Write the analysis result to the output file
     */
    private fun writeOutput(result: Map<String, Any>) {
        val outputPath = outputFile ?: throw MojoExecutionException("Output file not specified")
        
        try {
            val outputFileObj = File(outputPath)
            outputFileObj.parentFile?.mkdirs()
            
            FileWriter(outputFileObj).use { writer ->
                gson.toJson(result, writer)
            }
            
            logInfo("Analysis result written to: $outputPath")
            if (verbose) {
                logInfo("Output file size: ${outputFileObj.length()} bytes")
            }
            
        } catch (e: IOException) {
            throw MojoExecutionException("Failed to write output file: ${e.message}", e)
        }
    }
    
    /**
     * Log info message with simple analyzer prefix
     */
    private fun logInfo(message: String) {
        if (verbose) {
            log.info("[SimpleAnalyzer] $message")
        }
    }
}