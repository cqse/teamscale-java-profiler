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
 *
 * The agent jar is an input of the task, which also makes the task depend on building it. A dependency alone is not
 * enough: Gradle would consider the task up to date after a change to the agent, so it would not run against it.
 */
fun Task.createAgentCopy() {
	val agentJarFiles = agentJarSource.get()
	inputs.files(agentJarFiles)
		.withPropertyName("agentJar")
		.withPathSensitivity(PathSensitivity.NAME_ONLY)
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
