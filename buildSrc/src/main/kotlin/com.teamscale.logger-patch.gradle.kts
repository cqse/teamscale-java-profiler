import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE

plugins {
	java
}

// Configures the compile and runtime classpath to transform all jar files using the JulManagerFreeTransform
// See https://www.youtube.com/watch?v=T9U0BOlVc-c for an explanation of how this works
val julManagerFreeAttribute = Attribute.of("julManagerFree", Boolean::class.javaObjectType)

dependencies.artifactTypes.maybeCreate("jar").attributes.attribute(julManagerFreeAttribute, false)

dependencies.registerTransform(JulManagerFreeTransform::class) {
	from.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar").attribute(julManagerFreeAttribute, false)
	to.attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar").attribute(julManagerFreeAttribute, true)
}

configurations.compileClasspath {
	attributes.attribute(julManagerFreeAttribute, true)
}
configurations.runtimeClasspath {
	attributes.attribute(julManagerFreeAttribute, true)
}
