import org.apache.maven.execution.*
import org.apache.maven.project.MavenProject
import org.eclipse.aether.RepositorySystemSession
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Maven 4.0 compatible session factory using JSR-330 dependency injection.
 * This eliminates the need for PlexusContainer and uses modern DI patterns.
 */
@Named
@Singleton
class Maven4SessionFactory @Inject constructor(
    private val sessionBuilder: MavenSessionBuilder? = null,
    private val verbose: Boolean = false
) {
    
    // Cache for created sessions to avoid recreation overhead
    private val sessionCache = ConcurrentHashMap<String, MavenSession>()
    
    /**
     * Create a Maven session for goal execution using Maven 4.0 patterns
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
                println("Creating new Maven 4.0 session for: ${currentProject?.artifactId ?: "root"}")
            }
            
            // Create Maven execution result
            val executionResult = DefaultMavenExecutionResult()
            
            // Create Maven session using Maven 4.0 compatible approach
            val session = createMaven4Session(
                executionRequest,
                executionResult,
                repositorySystemSession,
                projects,
                currentProject
            )
            
            // Cache the session
            sessionCache[sessionKey] = session
            
            if (verbose) {
                println("Created Maven 4.0 session successfully")
                println("  Project: ${currentProject?.artifactId ?: "root"}")
                println("  Projects in reactor: ${projects.size}")
                println("  Goals: ${executionRequest.goals?.joinToString(", ") ?: "none"}")
            }
            
            return session
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to create Maven 4.0 session: ${e.message}", e)
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
     * Create a Maven 4.0 compatible session
     */
    private fun createMaven4Session(
        executionRequest: MavenExecutionRequest,
        executionResult: MavenExecutionResult,
        repositorySystemSession: RepositorySystemSession,
        projects: List<MavenProject>,
        currentProject: MavenProject?
    ): MavenSession {
        
        if (verbose) {
            println("Using Maven 4.0 session creation (JSR-330 compatible)")
        }
        
        // Try using sessionBuilder if available (Maven 4.0 pattern)
        sessionBuilder?.let { builder ->
            try {
                return builder
                    .withRequest(executionRequest)
                    .withResult(executionResult)
                    .withRepositorySession(repositorySystemSession)
                    .withProjects(projects)
                    .withCurrentProject(currentProject)
                    .build()
            } catch (e: Exception) {
                if (verbose) {
                    println("Warning: SessionBuilder failed, falling back to direct construction: ${e.message}")
                }
            }
        }
        
        // Fallback: Create Maven 4.0 compatible session directly
        return Maven4Session(
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
            println("Cleared Maven 4.0 session cache")
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

/**
 * Placeholder for Maven 4.0 session builder (if available)
 */
interface MavenSessionBuilder {
    fun withRequest(request: MavenExecutionRequest): MavenSessionBuilder
    fun withResult(result: MavenExecutionResult): MavenSessionBuilder
    fun withRepositorySession(session: RepositorySystemSession): MavenSessionBuilder
    fun withProjects(projects: List<MavenProject>): MavenSessionBuilder
    fun withCurrentProject(project: MavenProject?): MavenSessionBuilder
    fun build(): MavenSession
}