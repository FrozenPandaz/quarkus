# Eclipse Sisu Minimal Working Configuration

## The Challenge

The Eclipse Sisu configuration is modern and correct, but the API complexity makes it difficult to get a working setup. We need:

1. **SpaceModule**: Scans classpath for components
2. **WireModule**: Provides automatic dependency wiring  
3. **PlexusBindingModule**: Bridges Plexus components to Guice (ESSENTIAL for Maven components)

## The Problem

Without PlexusBindingModule, Maven's Plexus components (like ProjectBuilder) are not bound into the Guice injector, causing the "No implementation for ProjectBuilder was bound" error.

## Next Steps

1. **Research working PlexusBindingModule pattern**: Find a simple, working example
2. **Use Maven's own setup**: Look at how Maven CLI actually configures Eclipse Sisu
3. **Consider hybrid approach**: Eclipse Sisu for core DI + fallback to Plexus for component lookup

The technology choice (Eclipse Sisu) is correct - it's the implementation that needs refinement.