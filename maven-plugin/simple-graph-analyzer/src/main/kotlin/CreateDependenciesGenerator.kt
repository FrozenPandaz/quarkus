import org.apache.maven.execution.MavenSession
import org.apache.maven.project.MavenProject
import org.apache.maven.plugin.logging.Log
import model.*
import java.io.File

/**
 * Generates project dependencies for Nx integration
 * Uses the same logic as the complex analyzer to ensure consistent dependency detection
 */
class CreateDependenciesGenerator(
    private val session: MavenSession,
    private val reactorProjects: List<MavenProject>,
    private val verbose: Boolean,
    private val log: Log
) {
    
    /**
     * Generate project dependencies based on Maven dependencies
     * This uses the exact same logic as the complex analyzer
     */
    fun generateCreateDependencies(): List<RawProjectGraphDependency> {
        val dependencies = mutableListOf<RawProjectGraphDependency>()
        
        logInfo("Generating project dependencies for ${reactorProjects.size} projects")
        
        // Build artifact mapping using the same logic as complex analyzer
        val artifactToProject = buildArtifactMapping(reactorProjects)
        logInfo("Built artifact mapping for ${artifactToProject.size} workspace projects")
        
        var staticDeps = 0
        
        for (i in reactorProjects.indices) {
            val project = reactorProjects[i]
            val source = formatProjectKey(project)
            
            // Show progress every 100 projects
            if (i % 100 == 0 || i == reactorProjects.size - 1) {
                logInfo("Dependency analysis progress: ${i + 1}/${reactorProjects.size} projects")
            }
            
            try {
                // Add static dependencies using the same logic as complex analyzer
                val prevStatic = staticDeps
                staticDeps += addStaticDependencies(dependencies, project, artifactToProject)
                
                if (verbose && reactorProjects.size <= 20) {
                    val newStatic = staticDeps - prevStatic
                    if (newStatic > 0) {
                        logInfo("Project $source: $newStatic static dependencies")
                    }
                }
                
            } catch (e: Exception) {
                log.warn("Failed to process dependencies for project ${project.artifactId}: ${e.message}")
                if (verbose) {
                    log.warn("Exception details:", e)
                }
            }
        }
        
        logInfo("Generated ${dependencies.size} project dependencies ($staticDeps static dependencies)")
        return dependencies
    }
    
    /**
     * Build mapping from artifactId to project name for workspace projects
     * Same logic as complex analyzer
     */
    private fun buildArtifactMapping(projects: List<MavenProject>): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        for (project in projects) {
            if (project.groupId != null && project.artifactId != null) {
                val key = formatProjectKey(project)
                val projectName = formatProjectKey(project)
                mapping[key] = projectName
            } else {
                log.warn("Skipping project with null groupId or artifactId: $project")
            }
        }
        return mapping
    }
    
    /**
     * Add static dependencies (explicit Maven dependencies between workspace projects)
     * Same logic as complex analyzer
     */
    private fun addStaticDependencies(
        dependencies: MutableList<RawProjectGraphDependency>,
        project: MavenProject,
        artifactToProject: Map<String, String>
    ): Int {
        val source = formatProjectKey(project)
        var count = 0
        
        // Check declared dependencies - same as complex analyzer
        project.dependencies?.let { deps ->
            for (dep in deps) {
                if (dep.groupId != null && dep.artifactId != null) {
                    val depKey = "${dep.groupId}:${dep.artifactId}"
                    
                    // Check if this dependency refers to another project in workspace
                    val target = artifactToProject[depKey]
                    if (target != null && target != source) {
                        val dependency = RawProjectGraphDependency(
                            source, 
                            target, 
                            RawProjectGraphDependency.DependencyType.STATIC
                        )
                        dependencies.add(dependency)
                        count++
                        
                        if (verbose) {
                            logInfo("Found dependency: $source -> $target (scope: ${dep.scope ?: "compile"})")
                        }
                    }
                }
            }
        }
        
        return count
    }
    
    /**
     * Format Maven project as "groupId:artifactId" key
     * Same as complex analyzer
     */
    private fun formatProjectKey(project: MavenProject): String {
        return "${project.groupId}:${project.artifactId}"
    }
    
    /**
     * Log info message with generator prefix
     */
    private fun logInfo(message: String) {
        if (verbose) {
            log.info("[CreateDependenciesGenerator] $message")
        }
    }
}