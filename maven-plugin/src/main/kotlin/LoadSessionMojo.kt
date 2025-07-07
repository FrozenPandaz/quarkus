import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import java.io.File
import java.util.Properties

/**
 * Maven goal to load session context from disk for Nx batch executor
 */
@Mojo(name = "load-session", defaultPhase = LifecyclePhase.INITIALIZE)
class LoadSessionMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${session}", readonly = true)
    private lateinit var session: MavenSession

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Throws(MojoExecutionException::class)
    override fun execute() {
        // Only execute if invoked via Nx batch executor
        val nxSessionEnabled = session.userProperties.getProperty("nx.session.enabled")
        if (nxSessionEnabled != "true") {
            log.debug("Skipping nx:load-session - not invoked via Nx batch executor")
            return
        }

        log.info("Loading session context for Nx batch execution...")

        try {
            // Get workspace root directory
            val workspaceRoot = findWorkspaceRoot()
            val sessionDir = File(workspaceRoot, ".nx-maven-sessions")

            if (!sessionDir.exists()) {
                log.debug("Session directory does not exist: ${sessionDir.absolutePath}")
                return
            }

            // Load sessions for all projects in the reactor
            val allProjects = session.allProjects
            log.debug("Loading sessions for ${allProjects.size} projects")

            for (project in allProjects) {
                loadProjectSession(project, sessionDir)
            }

            log.info("Session loading completed")

        } catch (e: Exception) {
            log.warn("Failed to load session context: ${e.message}")
            if (log.isDebugEnabled) {
                log.debug("Session loading error details", e)
            }
        }
    }

    /**
     * Load session data for a specific project
     */
    private fun loadProjectSession(project: MavenProject, sessionDir: File) {
        val sessionFileName = "${project.artifactId.replace("/", "_")}.json"
        val sessionFile = File(sessionDir, sessionFileName)

        if (!sessionFile.exists()) {
            log.debug("No session file found for project ${project.artifactId}")
            return
        }

        try {
            val json = sessionFile.readText()
            val sessionData = gson.fromJson(json, Map::class.java) as Map<String, Any?>

            log.debug("Loading session for project: ${project.artifactId}")

            // Load user properties
            sessionData["userProperties"]?.let { props ->
                if (props is Map<*, *>) {
                    props.forEach { (key, value) ->
                        val keyStr = key.toString()
                        val valueStr = value?.toString()
                        if (valueStr != null) {
                            session.userProperties.setProperty(keyStr, valueStr)
                            log.debug("Restored property: $keyStr = $valueStr")
                        }
                    }
                }
            }

            // Load artifact information and set as properties for other goals to use
            sessionData["artifacts"]?.let { artifacts ->
                if (artifacts is List<*>) {
                    artifacts.forEachIndexed { index, artifact ->
                        if (artifact is Map<*, *>) {
                            val file = artifact["file"]?.toString()
                            val type = artifact["type"]?.toString()
                            val classifier = artifact["classifier"]?.toString()

                            if (file != null && type != null) {
                                val propertyPrefix = "nx.${project.artifactId}.artifact.$index"
                                session.userProperties.setProperty("$propertyPrefix.file", file)
                                session.userProperties.setProperty("$propertyPrefix.type", type)
                                if (classifier != null) {
                                    session.userProperties.setProperty("$propertyPrefix.classifier", classifier)
                                }
                                log.debug("Restored artifact: $file ($type${if (classifier != null) ":$classifier" else ""})")
                            }
                        }
                    }
                }
            }

            // Load build directories
            sessionData["buildDirectory"]?.toString()?.let { buildDir ->
                session.userProperties.setProperty("nx.${project.artifactId}.build.directory", buildDir)
                log.debug("Restored build directory: $buildDir")
            }

            sessionData["outputDirectory"]?.toString()?.let { outputDir ->
                session.userProperties.setProperty("nx.${project.artifactId}.build.outputDirectory", outputDir)
                log.debug("Restored output directory: $outputDir")
            }

            // Load local repository path
            sessionData["localRepository"]?.toString()?.let { localRepo ->
                session.userProperties.setProperty("nx.${project.artifactId}.localRepository", localRepo)
                log.debug("Restored local repository: $localRepo")
            }

            log.info("Loaded session for project: ${project.artifactId}")

        } catch (e: Exception) {
            log.warn("Failed to load session for project ${project.artifactId}: ${e.message}")
            if (log.isDebugEnabled) {
                log.debug("Session loading error for ${project.artifactId}", e)
            }
        }
    }

    /**
     * Find the workspace root directory
     */
    private fun findWorkspaceRoot(): File {
        // Start from current project and walk up to find workspace root
        var current = session.executionRootDirectory?.let { File(it) } ?: session.currentProject.basedir
        
        // Look for common workspace indicators
        while (current != null && current.parentFile != null) {
            if (File(current, "nx.json").exists() ||
                File(current, "workspace.json").exists() ||
                File(current, ".git").exists()) {
                return current
            }
            current = current.parentFile
        }
        
        // Fallback to execution root or current project base
        return session.executionRootDirectory?.let { File(it) } ?: session.currentProject.basedir
    }
}