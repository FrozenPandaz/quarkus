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
     * Generate plugin goal targets from project's effective plugins (including inherited)
     */
    private fun generatePluginGoalTargets(project: MavenProject, projectRoot: String): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        logInfo("Discovering plugins for project: ${project.artifactId}")
        
        // Get all effective build plugins (includes inherited plugins)
        val effectivePlugins = project.getBuildPlugins() ?: emptyList()
        logInfo("Found ${effectivePlugins.size} effective build plugins")
        
        effectivePlugins.forEach { plugin ->
            logInfo("Processing plugin: ${plugin.artifactId}")
            targets.putAll(generatePluginTargets(plugin, projectRoot))
        }
        
        // Also generate standard Maven goals that are always available
        targets.putAll(generateStandardMavenGoals(projectRoot))
        
        return targets
    }
    
    /**
     * Generate standard Maven plugin goals that are always available
     */
    private fun generateStandardMavenGoals(projectRoot: String): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        
        // Standard Maven plugins that are typically available
        val standardPluginGoals = mapOf(
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
            "maven-site:deploy@default-deploy" to "mvn site-deploy"
        )
        
        standardPluginGoals.forEach { (targetName, command) ->
            val target = TargetConfiguration("nx:run-commands").apply {
                options = mutableMapOf(
                    "command" to command,
                    "cwd" to projectRoot
                )
                dependsOn = mutableListOf()
                metadata = TargetMetadata(
                    description = "Run $targetName plugin goal"
                ).apply {
                    technologies = mutableListOf("maven")
                }
            }
            targets[targetName] = target
        }
        
        return targets
    }
    
    /**
     * Generate targets for a specific plugin
     */
    private fun generatePluginTargets(plugin: Plugin, projectRoot: String): Map<String, TargetConfiguration> {
        val targets = mutableMapOf<String, TargetConfiguration>()
        val pluginArtifactId = plugin.artifactId
        
        // Generate targets for plugin executions
        plugin.executions?.forEach { execution ->
            execution.goals?.forEach { goal ->
                val targetName = if (execution.id != null && execution.id != "default") {
                    "$pluginArtifactId:$goal@${execution.id}"
                } else {
                    "$pluginArtifactId:$goal"
                }
                
                val target = TargetConfiguration("nx:run-commands").apply {
                    options = mutableMapOf(
                        "command" to "mvn ${plugin.groupId}:${plugin.artifactId}:$goal" + 
                                   if (execution.id != null && execution.id != "default") "@${execution.id}" else "",
                        "cwd" to projectRoot
                    )
                    dependsOn = calculateGoalDependencies(execution.phase).toMutableList()
                    metadata = TargetMetadata(
                        description = "Run $pluginArtifactId:$goal plugin goal"
                    ).apply {
                        technologies = mutableListOf("maven")
                    }
                }
                targets[targetName] = target
            }
        }
        
        // Generate basic plugin goal targets (without executions)
        val commonPluginGoals = getCommonPluginGoals(pluginArtifactId)
        commonPluginGoals.forEach { goal ->
            val targetName = "$pluginArtifactId:$goal"
            if (!targets.containsKey(targetName)) {
                val target = TargetConfiguration("nx:run-commands").apply {
                    options = mutableMapOf(
                        "command" to "mvn ${plugin.groupId}:${plugin.artifactId}:$goal",
                        "cwd" to projectRoot
                    )
                    dependsOn = mutableListOf()
                    metadata = TargetMetadata(
                        description = "Run $pluginArtifactId:$goal plugin goal"
                    ).apply {
                        technologies = mutableListOf("maven")
                    }
                }
                targets[targetName] = target
            }
        }
        
        return targets
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
     * Get common goals for well-known Maven plugins
     */
    private fun getCommonPluginGoals(artifactId: String): List<String> {
        return when (artifactId) {
            "maven-compiler-plugin" -> listOf("compile", "testCompile")
            "maven-surefire-plugin" -> listOf("test")
            "maven-clean-plugin" -> listOf("clean")
            "maven-install-plugin" -> listOf("install")
            "maven-deploy-plugin" -> listOf("deploy")
            "maven-site-plugin" -> listOf("site")
            "maven-resources-plugin" -> listOf("resources", "testResources")
            "maven-jar-plugin" -> listOf("jar")
            "maven-source-plugin" -> listOf("jar-no-fork")
            "maven-enforcer-plugin" -> listOf("enforce")
            else -> emptyList()
        }
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