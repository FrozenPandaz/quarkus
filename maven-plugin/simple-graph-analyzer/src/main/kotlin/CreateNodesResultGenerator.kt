import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.apache.maven.plugin.logging.Log
import model.*
import java.io.File

/**
 * Generates CreateNodesV2 compatible results for Nx integration
 */
class CreateNodesResultGenerator(
    private val session: MavenSession,
    private val reactorProjects: List<MavenProject>,
    private val verbose: Boolean,
    private val log: Log
) {
    
    /**
     * Generate CreateNodesV2 results with custom target generation
     */
    fun generateCreateNodesResults(targetGenerator: (ProjectConfiguration) -> Map<String, TargetConfiguration>): List<CreateNodesV2Entry> {
        val results = mutableListOf<CreateNodesV2Entry>()
        val workspaceRoot = File(session.executionRootDirectory)
        
        logInfo("Generating create nodes results for ${reactorProjects.size} projects")
        
        for (project in reactorProjects) {
            try {
                val pomFile = File(project.basedir, "pom.xml")
                
                // Create relative path to pom.xml
                val pomPath = getRelativePath(workspaceRoot, pomFile).let {
                    if (it.isEmpty()) "pom.xml" else it
                }
                
                logInfo("Processing project: ${project.artifactId} at path: $pomPath")
                
                // Generate the project configuration
                val projectConfig = generateProjectConfiguration(project, workspaceRoot)
                
                // Generate targets using the callback
                val targets = targetGenerator(projectConfig)
                projectConfig.targets = targets.toMutableMap()
                
                // Create the CreateNodesResult
                val createNodesResult = CreateNodesResult()
                createNodesResult.addProject(projectConfig.root ?: ".", projectConfig)
                
                // Create the entry
                val entry = CreateNodesV2Entry(pomPath, createNodesResult)
                results.add(entry)
                
                logInfo("Successfully processed project: ${project.artifactId}")
                
            } catch (e: Exception) {
                log.warn("Failed to process project ${project.artifactId}: ${e.message}")
                if (verbose) {
                    log.warn("Exception details:", e)
                }
            }
        }
        
        logInfo("Generated ${results.size} create nodes results")
        return results
    }
    
    /**
     * Generate a ProjectConfiguration for a Maven project
     */
    private fun generateProjectConfiguration(project: MavenProject, workspaceRoot: File): ProjectConfiguration {
        val projectRoot = getRelativePath(workspaceRoot, project.basedir).let {
            if (it.isEmpty()) "." else it
        }
        
        // Create ProjectConfiguration
        val projectConfig = ProjectConfiguration().apply {
            name = "${project.groupId}:${project.artifactId}"
            root = projectRoot
        }
        
        // Add metadata
        val metadata = ProjectMetadata(
            project.groupId,
            project.artifactId,
            project.version,
            project.packaging
        )
        
        projectConfig.metadata = metadata
        
        // Set project type
        projectConfig.projectType = determineProjectType(project)
        
        return projectConfig
    }
    
    /**
     * Determine project type based on packaging
     */
    private fun determineProjectType(project: MavenProject): String {
        return when (project.packaging) {
            "pom" -> "library"
            "jar" -> "application"
            "war" -> "application"
            "maven-plugin" -> "library"
            else -> "application"
        }
    }
    
    /**
     * Get relative path between two files
     */
    private fun getRelativePath(base: File, target: File): String {
        val basePath = base.toPath().normalize()
        val targetPath = target.toPath().normalize()
        
        return try {
            val relativePath = basePath.relativize(targetPath)
            relativePath.toString().replace("\\", "/")
        } catch (e: IllegalArgumentException) {
            // If paths are not related, return absolute path
            target.absolutePath
        }
    }
    
    /**
     * Log info message with generator prefix
     */
    private fun logInfo(message: String) {
        if (verbose) {
            log.info("[CreateNodesResultGenerator] $message")
        }
    }
}