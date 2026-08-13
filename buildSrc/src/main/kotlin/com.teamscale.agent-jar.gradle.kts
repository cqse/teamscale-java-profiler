import java.io.Serializable

plugins {
	id("com.teamscale.java-convention")
}

/** The shaded agent jar, shared by the :agent project via [AGENT_JAR_CONFIGURATION]. */
val agentJarDependency = configurations.dependencyScope("teamscaleAgent")
val agentJarSource = configurations.resolvable("teamscaleAgentJar") {
	extendsFrom(agentJarDependency.get())
}

dependencies {
	agentJarDependency(project(":agent", AGENT_JAR_CONFIGURATION))
}

/**
 * Creates a copy of the agent jar file in the temporary directory of this task
 * to isolate it from other tasks running in parallel.
 */
fun Task.createAgentCopy() {
	val agentJarFiles = agentJarSource.get()
	dependsOn(agentJarFiles)
	doFirst("copyAgent", CopyAgent(agentJarFiles.elements.map { it.single().asFile }, agentJar))
}

class CopyAgent(
	val source: Provider<File>,
	val agentJar: File
) : Action<Task>, Serializable {
	override fun execute(t: Task) {
		agentJar.parentFile.mkdir()
		source.get().copyTo(agentJar, overwrite = true)
	}
}

tasks.withType<JavaExec> {
	createAgentCopy()
	startDebuggerBeforeProfiler()
}

tasks.test {
	createAgentCopy()
	startDebuggerBeforeProfiler()
}
