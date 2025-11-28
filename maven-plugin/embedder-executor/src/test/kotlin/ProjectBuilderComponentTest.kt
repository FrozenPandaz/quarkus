import org.junit.Test
import org.junit.Assert.*
import org.apache.maven.project.ProjectBuilder
import org.codehaus.plexus.classworlds.ClassWorld
import com.google.inject.Guice
import com.google.inject.Injector
import org.eclipse.sisu.plexus.PlexusBindingModule
import org.eclipse.sisu.space.SpaceModule
import org.eclipse.sisu.space.URLClassSpace

/**
 * Simple test to verify ProjectBuilder component lookup works with the fix
 */
class ProjectBuilderComponentTest {

    @Test
    fun testProjectBuilderComponentLookup() {
        try {
            // Create ClassWorld with proper realm setup (same as our fix)
            val classWorld = ClassWorld("plexus.core", Thread.currentThread().contextClassLoader)
            
            // Create Eclipse Sisu injector using basic configuration that works
            val classSpace = URLClassSpace(Thread.currentThread().contextClassLoader)
            
            // Create basic SpaceModule and WireModule
            val spaceModule = SpaceModule(classSpace)
            val wireModule = org.eclipse.sisu.wire.WireModule(spaceModule)
            
            // Create injector with just the core modules
            val injector = Guice.createInjector(wireModule)
            
            // Test the ProjectBuilder component lookup using Eclipse Sisu
            val projectBuilder = injector.getInstance(ProjectBuilder::class.java)
            
            assertNotNull("ProjectBuilder should not be null", projectBuilder)
            assertEquals("Should be the default implementation", 
                "org.apache.maven.project.DefaultProjectBuilder", 
                projectBuilder.javaClass.name)
            
            // No explicit cleanup needed for Sisu injector
            
        } catch (e: Exception) {
            fail("ProjectBuilder component lookup should not fail with Eclipse Sisu: ${e.message}")
        }
    }
    
    @Test
    fun testProjectBuilderAvailableInInjector() {
        try {
            // Create ClassWorld with proper realm setup (same as our fix)
            val classWorld = ClassWorld("plexus.core", Thread.currentThread().contextClassLoader)
            
            // Create Eclipse Sisu injector using basic configuration that works
            val classSpace = URLClassSpace(Thread.currentThread().contextClassLoader)
            
            // Create basic SpaceModule and WireModule
            val spaceModule = SpaceModule(classSpace)
            val wireModule = org.eclipse.sisu.wire.WireModule(spaceModule)
            
            // Create injector with just the core modules
            val injector = Guice.createInjector(wireModule)
            
            // Test that we can get an instance without errors
            val projectBuilder = injector.getInstance(ProjectBuilder::class.java)
            assertNotNull("ProjectBuilder should be available in injector", projectBuilder)
            
            // Also test RepositorySystem which was the original issue
            val repositorySystem = injector.getInstance(org.eclipse.aether.RepositorySystem::class.java)
            assertNotNull("RepositorySystem should be available in injector", repositorySystem)
            
            // No explicit cleanup needed for Sisu injector
            
        } catch (e: Exception) {
            fail("Eclipse Sisu injector setup should not fail: ${e.message}")
        }
    }
}