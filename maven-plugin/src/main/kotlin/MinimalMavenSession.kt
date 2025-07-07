import org.apache.maven.execution.*
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.project.MavenProject
// import org.apache.maven.project.ProjectDependencyGraph
// Try alternative import
// import org.apache.maven.execution.ProjectDependencyGraph
import org.apache.maven.repository.RepositorySystem
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
    // Use @Suppress to ignore the type mismatch temporarily
    @Suppress("RETURN_TYPE_MISMATCH_ON_OVERRIDE")
    override fun getProjectDependencyGraph(): Any? = null
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