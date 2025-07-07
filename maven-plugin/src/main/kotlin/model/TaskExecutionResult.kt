import com.google.gson.annotations.SerializedName
import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Dependency

/**
 * Result of executing a single task in the Maven Embedder batch executor.
 * Contains detailed information about goal execution, artifacts, and dependencies.
 */
data class TaskExecutionResult(
    @SerializedName("taskId")
    val taskId: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("duration")
    val duration: Long,
    
    @SerializedName("goalResults")
    val goalResults: List<GoalExecutionResult>,
    
    @SerializedName("artifacts")
    val artifacts: List<ArtifactResult> = emptyList(),
    
    @SerializedName("dependencies")
    val dependencies: List<DependencyResult> = emptyList(),
    
    @SerializedName("errorMessage")
    val errorMessage: String? = null,
    
    @SerializedName("executionContext")
    val executionContext: Map<String, Any> = emptyMap()
) {
    /**
     * Check if all goals in this task were successful
     */
    fun allGoalsSuccessful(): Boolean {
        return goalResults.all { it.success }
    }
    
    /**
     * Get the first failed goal, if any
     */
    fun getFirstFailedGoal(): GoalExecutionResult? {
        return goalResults.firstOrNull { !it.success }
    }
    
    /**
     * Get total output lines from all goals
     */
    fun getAllOutput(): List<String> {
        return goalResults.flatMap { it.output }
    }
    
    /**
     * Get total error lines from all goals
     */
    fun getAllErrors(): List<String> {
        return goalResults.flatMap { it.errors }
    }
    
    /**
     * Get a summary of this task execution
     */
    fun getSummary(): String {
        val status = if (success) "SUCCESS" else "FAILURE"
        val goalCount = goalResults.size
        val successfulGoals = goalResults.count { it.success }
        return "Task '$taskId' $status: $successfulGoals/$goalCount goals completed (${duration}ms)"
    }
}

/**
 * Result of executing a specific Maven goal within a task.
 */
data class GoalExecutionResult(
    @SerializedName("goal")
    val goal: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("duration")
    val duration: Long,
    
    @SerializedName("output")
    val output: List<String> = emptyList(),
    
    @SerializedName("errors")
    val errors: List<String> = emptyList(),
    
    @SerializedName("exitCode")
    val exitCode: Int = 0,
    
    @SerializedName("pluginInfo")
    val pluginInfo: PluginInfo? = null,
    
    @SerializedName("executionId")
    val executionId: String? = null
)

/**
 * Information about a Maven plugin that was executed.
 */
data class PluginInfo(
    @SerializedName("groupId")
    val groupId: String,
    
    @SerializedName("artifactId")
    val artifactId: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("goalName")
    val goalName: String,
    
    @SerializedName("executionId")
    val executionId: String? = null
)

/**
 * Result of artifact resolution/creation during task execution.
 */
data class ArtifactResult(
    @SerializedName("groupId")
    val groupId: String,
    
    @SerializedName("artifactId")
    val artifactId: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("classifier")
    val classifier: String? = null,
    
    @SerializedName("scope")
    val scope: String? = null,
    
    @SerializedName("file")
    val file: String? = null,
    
    @SerializedName("resolved")
    val resolved: Boolean = false
) {
    companion object {
        /**
         * Create ArtifactResult from Maven Artifact
         */
        fun fromMavenArtifact(artifact: Artifact): ArtifactResult {
            return ArtifactResult(
                groupId = artifact.groupId,
                artifactId = artifact.artifactId,
                version = artifact.version,
                type = artifact.type,
                classifier = artifact.classifier,
                scope = artifact.scope,
                file = artifact.file?.absolutePath,
                resolved = artifact.isResolved
            )
        }
    }
}

/**
 * Result of dependency resolution during task execution.
 */
data class DependencyResult(
    @SerializedName("groupId")
    val groupId: String,
    
    @SerializedName("artifactId")
    val artifactId: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("classifier")
    val classifier: String? = null,
    
    @SerializedName("scope")
    val scope: String,
    
    @SerializedName("optional")
    val optional: Boolean = false,
    
    @SerializedName("file")
    val file: String? = null
) {
    companion object {
        /**
         * Create DependencyResult from Maven Dependency
         */
        fun fromMavenDependency(dependency: Dependency): DependencyResult {
            return DependencyResult(
                groupId = dependency.groupId,
                artifactId = dependency.artifactId,
                version = dependency.version,
                type = dependency.type ?: "jar",
                classifier = dependency.classifier,
                scope = dependency.scope ?: "compile",
                optional = dependency.isOptional
            )
        }
    }
}