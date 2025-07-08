import org.junit.Test
import org.junit.Assert.*
import org.apache.maven.project.ProjectBuilder
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.classworlds.ClassWorld

/**
 * Simple test to verify ProjectBuilder component lookup works with the fix
 */
class ProjectBuilderComponentTest {

    @Test
    fun testProjectBuilderComponentLookup() {
        try {
            // Create ClassWorld with proper realm setup (same as our fix)
            val classWorld = ClassWorld("plexus.core", Thread.currentThread().contextClassLoader)
            
            // Create PlexusContainer with Maven component scanning enabled (same as our fix)
            val configuration = DefaultContainerConfiguration()
                .setAutoWiring(true)
                .setComponentVisibility(PlexusConstants.REALM_VISIBILITY)
                .setClassPathScanning(PlexusConstants.SCANNING_ON)
                .setClassWorld(classWorld)
            
            val plexusContainer = DefaultPlexusContainer(configuration)
            
            // Additional component discovery for Maven's built-in components
            plexusContainer.discoverComponents(plexusContainer.containerRealm)
            
            // Repository system configuration is handled by Maven's autodiscovery
            // with the maven-resolver-provider dependency
            
            // Test the ProjectBuilder component lookup that was failing
            val projectBuilder = plexusContainer.lookup("org.apache.maven.project.ProjectBuilder") as ProjectBuilder
            
            assertNotNull("ProjectBuilder should not be null", projectBuilder)
            assertEquals("Should be the default implementation", 
                "org.apache.maven.project.DefaultProjectBuilder", 
                projectBuilder.javaClass.name)
            
            // Cleanup
            plexusContainer.dispose()
            
        } catch (e: Exception) {
            fail("ProjectBuilder component lookup should not fail with our fix: ${e.message}")
        }
    }
    
    @Test
    fun testProjectBuilderAvailableInContainer() {
        try {
            // Create ClassWorld with proper realm setup (same as our fix)
            val classWorld = ClassWorld("plexus.core", Thread.currentThread().contextClassLoader)
            
            // Create container with our configuration
            val configuration = DefaultContainerConfiguration()
                .setAutoWiring(true)
                .setComponentVisibility(PlexusConstants.REALM_VISIBILITY)
                .setClassPathScanning(PlexusConstants.SCANNING_ON)
                .setClassWorld(classWorld)
            
            val plexusContainer = DefaultPlexusContainer(configuration)
            plexusContainer.discoverComponents(plexusContainer.containerRealm)
            
            // Repository system configuration is handled by Maven's autodiscovery
            // with the maven-resolver-provider dependency
            
            // Check if the component is actually available
            val hasComponent = plexusContainer.hasComponent("org.apache.maven.project.ProjectBuilder")
            
            assertTrue("ProjectBuilder component should be available in container", hasComponent)
            
            // Cleanup
            plexusContainer.dispose()
            
        } catch (e: Exception) {
            fail("Container setup should not fail: ${e.message}")
        }
    }
}