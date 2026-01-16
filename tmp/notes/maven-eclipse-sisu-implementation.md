# Maven's Eclipse Sisu Implementation - The Complete Picture

## What We Discovered from Maven Source Code

By examining Maven's actual source code in `MavenCli.java`, we found exactly how Maven implements Eclipse Sisu dependency injection:

### Maven's Container Setup Pattern

```java
// From Maven's MavenCli.container() method
container = new DefaultPlexusContainer( cc, new AbstractModule()
{
    protected void configure()
    {
        bind( ILoggerFactory.class ).toInstance( slf4jLoggerFactory );
    }
} );
```

### Key Insights

1. **Maven uses DefaultPlexusContainer with Guice AbstractModule**
   - Not pure Eclipse Sisu modules (WireModule, PlexusBindingModule, etc.)
   - DefaultPlexusContainer automatically integrates with Eclipse Sisu under the hood

2. **The Magic is in DefaultPlexusContainer**
   - When you pass an AbstractModule to DefaultPlexusContainer, it automatically:
   - Creates a Guice injector internally 
   - Uses Eclipse Sisu for component scanning and binding
   - Provides Plexus compatibility layer

3. **Configuration Pattern**
   - ContainerConfiguration with ClassWorld, auto-wiring, scanning
   - Name set to "maven"
   - Custom Guice bindings through AbstractModule

## Our Implementation

We now use **exactly the same pattern as Maven**:

```kotlin
// Create Plexus container with Eclipse Sisu (exactly like Maven does)
val configuration = DefaultContainerConfiguration()
    .setAutoWiring(true)
    .setComponentVisibility(PlexusConstants.REALM_VISIBILITY)
    .setClassPathScanning(PlexusConstants.SCANNING_ON)
    .setClassWorld(classWorld)
    .setName("maven")

// Create container with Guice module (same pattern as Maven's MavenCli.container())
plexusContainer = DefaultPlexusContainer(
    configuration,
    object : com.google.inject.AbstractModule() {
        override fun configure() {
            // Add any custom bindings here if needed
            // Maven binds ILoggerFactory here, we can add our own bindings
        }
    }
)
```

## Why This Works

1. **DefaultPlexusContainer IS Eclipse Sisu**
   - Modern versions of DefaultPlexusContainer use Eclipse Sisu internally
   - When you pass AbstractModule, it creates Guice injector with Eclipse Sisu
   - Provides automatic component scanning and JSR-330 support

2. **No Manual Module Configuration Needed**
   - We don't need to manually create WireModule, SpaceModule, PlexusBindingModule
   - DefaultPlexusContainer handles all of that internally
   - Just like Maven does

3. **Backward Compatibility**
   - Works with both Plexus @Component and JSR-330 @Named annotations
   - Maintains all Maven component discovery
   - Exactly the same behavior as Maven CLI

## Benefits

- ✅ **Uses Maven's exact approach** - no custom configuration
- ✅ **Eclipse Sisu under the hood** - modern DI framework
- ✅ **Simple and reliable** - lets Maven handle the complexity  
- ✅ **Component discovery works** - finds ProjectBuilder and all Maven components
- ✅ **Future-proof** - evolves with Maven's Eclipse Sisu implementation

## The Answer to "How Should We Use Eclipse Sisu?"

**Answer**: Use `DefaultPlexusContainer` with `AbstractModule` - exactly like Maven does. This gives you Eclipse Sisu dependency injection without the complexity of manual configuration.

Maven solved this problem for us by making DefaultPlexusContainer a facade over Eclipse Sisu!