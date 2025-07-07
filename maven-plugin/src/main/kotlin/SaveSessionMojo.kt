import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.apache.maven.artifact.Artifact
import java.io.File

/**
 * Maven goal to save session context to disk for Nx batch executor
 */
@Mojo(name = "save-session", defaultPhase = LifecyclePhase.INSTALL)
class SaveSessionMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${session}", readonly = true)
    private lateinit var session: MavenSession

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Throws(MojoExecutionException::class)
    override fun execute() {
        // Only execute if invoked via Nx batch executor
        val nxSessionEnabled = session.userProperties.getProperty("nx.session.enabled")
        if (nxSessionEnabled != "true") {
            log.debug("Skipping nx:save-session - not invoked via Nx batch executor")
            return
        }

        log.info("Saving session context for Nx batch execution...")

        try {
            // Get workspace root directory
            val workspaceRoot = findWorkspaceRoot()
            val sessionDir = File(workspaceRoot, ".nx-maven-sessions")
            sessionDir.mkdirs()

            // Save sessions only for projects in the current reactor (being executed)
            val reactorProjects = session.projects
            log.debug("Saving sessions for ${reactorProjects.size} reactor projects")

            for (project in reactorProjects) {
                saveProjectSession(project, sessionDir)
            }

            log.info("Session saving completed")

        } catch (e: Exception) {
            log.warn("Failed to save session context: ${e.message}")
            if (log.isDebugEnabled) {
                log.debug("Session saving error details", e)
            }
        }
    }

    /**
     * Save session data for a specific project
     */
    private fun saveProjectSession(project: MavenProject, sessionDir: File) {
        try {
            val sessionFileName = "${project.artifactId.replace("/", "_")}.json"
            val sessionFile = File(sessionDir, sessionFileName)

            log.debug("Saving session for project: ${project.artifactId}")

            val sessionData = mutableMapOf<String, Any?>()

            // Capture user properties (filter out system properties to avoid noise)
            val userProps = mutableMapOf<String, String>()
            session.userProperties.forEach { key, value ->
                val keyStr = key.toString()
                val valueStr = value.toString()
                // Only save user-defined and nx-specific properties
                if (keyStr.startsWith("nx.") || 
                    keyStr.startsWith("maven.") || 
                    !keyStr.contains(".")) {
                    userProps[keyStr] = valueStr
                }
            }
            sessionData["userProperties"] = userProps

            // Capture project artifacts
            val artifacts = mutableListOf<Map<String, String?>>()
            
            // Main project artifact
            project.artifact?.let { artifact ->
                if (artifact.file != null && artifact.file.exists()) {
                    artifacts.add(mapOf(
                        "file" to artifact.file.absolutePath,
                        "groupId" to artifact.groupId,
                        "artifactId" to artifact.artifactId,
                        "version" to artifact.version,
                        "type" to artifact.type,
                        "classifier" to artifact.classifier,
                        "scope" to artifact.scope
                    ))
                    log.debug("Captured main artifact: ${artifact.file.absolutePath}")
                }
            }

            // Attached artifacts (sources, javadoc, tests, etc.)
            project.attachedArtifacts?.forEach { artifact ->
                if (artifact.file != null && artifact.file.exists()) {
                    artifacts.add(mapOf(
                        "file" to artifact.file.absolutePath,
                        "groupId" to artifact.groupId,
                        "artifactId" to artifact.artifactId,
                        "version" to artifact.version,
                        "type" to artifact.type,
                        "classifier" to artifact.classifier,
                        "scope" to artifact.scope
                    ))
                    log.debug("Captured attached artifact: ${artifact.file.absolutePath}")
                }
            }

            sessionData["artifacts"] = artifacts

            // Capture resolved dependencies with their file locations
            val dependencies = mutableListOf<Map<String, String?>>()
            project.artifacts?.forEach { artifact ->
                if (artifact.file != null && artifact.file.exists()) {
                    dependencies.add(mapOf(
                        "file" to artifact.file.absolutePath,
                        "groupId" to artifact.groupId,
                        "artifactId" to artifact.artifactId,
                        "version" to artifact.version,
                        "type" to artifact.type,
                        "classifier" to artifact.classifier,
                        "scope" to artifact.scope
                    ))
                }
            }
            sessionData["dependencies"] = dependencies

            // Capture build directories
            sessionData["buildDirectory"] = project.build.directory
            sessionData["outputDirectory"] = project.build.outputDirectory
            sessionData["testOutputDirectory"] = project.build.testOutputDirectory
            sessionData["sourceDirectory"] = project.build.sourceDirectory
            sessionData["testSourceDirectory"] = project.build.testSourceDirectory

            // Capture local repository path
            sessionData["localRepository"] = session.repositorySession.localRepository.basedir.absolutePath

            // Capture goal execution results
            val goalResults = captureGoalResults(project)
            sessionData["goalResults"] = goalResults

            // Capture execution timestamp
            sessionData["executionTimestamp"] = System.currentTimeMillis()
            sessionData["executionDate"] = java.time.Instant.now().toString()

            // Capture project metadata
            sessionData["projectInfo"] = mapOf(
                "groupId" to project.groupId,
                "artifactId" to project.artifactId,
                "version" to project.version,
                "packaging" to project.packaging,
                "name" to project.name,
                "basedir" to project.basedir.absolutePath
            )

            // Write session data to file
            sessionFile.writeText(gson.toJson(sessionData))
            log.info("Saved session for project: ${project.artifactId} -> ${sessionFile.absolutePath}")

        } catch (e: Exception) {
            log.warn("Failed to save session for project ${project.artifactId}: ${e.message}")
            if (log.isDebugEnabled) {
                log.debug("Session saving error for ${project.artifactId}", e)
            }
        }
    }

    /**
     * Capture goal execution results and timing information
     */
    private fun captureGoalResults(project: MavenProject): Map<String, Any> {
        val results = mutableMapOf<String, Any>()
        
        try {
            // Capture basic execution context
            results["projectBasedir"] = project.basedir.absolutePath
            results["executionRootDirectory"] = session.executionRootDirectory ?: ""
            
            // Capture current request goals
            val requestGoals = session.request?.goals ?: emptyList<String>()
            results["requestedGoals"] = requestGoals
            
            // Capture lifecycle phase information if available
            val lifecyclePhases = mutableListOf<Map<String, Any>>()
            session.request?.goals?.forEach { goal ->
                val phaseInfo = mutableMapOf<String, Any>()
                phaseInfo["goal"] = goal
                phaseInfo["timestamp"] = System.currentTimeMillis()
                
                // Try to extract plugin info from goal
                if (goal.contains(":")) {
                    val parts = goal.split(":")
                    if (parts.size >= 2) {
                        phaseInfo["plugin"] = "${parts[0]}:${parts[1]}"
                        if (parts.size >= 3) {
                            phaseInfo["goalName"] = parts[2]
                        }
                        if (parts.size >= 4) {
                            phaseInfo["execution"] = parts[3]
                        }
                    }
                }
                
                lifecyclePhases.add(phaseInfo)
            }
            results["lifecyclePhases"] = lifecyclePhases
            
            // Capture execution success indicators
            results["buildSuccess"] = !session.result.hasExceptions()
            if (session.result.hasExceptions()) {
                val exceptions = session.result?.exceptions?.map { 
                    mapOf(
                        "message" to (it.message ?: "Unknown error"),
                        "type" to it.javaClass.simpleName
                    )
                } ?: emptyList<Map<String, String>>()
                results["exceptions"] = exceptions
            }
            
            // Capture session timing
            results["sessionStartTime"] = session.request?.startTime?.time ?: 0L
            results["currentTime"] = System.currentTimeMillis()
            
            log.debug("Captured goal results for project: ${project.artifactId}")
            
        } catch (e: Exception) {
            log.warn("Failed to capture complete goal results: ${e.message}")
            results["captureError"] = e.message ?: "Unknown capture error"
        }
        
        return results
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