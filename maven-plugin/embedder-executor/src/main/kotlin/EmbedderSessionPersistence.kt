
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Session persistence for Maven embedder execution
 */
class EmbedderSessionPersistence(
    private val workspaceRoot: String,
    private val verbose: Boolean = false
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val sessionDir = Paths.get(workspaceRoot, ".nx", "maven-sessions")
    
    init {
        // Create session directory if it doesn't exist
        try {
            Files.createDirectories(sessionDir)
        } catch (e: IOException) {
            if (verbose) {
                println("Warning: Could not create session directory: ${e.message}")
            }
        }
    }
    
    data class SessionData(
        val sessionId: String,
        val timestamp: Long,
        val projects: List<String> = emptyList(),
        val goals: List<String> = emptyList(),
        val artifacts: List<Map<String, String>> = emptyList(),
        val dependencies: List<String> = emptyList(),
        val properties: Map<String, String> = emptyMap(),
        val buildDirectory: String? = null,
        val outputDirectory: String? = null,
        val sessionProperties: Map<String, Any> = emptyMap()
    )
    
    fun saveSession(sessionData: SessionData) {
        try {
            val sessionFile = sessionDir.resolve("${sessionData.sessionId}.json")
            Files.write(sessionFile, gson.toJson(sessionData).toByteArray())
            if (verbose) {
                println("Session saved: ${sessionFile}")
            }
        } catch (e: IOException) {
            if (verbose) {
                println("Warning: Could not save session: ${e.message}")
            }
        }
    }
    
    fun loadSession(sessionId: String): SessionData? {
        return try {
            val sessionFile = sessionDir.resolve("${sessionId}.json")
            if (Files.exists(sessionFile)) {
                val content = Files.readString(sessionFile)
                gson.fromJson(content, SessionData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Could not load session: ${e.message}")
            }
            null
        }
    }
    
    fun getSessionId(goals: List<String>, projects: List<String>): String {
        val content = "${goals.sorted().joinToString(",")}-${projects.sorted().joinToString(",")}"
        return content.hashCode().toString()
    }
    
    fun cleanupOldSessions(maxAge: Long = 24 * 60 * 60 * 1000) { // 24 hours
        try {
            val cutoff = System.currentTimeMillis() - maxAge
            Files.walk(sessionDir)
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }
                .forEach { sessionFile ->
                    try {
                        val sessionData = gson.fromJson(Files.readString(sessionFile), SessionData::class.java)
                        if (sessionData.timestamp < cutoff) {
                            Files.delete(sessionFile)
                            if (verbose) {
                                println("Cleaned up old session: ${sessionFile}")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore errors with individual session files
                    }
                }
        } catch (e: Exception) {
            if (verbose) {
                println("Warning: Could not cleanup old sessions: ${e.message}")
            }
        }
    }
    
    fun getSessionDirectory(): File {
        return sessionDir.toFile()
    }
    
    fun loadBatchSession(projects: List<String>): Map<String, SessionData> {
        val sessionMap = mutableMapOf<String, SessionData>()
        
        projects.forEach { project ->
            try {
                val sessionFile = sessionDir.resolve("${project}.json")
                if (Files.exists(sessionFile)) {
                    val content = Files.readString(sessionFile)
                    val sessionData = gson.fromJson(content, SessionData::class.java)
                    sessionMap[project] = sessionData
                }
            } catch (e: Exception) {
                if (verbose) {
                    println("Warning: Could not load session for project $project: ${e.message}")
                }
            }
        }
        
        return sessionMap
    }
    
    fun saveBatchSession(projects: List<String>, results: List<Any>, sessionProperties: Map<String, Any>, buildInfo: Any?) {
        try {
            projects.forEach { project ->
                val sessionData = SessionData(
                    sessionId = project,
                    timestamp = System.currentTimeMillis(),
                    projects = listOf(project),
                    sessionProperties = sessionProperties
                )
                
                val sessionFile = sessionDir.resolve("${project}.json")
                Files.write(sessionFile, gson.toJson(sessionData).toByteArray())
                
                if (verbose) {
                    println("Session saved for project $project: ${sessionFile}")
                }
            }
        } catch (e: IOException) {
            if (verbose) {
                println("Warning: Could not save batch session: ${e.message}")
            }
        }
    }
}