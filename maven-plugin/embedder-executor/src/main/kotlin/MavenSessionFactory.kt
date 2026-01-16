
import org.apache.maven.execution.*
import org.apache.maven.project.MavenProject
// import org.apache.maven.project.ProjectDependencyGraph
// Removed RepositorySystem import - no longer needed with direct construction
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.settings.Settings
import org.eclipse.aether.RepositorySystemSession
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.component.repository.exception.ComponentLookupException
import java.util.concurrent.ConcurrentHashMap
import java.io.File

/**
 * Factory for creating proper Maven sessions for the embedder batch executor.
 * Creates sessions that can be used for actual goal execution.
 */
class MavenSessionFactory(
    private val plexusContainer: PlexusContainer,
    private val verbose: Boolean = false
) {
    
    // Cache for created sessions to avoid recreation overhead
    private val sessionCache = ConcurrentHashMap<String, MavenSession>()
    
    /**
     * Create a Maven session for goal execution
     */
    fun createMavenSession(
        executionRequest: MavenExecutionRequest,
        repositorySystemSession: RepositorySystemSession,
        projects: List<MavenProject>,
        currentProject: MavenProject? = null
    ): MavenSession {
        
        val sessionKey = generateSessionKey(executionRequest, currentProject)
        
        // Check cache first
        sessionCache[sessionKey]?.let { cachedSession ->
            if (verbose) {
                println("Using cached Maven session for: ${currentProject?.artifactId ?: "root"}")
            }
            return cachedSession
        }
        
        try {
            if (verbose) {
                println("Creating new Maven session for: ${currentProject?.artifactId ?: "root"}")
            }
            
            // Create Maven execution result
            val executionResult = DefaultMavenExecutionResult()
            
            // Create Maven session using a compatible approach for Maven 3.8.8
            val session = createCompatibleMavenSession(
                executionRequest,
                executionResult,
                repositorySystemSession,
                projects,
                currentProject
            )
            
            // Cache the session
            sessionCache[sessionKey] = session
            
            if (verbose) {
                println("Created Maven session successfully")
                println("  Project: ${currentProject?.artifactId ?: "root"}")
                println("  Projects in reactor: ${projects.size}")
                println("  Goals: ${executionRequest.goals?.joinToString(", ") ?: "none"}")
            }
            
            return session
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to create Maven session: ${e.message}", e)
        }
    }
    
    /**
     * Create a session for a specific project and goals
     */
    fun createProjectSession(
        baseRequest: MavenExecutionRequest,
        repositorySystemSession: RepositorySystemSession,
        project: MavenProject,
        goals: List<String>
    ): MavenSession {
        
        // Create project-specific execution request
        val projectRequest = DefaultMavenExecutionRequest().apply {
            // Copy base settings
            baseRequest.baseDirectory?.let { setBaseDirectory(File(it)) }
            setPom(project.file)
            setGoals(goals)
            setLoggingLevel(baseRequest.loggingLevel)
            setInteractiveMode(baseRequest.isInteractiveMode)
            
            // Copy parallel execution settings
            setDegreeOfConcurrency(baseRequest.getDegreeOfConcurrency())
            
            // Copy properties
            systemProperties.putAll(baseRequest.systemProperties)
            userProperties.putAll(baseRequest.userProperties)
            
            // Copy profiles
            activeProfiles = baseRequest.activeProfiles?.toList() ?: emptyList()
            inactiveProfiles = baseRequest.inactiveProfiles?.toList() ?: emptyList()
            
            // Copy other settings
            setOffline(baseRequest.isOffline)
            setUpdateSnapshots(baseRequest.isUpdateSnapshots)
            setNoSnapshotUpdates(baseRequest.isNoSnapshotUpdates)
            setGlobalChecksumPolicy(baseRequest.globalChecksumPolicy)
            setLocalRepositoryPath(baseRequest.localRepositoryPath)
        }
        
        return createMavenSession(
            projectRequest,
            repositorySystemSession,
            listOf(project),
            project
        )
    }
    
    /**
     * Create a compatible Maven session that works with Maven 3.8.8
     */
    private fun createCompatibleMavenSession(
        executionRequest: MavenExecutionRequest,
        executionResult: MavenExecutionResult,
        repositorySystemSession: RepositorySystemSession,
        projects: List<MavenProject>,
        currentProject: MavenProject?
    ): MavenSession {
        
        // Use MinimalMavenSession directly to avoid any Maven class lookups
        // This completely bypasses the RepositorySystem dependency issue
        if (verbose) {
            println("Using MinimalMavenSession directly (Eclipse Sisu compatible)")
        }
        
        return MinimalMavenSession(
            executionRequest,
            executionResult,
            repositorySystemSession,
            projects.toMutableList(),
            currentProject
        )
    }
    
    /**
     * Create session using Maven's built-in approach
     */
    private fun createSessionWithConstructor(
        executionRequest: MavenExecutionRequest,
        executionResult: MavenExecutionResult,
        repositorySystemSession: RepositorySystemSession,
        projects: List<MavenProject>,
        currentProject: MavenProject?
    ): MavenSession {
        
        // Use MinimalMavenSession directly to avoid Maven class lookup
        // This eliminates the RepositorySystem dependency issue
        if (verbose) {
            println("Using MinimalMavenSession (direct construction approach)")
        }
        
        return MinimalMavenSession(
            executionRequest,
            executionResult,
            repositorySystemSession,
            projects.toMutableList(),
            currentProject
        )
    }
    
    /**
     * Create session using builder/factory approach
     */
    private fun createSessionWithBuilder(
        executionRequest: MavenExecutionRequest,
        executionResult: MavenExecutionResult,
        repositorySystemSession: RepositorySystemSession,
        projects: List<MavenProject>,
        currentProject: MavenProject?
    ): MavenSession {
        
        // Use MinimalMavenSession directly to avoid any Maven class lookups
        if (verbose) {
            println("Using MinimalMavenSession (builder approach)")
        }
        
        return MinimalMavenSession(
            executionRequest,
            executionResult,
            repositorySystemSession,
            projects.toMutableList(),
            currentProject
        )
    }
    
    /**
     * Generate cache key for session
     */
    private fun generateSessionKey(
        executionRequest: MavenExecutionRequest,
        currentProject: MavenProject?
    ): String {
        return buildString {
            append(executionRequest.baseDirectory?.toString() ?: "")
            append(":")
            append(currentProject?.artifactId ?: "root")
            append(":")
            append(executionRequest.goals?.joinToString(",") ?: "")
        }
    }
    
    /**
     * Clear session cache
     */
    fun clearCache() {
        sessionCache.clear()
        if (verbose) {
            println("Cleared Maven session cache")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Int> {
        return mapOf(
            "cachedSessions" to sessionCache.size
        )
    }
}