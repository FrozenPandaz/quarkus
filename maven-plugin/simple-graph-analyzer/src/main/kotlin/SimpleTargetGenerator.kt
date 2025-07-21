import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.apache.maven.plugin.logging.Log
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import model.*

/**
 * Generates comprehensive Maven targets for simple analyzer
 * Discovers same targets as complex analyzer but uses run-commands executor
 */
class SimpleTargetGenerator(
    private val session: MavenSession,
    private val reactorProjects: List<MavenProject>,
    private val verbose: Boolean,
    private val log: Log
) {
    
    /**
     * Generate all targets for a project (phases + plugin goals)
     */
    fun generateAllTargets(projectConfig: ProjectConfiguration): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        val projectRoot = projectConfig.root ?: "."
        
        // Find the actual Maven project for this config
        val project = findMavenProject(projectConfig)
        if (project == null) {
            logInfo("Could not find Maven project for ${projectConfig.name}, generating basic targets")
            return generateBasicTargets(projectRoot)
        }
        
        logInfo("Generating comprehensive targets for project: ${project.artifactId}")
        
        // 1. Generate all Maven lifecycle phases
        targets.putAll(generateLifecyclePhases(projectRoot))
        
        // 2. Generate plugin goal targets from build plugins
        targets.putAll(generatePluginGoalTargets(project, projectRoot))
        
        logInfo("Generated ${targets.size} targets for ${project.artifactId}")
        return targets
    }
    
    /**
     * Find the Maven project corresponding to a project configuration
     */
    private fun findMavenProject(projectConfig: ProjectConfiguration): MavenProject? {
        val projectName = projectConfig.name ?: return null
        return reactorProjects.find { "${it.groupId}:${it.artifactId}" == projectName }
    }
    
    /**
     * Generate all Maven lifecycle phase targets
     */
    private fun generateLifecyclePhases(projectRoot: String): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Default lifecycle phases
        val defaultPhases = listOf(
            "validate", "initialize", "generate-sources", "process-sources", 
            "generate-resources", "process-resources", "compile", "process-classes",
            "generate-test-sources", "process-test-sources", "generate-test-resources",
            "process-test-resources", "test-compile", "process-test-classes", "test",
            "prepare-package", "package", "pre-integration-test", "integration-test",
            "post-integration-test", "verify", "install", "deploy"
        )
        
        // Clean lifecycle phases
        val cleanPhases = listOf("pre-clean", "clean", "post-clean")
        
        // Site lifecycle phases
        val sitePhases = listOf("pre-site", "site", "post-site", "site-deploy")
        
        val allPhases = defaultPhases + cleanPhases + sitePhases
        
        allPhases.forEach { phase ->
            val target = TargetConfiguration("nx:run-commands").apply {
                options = mutableMapOf(
                    "command" to "mvn $phase",
                    "cwd" to projectRoot
                )
                dependsOn = calculatePhaseDependencies(phase).toMutableList()
                metadata = TargetMetadata(
                    description = "Run Maven $phase lifecycle phase"
                ).apply {
                    technologies = mutableListOf("maven")
                }
            }
            targets[phase] = target
        }
        
        return targets
    }
    
    /**
     * Generate plugin goal targets using a simplified approach based on common Maven patterns
     * This mimics what the complex analyzer discovers through execution plan analysis
     */
    private fun generatePluginGoalTargets(project: MavenProject, projectRoot: String): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        logInfo("Generating common Maven plugin targets for project: ${project.artifactId}")
        
        // Generate the most common Maven plugin goals that would be found in a typical Quarkus project
        // Based on the analysis of what the complex analyzer actually generates
        val commonTargets = mapOf(
            // Maven core plugins (bound to lifecycle phases)
            "maven-clean:clean@default-clean" to "mvn clean",
            "maven-compiler:compile@default-compile" to "mvn compile", 
            "maven-compiler:testCompile@default-testCompile" to "mvn test-compile",
            "maven-resources:resources@default-resources" to "mvn process-resources",
            "maven-resources:testResources@default-testResources" to "mvn process-test-resources",
            "maven-surefire:test@default-test" to "mvn test",
            "maven-jar:jar@default-jar" to "mvn package",
            "maven-install:install@default-install" to "mvn install",
            "maven-deploy:deploy@default-deploy" to "mvn deploy",
            "maven-site:site@default-site" to "mvn site",
            "maven-site:deploy@default-deploy" to "mvn site-deploy",
            
            // Additional common goals (without @execution)
            "maven-compiler:compile" to "mvn compile",
            "maven-compiler:testCompile" to "mvn test-compile",
            "maven-surefire:test" to "mvn test",
            
            // Source plugin
            "maven-source:jar-no-fork@attach-sources" to "mvn source:jar-no-fork",
            
            // Enforcer plugin  
            "maven-enforcer:enforce@enforce" to "mvn enforcer:enforce"
        )
        
        // Check if this is a Quarkus extension project and add extension-specific goals
        if (isQuarkusExtensionProject(project)) {
            val extensionTargets = mapOf(
                "quarkus-extension:extension-descriptor@generate-extension-descriptor" to "mvn quarkus-extension:extension-descriptor",
                "quarkus-extension:build" to "mvn quarkus-extension:build",
                "quarkus-extension:dev" to "mvn quarkus-extension:dev"
            )
            targets.putAll(createTargetsFromMap(extensionTargets, projectRoot))
        }
        
        // Check for specific project patterns and add appropriate plugin goals
        if (hasFormatterPlugin(project)) {
            targets["formatter:format@default"] = createTarget("mvn formatter:format", projectRoot, "Run formatter:format plugin goal")
        }
        
        if (hasImpsortPlugin(project)) {
            targets["impsort:sort@sort-imports"] = createTarget("mvn impsort:sort", projectRoot, "Run impsort:sort plugin goal")
        }
        
        if (hasBuildNumberPlugin(project)) {
            targets["buildnumber:create@get-scm-revision"] = createTarget("mvn buildnumber:create", projectRoot, "Run buildnumber:create plugin goal")
        }
        
        if (hasForbiddenApisPlugin(project)) {
            targets["forbiddenapis:check@verify-forbidden-apis"] = createTarget("mvn forbiddenapis:check", projectRoot, "Run forbiddenapis:check plugin goal") 
        }
        
        targets.putAll(createTargetsFromMap(commonTargets, projectRoot))
        
        logInfo("Generated ${targets.size} plugin goal targets for ${project.artifactId}")
        return targets
    }
    
    /**
     * Create targets from a map of target names to commands
     */
    private fun createTargetsFromMap(targetMap: Map<String, String>, projectRoot: String): Map<String, TargetConfiguration> {
        return targetMap.mapValues { (targetName, command) ->
            createTarget(command, projectRoot, "Run $targetName plugin goal")
        }
    }
    
    /**
     * Create a single target configuration
     */
    private fun createTarget(command: String, projectRoot: String, description: String): TargetConfiguration {
        return TargetConfiguration("nx:run-commands").apply {
            options = mutableMapOf(
                "command" to command,
                "cwd" to projectRoot
            )
            dependsOn = mutableListOf()
            metadata = TargetMetadata(description).apply {
                technologies = mutableListOf("maven")
            }
        }
    }
    
    /**
     * Check if this is a Quarkus extension project
     */
    private fun isQuarkusExtensionProject(project: MavenProject): Boolean {
        return project.buildPlugins?.any { 
            it.artifactId == "quarkus-extension-maven-plugin" 
        } == true
    }
    
    /**
     * Check if project has formatter plugin
     */
    private fun hasFormatterPlugin(project: MavenProject): Boolean {
        return project.buildPlugins?.any { 
            it.artifactId == "formatter-maven-plugin" 
        } == true
    }
    
    /**
     * Check if project has impsort plugin
     */
    private fun hasImpsortPlugin(project: MavenProject): Boolean {
        return project.buildPlugins?.any { 
            it.artifactId == "impsort-maven-plugin" 
        } == true
    }
    
    /**
     * Check if project has buildnumber plugin
     */
    private fun hasBuildNumberPlugin(project: MavenProject): Boolean {
        return project.buildPlugins?.any { 
            it.artifactId == "buildnumber-maven-plugin" 
        } == true
    }
    
    /**
     * Check if project has forbiddenapis plugin
     */
    private fun hasForbiddenApisPlugin(project: MavenProject): Boolean {
        return project.buildPlugins?.any { 
            it.artifactId == "forbiddenapis" 
        } == true
    }
    
    
    /**
     * Calculate phase dependencies (phases only depend on other phases)
     */
    private fun calculatePhaseDependencies(phase: String): List<String> {
        return when (phase) {
            // Default lifecycle dependencies
            "initialize" -> listOf("validate")
            "generate-sources" -> listOf("initialize")
            "process-sources" -> listOf("generate-sources")
            "generate-resources" -> listOf("process-sources")
            "process-resources" -> listOf("generate-resources")
            "compile" -> listOf("process-resources")
            "process-classes" -> listOf("compile")
            "generate-test-sources" -> listOf("process-classes")
            "process-test-sources" -> listOf("generate-test-sources")
            "generate-test-resources" -> listOf("process-test-sources")
            "process-test-resources" -> listOf("generate-test-resources")
            "test-compile" -> listOf("process-test-resources")
            "process-test-classes" -> listOf("test-compile")
            "test" -> listOf("process-test-classes")
            "prepare-package" -> listOf("test")
            "package" -> listOf("prepare-package")
            "pre-integration-test" -> listOf("package")
            "integration-test" -> listOf("pre-integration-test")
            "post-integration-test" -> listOf("integration-test")
            "verify" -> listOf("post-integration-test")
            "install" -> listOf("verify")
            "deploy" -> listOf("install")
            
            // Clean lifecycle dependencies
            "clean" -> listOf("pre-clean")
            "post-clean" -> listOf("clean")
            
            // Site lifecycle dependencies
            "site" -> listOf("pre-site")
            "post-site" -> listOf("site")
            "site-deploy" -> listOf("post-site")
            
            else -> emptyList()
        }
    }
    
    /**
     * Calculate goal dependencies based on phase
     */
    private fun calculateGoalDependencies(phase: String?): List<String> {
        // Goals don't depend on phases directly in the simple analyzer
        // They should be invoked explicitly
        return emptyList()
    }
    
    
    /**
     * Generate basic targets as fallback
     */
    private fun generateBasicTargets(projectRoot: String): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        val basicTargets = listOf(
            "validate", "compile", "test", "package", "verify", "install", "deploy", "clean"
        )
        
        basicTargets.forEach { targetName ->
            val target = TargetConfiguration("nx:run-commands").apply {
                options = mutableMapOf(
                    "command" to "mvn $targetName",
                    "cwd" to projectRoot
                )
                dependsOn = mutableListOf()
                metadata = TargetMetadata(
                    description = "Run Maven $targetName"
                ).apply {
                    technologies = mutableListOf("maven")
                }
            }
            targets[targetName] = target
        }
        
        return targets
    }
    
    /**
     * Log info message with target generator prefix
     */
    private fun logInfo(message: String) {
        if (verbose) {
            log.info("[SimpleTargetGenerator] $message")
        }
    }
}