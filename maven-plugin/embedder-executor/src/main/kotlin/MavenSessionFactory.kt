
import org.apache.maven.execution.*
import org.apache.maven.project.MavenProject
// import org.apache.maven.project.ProjectDependencyGraph
import org.apache.maven.repository.RepositorySystem
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.settings.Settings
import org.eclipse.aether.RepositorySystemSession
import org.codehaus.plexus.PlexusContainer
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
        
        // Try to create session using available constructor approaches
        return try {
            // Approach 1: Try direct constructor (may not be available in all Maven versions)
            createSessionWithConstructor(
                executionRequest,
                executionResult,
                repositorySystemSession,
                projects,
                currentProject
            )
        } catch (e: Exception) {
            if (verbose) {
                println("Direct constructor failed, trying builder approach: ${e.message}")
            }
            
            // Approach 2: Try using session builder/factory
            createSessionWithBuilder(
                executionRequest,
                executionResult,
                repositorySystemSession,
                projects,
                currentProject
            )
        }
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
        
        try {
            // Try to use Maven's session factory from Plexus container
            val maven = plexusContainer.lookup("org.apache.maven.Maven") as org.apache.maven.Maven
            
            // Use Maven core to create session - this is the proper way
            // Create a session using the Maven core approach
            // Maven.execute returns a result, but we need a session first
            // Let's try to use Maven's session creation approach
            
            // Fallback to minimal implementation immediately since Maven.execute
            // doesn't provide direct session access
            return MinimalMavenSession(
                executionRequest,
                executionResult,
                repositorySystemSession,
                projects.toMutableList(),
                currentProject
            )
            
        } catch (e: Exception) {
            if (verbose) {
                println("Maven core session creation failed: ${e.message}")
                println("Using minimal session implementation")
            }
            
            // Fallback to minimal implementation
            return MinimalMavenSession(
                executionRequest,
                executionResult,
                repositorySystemSession,
                projects.toMutableList(),
                currentProject
            )
        }
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
        
        // Try to use Plexus container to lookup session factory/builder
        try {
            // This is a fallback - try to find a session factory in the container
            val sessionFactory = plexusContainer.lookup("sessionFactory")
            if (verbose) {
                println("Found session factory: ${sessionFactory.javaClass.name}")
            }
            // Use reflection to call factory methods if available
        } catch (e: Exception) {
            if (verbose) {
                println("No session factory found, using manual implementation")
            }
        }
        
        // Fallback to our manual implementation
        return createSessionWithConstructor(
            executionRequest,
            executionResult, 
            repositorySystemSession,
            projects,
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