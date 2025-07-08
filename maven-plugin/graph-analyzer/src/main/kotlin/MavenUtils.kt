import org.apache.maven.project.MavenProject
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Dependency
import org.apache.maven.execution.MavenSession
import java.io.File

/**
 * Enhanced utility methods for Maven project operations, including embedder-specific functionality.
 */
object MavenUtils {
    
    /**
     * Format Maven project as "groupId:artifactId" key
     */
    fun formatProjectKey(project: MavenProject): String {
        return "${project.groupId}:${project.artifactId}"
    }
    
    /**
     * Format Maven project with version as "groupId:artifactId:version" key
     */
    fun formatProjectKeyWithVersion(project: MavenProject): String {
        return "${project.groupId}:${project.artifactId}:${project.version}"
    }
    
    /**
     * Format Maven artifact as "groupId:artifactId:version:type:classifier" key
     */
    fun formatArtifactKey(artifact: Artifact): String {
        return buildString {
            append("${artifact.groupId}:${artifact.artifactId}:${artifact.version}:${artifact.type}")
            if (artifact.classifier != null) {
                append(":${artifact.classifier}")
            }
        }
    }
    
    /**
     * Format Maven dependency as "groupId:artifactId:version:scope" key
     */
    fun formatDependencyKey(dependency: Dependency): String {
        return "${dependency.groupId}:${dependency.artifactId}:${dependency.version}:${dependency.scope ?: "compile"}"
    }
    
    /**
     * Parse goal string into plugin and goal components
     */
    fun parseGoal(goal: String): Pair<String?, String> {
        return when {
            goal.contains(":") -> {
                val lastColonIndex = goal.lastIndexOf(":")
                val plugin = goal.substring(0, lastColonIndex)
                val goalName = goal.substring(lastColonIndex + 1)
                Pair(plugin, goalName)
            }
            else -> Pair(null, goal) // Lifecycle phase
        }
    }
    
    /**
     * Check if a goal is a lifecycle phase
     */
    fun isLifecyclePhase(goal: String): Boolean {
        val lifecyclePhases = setOf(
            "validate", "initialize", "generate-sources", "process-sources", "generate-resources",
            "process-resources", "compile", "process-classes", "generate-test-sources",
            "process-test-sources", "generate-test-resources", "process-test-resources",
            "test-compile", "process-test-classes", "test", "prepare-package", "package",
            "pre-integration-test", "integration-test", "post-integration-test", "verify",
            "install", "deploy"
        )
        return goal in lifecyclePhases
    }
    
    /**
     * Get the effective project directory
     */
    fun getEffectiveProjectDirectory(project: MavenProject, projectRoot: String?): File {
        return when {
            projectRoot != null && projectRoot != "." -> File(projectRoot)
            else -> project.basedir
        }
    }
    
    /**
     * Check if project has a specific packaging type
     */
    fun hasPackaging(project: MavenProject, packaging: String): Boolean {
        return project.packaging?.equals(packaging, ignoreCase = true) == true
    }
    
    /**
     * Check if project is a parent/aggregator project
     */
    fun isAggregatorProject(project: MavenProject): Boolean {
        return hasPackaging(project, "pom") && project.modules.isNotEmpty()
    }
    
    /**
     * Check if project is a leaf project (no modules)
     */
    fun isLeafProject(project: MavenProject): Boolean {
        return project.modules.isEmpty()
    }
    
    /**
     * Get project's build output directory
     */
    fun getBuildOutputDirectory(project: MavenProject): File {
        return File(project.build.outputDirectory)
    }
    
    /**
     * Get project's test output directory
     */
    fun getTestOutputDirectory(project: MavenProject): File {
        return File(project.build.testOutputDirectory)
    }
    
    /**
     * Get project's source directories
     */
    fun getSourceDirectories(project: MavenProject): List<File> {
        val dirs = mutableListOf<File>()
        
        // Main source directory
        if (project.build.sourceDirectory != null) {
            dirs.add(File(project.build.sourceDirectory))
        }
        
        // Additional source directories from build helper plugin or other sources
        project.compileSourceRoots?.forEach { sourceRoot ->
            dirs.add(File(sourceRoot))
        }
        
        return dirs.filter { it.exists() }
    }
    
    /**
     * Get project's test source directories
     */
    fun getTestSourceDirectories(project: MavenProject): List<File> {
        val dirs = mutableListOf<File>()
        
        // Test source directory
        if (project.build.testSourceDirectory != null) {
            dirs.add(File(project.build.testSourceDirectory))
        }
        
        // Additional test source directories
        project.testCompileSourceRoots?.forEach { sourceRoot ->
            dirs.add(File(sourceRoot))
        }
        
        return dirs.filter { it.exists() }
    }
    
    /**
     * Get project's resource directories
     */
    fun getResourceDirectories(project: MavenProject): List<File> {
        return project.resources?.mapNotNull { resource ->
            resource.directory?.let { File(it) }?.takeIf { it.exists() }
        } ?: emptyList()
    }
    
    /**
     * Get project's test resource directories
     */
    fun getTestResourceDirectories(project: MavenProject): List<File> {
        return project.testResources?.mapNotNull { resource ->
            resource.directory?.let { File(it) }?.takeIf { it.exists() }
        } ?: emptyList()
    }
    
    /**
     * Check if Maven session has exceptions
     */
    fun hasSessionExceptions(session: MavenSession): Boolean {
        return session.result?.hasExceptions() == true
    }
    
    /**
     * Get session exception messages
     */
    fun getSessionExceptionMessages(session: MavenSession): List<String> {
        return session.result?.exceptions?.mapNotNull { it.message } ?: emptyList()
    }
    
    /**
     * Find workspace root by looking for common markers
     */
    fun findWorkspaceRoot(startDir: File): File {
        var current: File? = startDir
        
        while (current != null && current.parentFile != null) {
            // Look for common workspace indicators
            if (File(current, "nx.json").exists() ||
                File(current, "workspace.json").exists() ||
                File(current, ".git").exists() ||
                File(current, "pom.xml").exists()) {
                return current
            }
            current = current.parentFile
        }
        
        // Fallback to start directory
        return startDir
    }
    
    /**
     * Check if file is a Maven POM file
     */
    fun isPomFile(file: File): Boolean {
        return file.exists() && file.isFile && file.name.equals("pom.xml", ignoreCase = true)
    }
    
    /**
     * Validate project coordinates
     */
    fun validateProjectCoordinates(groupId: String?, artifactId: String?, version: String?): Boolean {
        return !groupId.isNullOrBlank() && 
               !artifactId.isNullOrBlank() && 
               !version.isNullOrBlank()
    }
    
    /**
     * Generate a safe filename from project coordinates
     */
    fun generateSafeFileName(project: MavenProject): String {
        return "${project.artifactId.replace("/", "_").replace("\\", "_")}-${project.version}"
    }
}