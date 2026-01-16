import org.apache.maven.execution.MavenSession
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Dependency
import org.apache.maven.project.MavenProject
import java.util.concurrent.ConcurrentHashMap

/**
 * Context for managing Maven Embedder session state and caching.
 * Provides centralized management of execution results, artifacts, and dependencies.
 */
class EmbedderSessionContext(
    val mavenSession: MavenSession
) {
    // Task execution results cache
    private val _executionResults = ConcurrentHashMap<String, TaskExecutionResult>()
    
    // Artifact resolution cache
    private val _artifactCache = ConcurrentHashMap<String, Artifact>()
    
    // Dependency resolution cache
    private val _dependencyCache = ConcurrentHashMap<String, List<Dependency>>()
    
    // Plugin resolution cache
    private val _pluginCache = ConcurrentHashMap<String, org.apache.maven.plugin.descriptor.PluginDescriptor>()
    
    // Session-level properties
    private val _sessionProperties = ConcurrentHashMap<String, Any>()
    
    /**
     * Get all execution results
     */
    val executionResults: Map<String, TaskExecutionResult>
        get() = _executionResults.toMap()
    
    /**
     * Get artifact cache
     */
    val artifactCache: Map<String, Artifact>
        get() = _artifactCache.toMap()
    
    /**
     * Get dependency cache
     */
    val dependencyCache: Map<String, List<Dependency>>
        get() = _dependencyCache.toMap()
    
    /**
     * Get plugin cache
     */
    val pluginCache: Map<String, org.apache.maven.plugin.descriptor.PluginDescriptor>
        get() = _pluginCache.toMap()
    
    /**
     * Get session properties
     */
    val sessionProperties: Map<String, Any>
        get() = _sessionProperties.toMap()
    
    /**
     * Store task execution result
     */
    fun storeExecutionResult(taskId: String, result: TaskExecutionResult) {
        _executionResults[taskId] = result
    }
    
    /**
     * Get task execution result
     */
    fun getExecutionResult(taskId: String): TaskExecutionResult? {
        return _executionResults[taskId]
    }
    
    /**
     * Cache resolved artifact
     */
    fun cacheArtifact(key: String, artifact: Artifact) {
        _artifactCache[key] = artifact
    }
    
    /**
     * Get cached artifact
     */
    fun getCachedArtifact(key: String): Artifact? {
        return _artifactCache[key]
    }
    
    /**
     * Cache resolved dependencies for a project
     */
    fun cacheDependencies(projectKey: String, dependencies: List<Dependency>) {
        _dependencyCache[projectKey] = dependencies
    }
    
    /**
     * Get cached dependencies for a project
     */
    fun getCachedDependencies(projectKey: String): List<Dependency>? {
        return _dependencyCache[projectKey]
    }
    
    /**
     * Cache plugin descriptor
     */
    fun cachePlugin(pluginKey: String, plugin: org.apache.maven.plugin.descriptor.PluginDescriptor) {
        _pluginCache[pluginKey] = plugin
    }
    
    /**
     * Get cached plugin descriptor
     */
    fun getCachedPlugin(pluginKey: String): org.apache.maven.plugin.descriptor.PluginDescriptor? {
        return _pluginCache[pluginKey]
    }
    
    /**
     * Set session property
     */
    fun setSessionProperty(key: String, value: Any) {
        _sessionProperties[key] = value
    }
    
    /**
     * Get session property
     */
    fun getSessionProperty(key: String): Any? {
        return _sessionProperties[key]
    }
    
    /**
     * Generate artifact key for caching
     */
    fun generateArtifactKey(groupId: String, artifactId: String, version: String, type: String = "jar", classifier: String? = null): String {
        return if (classifier != null) {
            "$groupId:$artifactId:$version:$type:$classifier"
        } else {
            "$groupId:$artifactId:$version:$type"
        }
    }
    
    /**
     * Generate plugin key for caching
     */
    fun generatePluginKey(groupId: String, artifactId: String, version: String? = null): String {
        return if (version != null) {
            "$groupId:$artifactId:$version"
        } else {
            "$groupId:$artifactId"
        }
    }
    
    /**
     * Generate project key for caching
     */
    fun generateProjectKey(project: MavenProject): String {
        return "${project.groupId}:${project.artifactId}:${project.version}"
    }
    
    /**
     * Clear all caches
     */
    fun clearCaches() {
        _executionResults.clear()
        _artifactCache.clear()
        _dependencyCache.clear()
        _pluginCache.clear()
        _sessionProperties.clear()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Int> {
        return mapOf(
            "executionResults" to _executionResults.size,
            "artifacts" to _artifactCache.size,
            "dependencies" to _dependencyCache.size,
            "plugins" to _pluginCache.size,
            "sessionProperties" to _sessionProperties.size
        )
    }
    
    /**
     * Get session summary
     */
    fun getSessionSummary(): String {
        val stats = getCacheStats()
        val totalTasks = _executionResults.size
        val successfulTasks = _executionResults.values.count { it.success }
        val failedTasks = totalTasks - successfulTasks
        
        return """
            |Maven Embedder Session Summary:
            |  Tasks: $totalTasks total, $successfulTasks successful, $failedTasks failed
            |  Cached artifacts: ${stats["artifacts"]}
            |  Cached dependencies: ${stats["dependencies"]}
            |  Cached plugins: ${stats["plugins"]}
            |  Session properties: ${stats["sessionProperties"]}
        """.trimMargin()
    }
}