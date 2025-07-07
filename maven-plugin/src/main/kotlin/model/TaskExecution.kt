import org.apache.maven.project.MavenProject
import com.google.gson.annotations.SerializedName

/**
 * Represents a task to be executed in the Maven Embedder batch executor.
 * Each task corresponds to an Nx task with specific goals and configuration.
 */
data class TaskExecution(
    @SerializedName("taskId")
    val taskId: String,
    
    @SerializedName("goals")
    val goals: List<String>,
    
    @SerializedName("project")
    val project: MavenProject,
    
    @SerializedName("configuration")
    val configuration: Map<String, Any> = emptyMap(),
    
    @SerializedName("projectRoot")
    val projectRoot: String? = null,
    
    @SerializedName("properties")
    val properties: Map<String, String> = emptyMap()
) {
    /**
     * Get the effective project root, defaulting to project basedir if not specified
     */
    fun getEffectiveProjectRoot(): String {
        return projectRoot ?: project.basedir.absolutePath
    }
    
    /**
     * Check if this task has any goals to execute
     */
    fun hasGoals(): Boolean {
        return goals.isNotEmpty()
    }
    
    /**
     * Get a human-readable description of this task
     */
    fun getDescription(): String {
        return "Task '$taskId' executing goals [${goals.joinToString(", ")}] on project '${project.artifactId}'"
    }
}