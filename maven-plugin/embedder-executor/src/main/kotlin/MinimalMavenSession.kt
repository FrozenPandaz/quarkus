
import org.apache.maven.execution.*
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.ProjectDependencyGraph
import org.apache.maven.project.MavenProject
// Removed RepositorySystem import - not needed in Maven 4.0
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.settings.Settings
import org.eclipse.aether.RepositorySystemSession
import java.io.File

/**
 * Minimal implementation of MavenSession for Maven 3.8.8 compatibility.
 * This is used as a fallback when Maven's built-in session creation fails.
 */
class MinimalMavenSession(
    private val request: MavenExecutionRequest,
    private val result: MavenExecutionResult,
    private val repositorySession: RepositorySystemSession,
    private val projectList: MutableList<MavenProject>,
    private var current: MavenProject?
) : MavenSession(
    null, // PlexusContainer
    repositorySession,
    request,
    result
) {
    
    // Create a simple dependency graph for the projects
    private val dependencyGraph = SimpleDependencyGraph(projectList)

    override fun getRequest(): MavenExecutionRequest = request
    override fun getResult(): MavenExecutionResult = result
    override fun getRepositorySession(): RepositorySystemSession = repositorySession
    override fun getProjects(): MutableList<MavenProject> = projectList
    override fun getCurrentProject(): MavenProject? = current
    override fun setCurrentProject(project: MavenProject?) { current = project }
    
    override fun getSettings(): Settings = Settings()
    override fun getLocalRepository(): ArtifactRepository? = null
    override fun getExecutionRootDirectory(): String? = request.baseDirectory?.toString()
    override fun isOffline(): Boolean = request.isOffline
    override fun isParallel(): Boolean = request.getDegreeOfConcurrency() > 1
    
    override fun getSystemProperties(): java.util.Properties = request.systemProperties
    override fun getUserProperties(): java.util.Properties = request.userProperties
    override fun getStartTime(): java.util.Date = request.startTime ?: java.util.Date()
    
    // Additional required methods
    override fun getProjectDependencyGraph(): ProjectDependencyGraph = dependencyGraph
    override fun getAllProjects(): MutableList<MavenProject> = projectList
    override fun getProjectMap(): MutableMap<String, MavenProject> {
        return projectList.associateBy { "${it.groupId}:${it.artifactId}" }.toMutableMap()
    }
    override fun getPluginContext(
        plugin: org.apache.maven.plugin.descriptor.PluginDescriptor, 
        project: MavenProject
    ): MutableMap<String, Any> {
        return mutableMapOf()
    }
}

/**
 * Simple implementation of ProjectDependencyGraph for basic Maven reactor functionality.
 * This provides the minimum required functionality to prevent NullPointerException.
 */
class SimpleDependencyGraph(
    private val projects: List<MavenProject>
) : ProjectDependencyGraph {
    
    private val projectMap = projects.associateBy { it }
    
    override fun getAllProjects(): List<MavenProject> = projects
    
    override fun getSortedProjects(): List<MavenProject> = projects
    
    override fun getDownstreamProjects(project: MavenProject, transitive: Boolean): List<MavenProject> {
        // For simple cases, return empty list (no downstream dependencies)
        return emptyList()
    }
    
    override fun getUpstreamProjects(project: MavenProject, transitive: Boolean): List<MavenProject> {
        // For simple cases, return empty list (no upstream dependencies)
        return emptyList()
    }
}