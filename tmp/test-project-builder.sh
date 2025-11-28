#!/bin/bash

# Test script to verify ProjectBuilder component lookup fix
echo "Testing ProjectBuilder component lookup fix..."

# Build the project first
cd /home/jason/projects/triage/java/quarkus/maven-plugin
mvn clean install -DskipTests -q

# Create a test Maven project
mkdir -p /tmp/test-maven-project
cd /tmp/test-maven-project

cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.test</groupId>
    <artifactId>test-project</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
EOF

# Run the embedder executor with the fix
echo "Running embedder executor test..."
java -cp "/home/jason/projects/triage/java/quarkus/maven-plugin/embedder-executor/target/embedder-executor-999-SNAPSHOT.jar:/home/jason/.m2/repository/org/apache/maven/maven-core/3.9.10/maven-core-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-embedder/3.9.10/maven-embedder-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-compat/3.9.10/maven-compat-3.9.10.jar:/home/jason/.m2/repository/com/google/code/gson/gson/2.13.1/gson-2.13.1.jar:/home/jason/.m2/repository/org/jetbrains/kotlin/kotlin-stdlib/1.9.20/kotlin-stdlib-1.9.20.jar:/home/jason/.m2/repository/org/eclipse/aether/aether-impl/1.4.0/aether-impl-1.4.0.jar:/home/jason/.m2/repository/org/eclipse/aether/aether-api/1.4.0/aether-api-1.4.0.jar:/home/jason/.m2/repository/org/eclipse/aether/aether-util/1.4.0/aether-util-1.4.0.jar:/home/jason/.m2/repository/org/eclipse/aether/aether-connector-basic/1.4.0/aether-connector-basic-1.4.0.jar:/home/jason/.m2/repository/org/eclipse/aether/aether-transport-wagon/1.4.0/aether-transport-wagon-1.4.0.jar:/home/jason/.m2/repository/org/apache/maven/wagon/wagon-provider-api/3.5.3/wagon-provider-api-3.5.3.jar:/home/jason/.m2/repository/org/codehaus/plexus/plexus-utils/3.5.1/plexus-utils-3.5.1.jar:/home/jason/.m2/repository/org/codehaus/plexus/plexus-component-annotations/2.1.0/plexus-component-annotations-2.1.0.jar:/home/jason/.m2/repository/org/apache/maven/maven-model/3.9.10/maven-model-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-settings/3.9.10/maven-settings-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-settings-builder/3.9.10/maven-settings-builder-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-repository-metadata/3.9.10/maven-repository-metadata-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-artifact/3.9.10/maven-artifact-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-plugin-api/3.9.10/maven-plugin-api-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-model-builder/3.9.10/maven-model-builder-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-builder-support/3.9.10/maven-builder-support-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-resolver-provider/3.9.10/maven-resolver-provider-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/shared/maven-shared-utils/3.4.2/maven-shared-utils-3.4.2.jar:/home/jason/.m2/repository/commons-io/commons-io/2.16.1/commons-io-2.16.1.jar:/home/jason/.m2/repository/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar:/home/jason/.m2/repository/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar:/home/jason/.m2/repository/org/codehaus/plexus/plexus-interpolation/1.26/plexus-interpolation-1.26.jar:/home/jason/.m2/repository/org/codehaus/plexus/plexus-classworlds/2.8.0/plexus-classworlds-2.8.0.jar:/home/jason/.m2/repository/org/apache/maven/maven-cli/3.9.10/maven-cli-3.9.10.jar:/home/jason/.m2/repository/org/apache/maven/maven-slf4j-provider/3.9.10/maven-slf4j-provider-3.9.10.jar:/home/jason/.m2/repository/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar:/home/jason/.m2/repository/org/sonatype/plexus/plexus-sec-dispatcher/1.4/plexus-sec-dispatcher-1.4.jar:/home/jason/.m2/repository/org/sonatype/plexus/plexus-cipher/1.4/plexus-cipher-1.4.jar:/home/jason/.m2/repository/org/apache/commons/commons-cli/1.9.0/commons-cli-1.9.0.jar:/home/jason/.m2/repository/org/eclipse/sisu/org.eclipse.sisu.plexus/0.9.0.M3/org.eclipse.sisu.plexus-0.9.0.M3.jar:/home/jason/.m2/repository/org/eclipse/sisu/org.eclipse.sisu.inject/0.9.0.M3/org.eclipse.sisu.inject-0.9.0.M3.jar:/home/jason/.m2/repository/com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar:/home/jason/.m2/repository/aopalliance/aopalliance/1.0/aopalliance-1.0.jar:/home/jason/.m2/repository/com/google/inject/guice/5.1.0/guice-5.1.0.jar:/home/jason/.m2/repository/javax/inject/javax.inject/1/javax.inject-1.jar:/home/jason/.m2/repository/org/codehaus/plexus/plexus-container-default/2.1.1/plexus-container-default-2.1.1.jar:/home/jason/.m2/repository/org/apache/xbean/xbean-reflect/3.7/xbean-reflect-3.7.jar:/home/jason/.m2/repository/log4j/log4j/1.2.12/log4j-1.2.12.jar:/home/jason/.m2/repository/commons-logging/commons-logging-api/1.1/commons-logging-api-1.1.jar:/home/jason/.m2/repository/junit/junit/3.8.1/junit-3.8.1.jar:/home/jason/projects/triage/java/quarkus/maven-plugin/graph-analyzer/target/graph-analyzer-999-SNAPSHOT.jar" \
    NxMavenEmbedderBatchExecutor \
    "validate" \
    "/tmp/test-maven-project" \
    "." \
    "/tmp/test-results.json" \
    true 2>&1 | head -20

echo "Test completed. Check the output above for any ProjectBuilder component lookup errors."
echo "If you see successful initialization messages, the fix is working."