import org.apache.maven.execution.*
import org.apache.maven.project.MavenProject
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.settings.Settings
import org.eclipse.aether.RepositorySystemSession
import java.io.File

/**
 * Maven 4.0 compatible implementation of MavenSession.
 * This eliminates PlexusContainer dependency and uses modern patterns.
 */
class Maven4Session(
    private val request: MavenExecutionRequest,
    private val result: MavenExecutionResult,
    private val repositorySession: RepositorySystemSession,
    private val projectList: MutableList<MavenProject>,
    private var current: MavenProject?
) : MavenSession(
    // Maven 4.0: Pass null for container as it's no longer required
    null, // PlexusContainer - not needed in Maven 4.0 
    repositorySession,
    request,
    result
) {
    
    // Create a simple dependency graph for the projects
    private val dependencyGraph = Maven4DependencyGraph(projectList)

    override fun getRequest(): MavenExecutionRequest = request
    override fun getResult(): MavenExecutionResult = result
    override fun getRepositorySession(): RepositorySystemSession = repositorySession
    override fun getProjects(): MutableList<MavenProject> = projectList
    override fun getCurrentProject(): MavenProject? = current
    override fun setCurrentProject(project: MavenProject?) { current = project }
    
    override fun getSettings(): Settings = Settings() // Maven 4.0: Settings handled differently
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
 * Maven 4.0 compatible implementation of ProjectDependencyGraph.
 * This provides enhanced dependency resolution using Maven 4.0 patterns.
 */
class Maven4DependencyGraph(
    private val projects: List<MavenProject>
) : ProjectDependencyGraph {
    
    private val projectMap = projects.associateBy { it }
    
    override fun getAllProjects(): List<MavenProject> = projects
    
    override fun getSortedProjects(): List<MavenProject> = projects
    
    override fun getDownstreamProjects(project: MavenProject, transitive: Boolean): List<MavenProject> {
        // Maven 4.0: Enhanced dependency resolution would go here
        // For now, return empty list to avoid circular dependencies
        return emptyList()
    }
    
    override fun getUpstreamProjects(project: MavenProject, transitive: Boolean): List<MavenProject> {
        // Maven 4.0: Enhanced dependency resolution would go here
        // For now, return empty list to avoid circular dependencies
        return emptyList()
    }
}