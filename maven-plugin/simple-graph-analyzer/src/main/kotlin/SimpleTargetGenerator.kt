import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.apache.maven.plugin.logging.Log
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.lifecycle.LifecycleExecutor
import org.apache.maven.lifecycle.MavenExecutionPlan
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.descriptor.MojoDescriptor
import model.*

/**
 * Generates comprehensive Maven targets for simple analyzer
 * Discovers same targets as complex analyzer but uses run-commands executor
 */
class SimpleTargetGenerator(
    private val session: MavenSession,
    private val reactorProjects: List<MavenProject>,
    private val verbose: Boolean,
    private val log: Log,
    private val lifecycleExecutor: LifecycleExecutor?
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
        targets.putAll(generateLifecyclePhases(projectRoot, project))
        
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
    private fun generateLifecyclePhases(projectRoot: String, project: MavenProject): Map<String, TargetConfiguration> {
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
                // Add Maven API-based cacheability
                cache = if (shouldEnableCaching(phase, project)) true else null
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
        
        // Generate comprehensive Maven plugin goals to match what the complex analyzer discovers
        // This includes all targets that the complex analyzer would generate through dynamic discovery
        val commonTargets = mutableMapOf<String, String>()
        
        // Maven core plugins (bound to lifecycle phases) - these have @execution-id
        commonTargets.putAll(mapOf(
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
        ))
        
        // Additional common goals (without @execution-id) - standalone goals
        commonTargets.putAll(mapOf(
            "maven-compiler:compile" to "mvn compiler:compile",
            "maven-compiler:testCompile" to "mvn compiler:testCompile",
            "maven-surefire:test" to "mvn surefire:test",
            "maven-clean:clean" to "mvn clean:clean",
            "maven-resources:resources" to "mvn resources:resources",
            "maven-resources:testResources" to "mvn resources:testResources",
            "maven-jar:jar" to "mvn jar:jar",
            "maven-install:install" to "mvn install:install",
            "maven-deploy:deploy" to "mvn deploy:deploy",
            "maven-site:site" to "mvn site:site"
        ))
        
        // Source and Javadoc plugins
        commonTargets.putAll(mapOf(
            "maven-source:jar-no-fork@attach-sources" to "mvn source:jar-no-fork",
            "maven-source:jar-no-fork" to "mvn source:jar-no-fork",
            "maven-source:jar" to "mvn source:jar",
            "maven-javadoc:jar@attach-javadocs" to "mvn javadoc:jar",
            "maven-javadoc:jar" to "mvn javadoc:jar"
        ))
        
        // Enforcer plugin variants
        commonTargets.putAll(mapOf(
            "maven-enforcer:enforce@enforce" to "mvn enforcer:enforce",
            "maven-enforcer:enforce" to "mvn enforcer:enforce"
        ))
        
        // Additional plugin goals that complex analyzer commonly discovers
        commonTargets.putAll(mapOf(
            "maven-dependency:copy-dependencies" to "mvn dependency:copy-dependencies",
            "maven-dependency:analyze" to "mvn dependency:analyze",
            "maven-failsafe:integration-test" to "mvn failsafe:integration-test",
            "maven-failsafe:verify" to "mvn failsafe:verify",
            "build-helper:add-source" to "mvn build-helper:add-source",
            "build-helper:add-test-source" to "mvn build-helper:add-test-source"
        ))
        
        // Add common plugin goals that the complex analyzer discovers through getCommonGoalsForPlugin
        // This matches the dynamic discovery logic from ExecutionPlanAnalysisService
        project.buildPlugins?.forEach { plugin ->
            val artifactId = plugin.artifactId
            when {
                artifactId.contains("compiler") -> {
                    commonTargets["maven-compiler:compile"] = "mvn compiler:compile"
                    commonTargets["maven-compiler:testCompile"] = "mvn compiler:testCompile"
                }
                artifactId.contains("surefire") -> {
                    commonTargets["maven-surefire:test"] = "mvn surefire:test"
                }
                artifactId.contains("quarkus") -> {
                    commonTargets["quarkus:dev"] = "mvn quarkus:dev"
                    commonTargets["quarkus:build"] = "mvn quarkus:build"
                }
                artifactId.contains("spring-boot") -> {
                    commonTargets["spring-boot:run"] = "mvn spring-boot:run"
                    commonTargets["spring-boot:repackage"] = "mvn spring-boot:repackage"
                }
            }
        }
        
        // Create targets from the comprehensive map  
        targets.putAll(createTargetsFromMap(commonTargets.toMap(), projectRoot, project))
        
        // Check if this is a Quarkus extension project and add extension-specific goals
        if (isQuarkusExtensionProject(project)) {
            val extensionTargets = mapOf(
                "quarkus-extension:extension-descriptor@generate-extension-descriptor" to "mvn quarkus-extension:extension-descriptor",
                "quarkus-extension:build" to "mvn quarkus-extension:build",
                "quarkus-extension:dev" to "mvn quarkus-extension:dev"
            )
            targets.putAll(createTargetsFromMap(extensionTargets, projectRoot, project))
        }
        
        // Check for specific project patterns and add appropriate plugin goals
        if (hasFormatterPlugin(project)) {
            targets["formatter:format@default"] = createTarget("mvn formatter:format", projectRoot, "Run formatter:format plugin goal", "format", project)
        }
        
        if (hasImpsortPlugin(project)) {
            targets["impsort:sort@sort-imports"] = createTarget("mvn impsort:sort", projectRoot, "Run impsort:sort plugin goal", "sort", project)
        }
        
        if (hasBuildNumberPlugin(project)) {
            targets["buildnumber:create@get-scm-revision"] = createTarget("mvn buildnumber:create", projectRoot, "Run buildnumber:create plugin goal", "create", project)
        }
        
        if (hasForbiddenApisPlugin(project)) {
            targets["forbiddenapis:check@verify-forbidden-apis"] = createTarget("mvn forbiddenapis:check", projectRoot, "Run forbiddenapis:check plugin goal", "check", project) 
        }
        
        logInfo("Generated ${targets.size} plugin goal targets for ${project.artifactId}")
        return targets
    }
    
    /**
     * Create targets from a map of target names to commands
     */
    private fun createTargetsFromMap(targetMap: Map<String, String>, projectRoot: String, project: MavenProject): Map<String, TargetConfiguration> {
        return targetMap.mapValues { (targetName, command) ->
            // Extract goal name from target name (e.g., "maven-clean:clean" -> "clean")
            val goalName = if (targetName.contains(":")) targetName.split(":").last() else targetName
            createTarget(command, projectRoot, "Run $targetName plugin goal", goalName, project)
        }
    }
    
    /**
     * Create a single target configuration
     */
    private fun createTarget(command: String, projectRoot: String, description: String, goalName: String? = null, project: MavenProject? = null): TargetConfiguration {
        return TargetConfiguration("nx:run-commands").apply {
            options = mutableMapOf(
                "command" to command,
                "cwd" to projectRoot
            )
            dependsOn = mutableListOf()
            metadata = TargetMetadata(description).apply {
                technologies = mutableListOf("maven")
            }
            // Add Maven API-based cacheability for plugin goals
            cache = if (goalName != null && project != null && shouldEnableCaching(goalName, project)) true else null
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
     * Determine if a goal should be cacheable based on Maven API properties
     */
    private fun shouldEnableCaching(goal: String, project: MavenProject): Boolean {
        if (lifecycleExecutor == null) {
            // Fallback to basic heuristics if lifecycle executor not available
            return isCleanPhase(goal) || isCompilationGoal(goal)
        }
        
        try {
            // Try to get execution plan for the goal
            val executionPlan = lifecycleExecutor.calculateExecutionPlan(session, goal)
            
            // Look for the mojo execution that matches our goal
            val mojoExecution = executionPlan.mojoExecutions.find { mojoExec ->
                mojoExec.goal == goal || "${mojoExec.plugin.artifactId}:${mojoExec.goal}" == goal
            }
            
            if (mojoExecution != null) {
                val mojoDescriptor = mojoExecution.mojoDescriptor
                
                if (verbose) {
                    log.info("[SimpleTargetGenerator] Maven API indicates goal '$goal' properties: " +
                            "threadSafe=${mojoDescriptor.isThreadSafe}, " +
                            "onlineRequired=${mojoDescriptor.isOnlineRequired}, " +
                            "aggregator=${mojoDescriptor.isAggregator}")
                }
                
                // Use same logic as complex analyzer
                return when {
                    mojoDescriptor.alwaysExecute() -> false  // Always execute goals shouldn't be cached
                    mojoDescriptor.isOnlineRequired -> false  // Network-dependent goals shouldn't be cached
                    mojoDescriptor.isAggregator -> false  // Aggregator goals shouldn't be cached
                    mojoDescriptor.isThreadSafe -> true  // Thread-safe goals are good for caching
                    else -> false  // Default to false for safety
                }
            }
        } catch (e: Exception) {
            if (verbose) {
                log.info("[SimpleTargetGenerator] Maven API introspection failed for goal '$goal', using fallback: ${e.message}")
            }
        }
        
        // Fallback to basic heuristics
        return isCleanPhase(goal) || isCompilationGoal(goal)
    }
    
    /**
     * Check if goal is a clean phase (known to be cacheable)
     */
    private fun isCleanPhase(goal: String): Boolean {
        return goal in listOf("clean", "pre-clean", "post-clean") || goal.contains("clean:clean")
    }
    
    /**
     * Check if goal is a compilation goal (likely cacheable)
     */
    private fun isCompilationGoal(goal: String): Boolean {
        return goal.contains("compile") || goal.contains("resources") || goal.contains("jar")
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