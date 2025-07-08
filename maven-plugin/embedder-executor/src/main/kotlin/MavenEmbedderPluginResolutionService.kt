
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.PluginManager
import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.plugin.descriptor.MojoDescriptor
import org.apache.maven.plugin.MojoExecution
import org.apache.maven.plugin.PluginResolutionException
import org.apache.maven.plugin.PluginDescriptorParsingException
import org.apache.maven.project.MavenProject
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.plugin.version.PluginVersionResolutionException
import org.apache.maven.plugin.version.PluginVersionResolver
import org.codehaus.plexus.component.repository.exception.ComponentLookupException
import org.codehaus.plexus.PlexusContainer
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced plugin resolution service that uses Maven Embedder APIs for proper
 * plugin resolution, dependency management, and descriptor analysis.
 */
class MavenEmbedderPluginResolutionService(
    private val plexusContainer: PlexusContainer,
    private val session: MavenSession?,
    private val sessionContext: EmbedderSessionContext?,
    private val verbose: Boolean = false
) {
    
    private val pluginManager: PluginManager by lazy {
        try {
            plexusContainer.lookup("org.apache.maven.plugin.PluginManager") as PluginManager
        } catch (e: ComponentLookupException) {
            throw RuntimeException("Failed to lookup PluginManager", e)
        }
    }
    
    private val pluginVersionResolver: PluginVersionResolver? by lazy {
        try {
            plexusContainer.lookup("org.apache.maven.plugin.version.PluginVersionResolver") as PluginVersionResolver
        } catch (e: ComponentLookupException) {
            if (verbose) {
                println("Warning: Failed to lookup PluginVersionResolver in test environment: ${e.message}")
            }
            null
        }
    }
    
    // Cache for resolved plugin descriptors
    private val pluginDescriptorCache = ConcurrentHashMap<String, PluginDescriptor>()
    
    // Cache for resolved mojo descriptors
    private val mojoDescriptorCache = ConcurrentHashMap<String, MojoDescriptor>()

    /**
     * Resolve a Maven plugin by its coordinates (groupId:artifactId or groupId:artifactId:version)
     */
    fun resolvePlugin(pluginKey: String, project: MavenProject): PluginDescriptor? {
        // Check cache first
        val cached = sessionContext?.getCachedPlugin(pluginKey)
        if (cached != null) {
            if (verbose) {
                println("Using cached plugin descriptor for: $pluginKey")
            }
            return cached
        }
        
        return try {
            val (groupId, artifactId, version) = parsePluginKey(pluginKey)
            
            if (verbose) {
                println("Resolving plugin: $groupId:$artifactId:$version")
            }
            
            // Create plugin artifact
            val pluginArtifact = createPluginArtifact(groupId, artifactId, version, project)
            
            // Setup plugin for resolution
            val plugin = org.apache.maven.model.Plugin().apply {
                this.groupId = groupId
                this.artifactId = artifactId
                this.version = version
            }
            
            // For now, return a dummy plugin descriptor
            // TODO: Implement proper plugin resolution
            val pluginDescriptor = null
            
            if (verbose) {
                println("Plugin resolution not yet implemented for: $pluginKey")
            }
            
            pluginDescriptor
            
        } catch (e: PluginResolutionException) {
            if (verbose) {
                println("Failed to resolve plugin $pluginKey: ${e.message}")
            }
            null
        } catch (e: PluginDescriptorParsingException) {
            if (verbose) {
                println("Failed to parse plugin descriptor for $pluginKey: ${e.message}")
            }
            null
        } catch (e: Exception) {
            if (verbose) {
                println("Unexpected error resolving plugin $pluginKey: ${e.message}")
            }
            null
        }
    }

    /**
     * Resolve a specific goal within a plugin to get its MojoDescriptor
     */
    fun resolveGoal(goal: String, project: MavenProject): MojoDescriptor? {
        val cacheKey = "${project.id}:$goal"
        
        // Check cache first
        val cached = mojoDescriptorCache[cacheKey]
        if (cached != null) {
            if (verbose) {
                println("Using cached mojo descriptor for: $goal")
            }
            return cached
        }
        
        return try {
            val (pluginKey, goalName) = parseGoal(goal)
            
            if (verbose) {
                println("Resolving goal: $goal (plugin: $pluginKey, goal: $goalName)")
            }
            
            // Resolve the plugin first
            val pluginDescriptor = resolvePlugin(pluginKey, project)
                ?: return null
            
            // Find the specific mojo
            val mojoDescriptor = pluginDescriptor.mojos?.find { it.goal == goalName }
            
            if (mojoDescriptor != null) {
                // Cache the result
                mojoDescriptorCache[cacheKey] = mojoDescriptor
                
                if (verbose) {
                    println("Successfully resolved goal: $goal")
                    println("  Implementation: ${mojoDescriptor.implementation}")
                    println("  Phase: ${mojoDescriptor.phase}")
                }
            } else {
                if (verbose) {
                    println("Goal $goalName not found in plugin $pluginKey")
                }
            }
            
            mojoDescriptor
            
        } catch (e: Exception) {
            if (verbose) {
                println("Failed to resolve goal $goal: ${e.message}")
            }
            null
        }
    }

    /**
     * Analyze goal dependencies - what artifacts and dependencies are needed for this goal
     */
    fun analyzeGoalDependencies(goal: String, project: MavenProject): List<Artifact> {
        val dependencies = mutableListOf<Artifact>()
        
        try {
            val mojoDescriptor = resolveGoal(goal, project)
                ?: return dependencies
            
            // Get plugin dependencies
            mojoDescriptor.pluginDescriptor?.let { pluginDescriptor ->
                pluginDescriptor.dependencies?.forEach { dependency ->
                    val artifact = DefaultArtifact(
                        dependency.groupId,
                        dependency.artifactId,
                        dependency.version,
                        "runtime",  // Use fixed scope
                        dependency.type ?: "jar",
                        "",  // Use empty string for classifier
                        DefaultArtifactHandler(dependency.type ?: "jar")
                    )
                    dependencies.add(artifact)
                }
            }
            
            if (verbose && dependencies.isNotEmpty()) {
                println("Goal $goal has ${dependencies.size} dependencies:")
                dependencies.forEach { dep ->
                    println("  ${dep.groupId}:${dep.artifactId}:${dep.version}")
                }
            }
            
        } catch (e: Exception) {
            if (verbose) {
                println("Failed to analyze dependencies for goal $goal: ${e.message}")
            }
        }
        
        return dependencies
    }

    /**
     * Get all available goals for a plugin
     */
    fun getPluginGoals(pluginKey: String, project: MavenProject): List<String> {
        return try {
            val pluginDescriptor = resolvePlugin(pluginKey, project)
                ?: return emptyList()
            
            pluginDescriptor.mojos?.map { it.goal } ?: emptyList()
            
        } catch (e: Exception) {
            if (verbose) {
                println("Failed to get goals for plugin $pluginKey: ${e.message}")
            }
            emptyList()
        }
    }

    /**
     * Check if a plugin is available for resolution
     */
    fun isPluginAvailable(pluginKey: String, project: MavenProject): Boolean {
        return try {
            resolvePlugin(pluginKey, project) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolve plugin version if not specified
     */
    fun resolvePluginVersion(groupId: String, artifactId: String, project: MavenProject): String? {
        return try {
            if (pluginVersionResolver == null) {
                if (verbose) {
                    println("PluginVersionResolver not available, returning null for $groupId:$artifactId")
                }
                return null
            }
            
            val plugin = org.apache.maven.model.Plugin().apply {
                this.groupId = groupId
                this.artifactId = artifactId
            }
            
            val versionResult = pluginVersionResolver!!.resolve(
                org.apache.maven.plugin.version.DefaultPluginVersionRequest(plugin, session)
            )
            
            versionResult.version
            
        } catch (e: PluginVersionResolutionException) {
            if (verbose) {
                println("Failed to resolve version for plugin $groupId:$artifactId: ${e.message}")
            }
            null
        }
    }

    /**
     * Parse plugin key into components (groupId:artifactId or groupId:artifactId:version)
     */
    private fun parsePluginKey(pluginKey: String): Triple<String, String, String?> {
        val parts = pluginKey.split(":")
        
        return when (parts.size) {
            2 -> {
                val (groupId, artifactId) = parts
                Triple(groupId, artifactId, null)
            }
            3 -> {
                val (groupId, artifactId, version) = parts
                Triple(groupId, artifactId, version)
            }
            else -> throw IllegalArgumentException("Invalid plugin key format: $pluginKey. Expected groupId:artifactId or groupId:artifactId:version")
        }
    }

    /**
     * Parse goal into plugin key and goal name
     */
    private fun parseGoal(goal: String): Pair<String, String> {
        return when {
            goal.contains(":") -> {
                val parts = goal.split(":")
                when (parts.size) {
                    2 -> {
                        // Assume standard Maven plugin naming convention
                        val goalName = parts[1]
                        val pluginArtifactId = if (parts[0].endsWith("-maven-plugin")) {
                            parts[0]
                        } else {
                            "${parts[0]}-maven-plugin"
                        }
                        Pair("org.apache.maven.plugins:$pluginArtifactId", goalName)
                    }
                    3 -> {
                        // groupId:artifactId:goal
                        val (groupId, artifactId, goalName) = parts
                        Pair("$groupId:$artifactId", goalName)
                    }
                    4 -> {
                        // groupId:artifactId:version:goal
                        val (groupId, artifactId, version, goalName) = parts
                        Pair("$groupId:$artifactId:$version", goalName)
                    }
                    else -> throw IllegalArgumentException("Invalid goal format: $goal")
                }
            }
            else -> {
                // Assume it's a lifecycle phase, try to resolve from standard plugins
                Pair("org.apache.maven.plugins:maven-compiler-plugin", goal)
            }
        }
    }

    /**
     * Create plugin artifact for resolution
     */
    private fun createPluginArtifact(groupId: String, artifactId: String, version: String?, project: MavenProject): Artifact {
        val resolvedVersion = version ?: resolvePluginVersion(groupId, artifactId, project)
            ?: throw PluginVersionResolutionException(groupId, artifactId, "Unable to resolve plugin version")
        
        return DefaultArtifact(
            groupId,
            artifactId,
            resolvedVersion,
            "runtime",
            "maven-plugin",
            null,
            DefaultArtifactHandler("maven-plugin")
        )
    }

    /**
     * Get resolution statistics
     */
    fun getResolutionStats(): Map<String, Int> {
        return mapOf(
            "pluginDescriptors" to pluginDescriptorCache.size,
            "mojoDescriptors" to mojoDescriptorCache.size,
            "cachedPlugins" to (sessionContext?.pluginCache?.size ?: 0)
        )
    }

    /**
     * Clear all caches
     */
    fun clearCaches() {
        pluginDescriptorCache.clear()
        mojoDescriptorCache.clear()
        sessionContext?.clearCaches()
    }
}