import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.io.File

/** Determines the path under which the com.teamscale.agent-jar plugin stored the agent jar. */
val Task.agentJar: File
	get() = this.temporaryDir.resolve("libs/agent.jar")

val Test.logFilePath
	get() = "logTest"

/** Adds a convenient way to attach the Teamscale JaCoCo agent to the JVM with the given options in a readable map format. */
fun JavaExec.teamscaleAgent(options: Map<String, String>) {
	jvmArgs(
		"-javaagent:$agentJar=${options.entries.joinToString(separator = ",") { "${it.key}=${it.value}" }}"
	)
}

/** Adds a convenient way to attach the Teamscale JaCoCo agent to the test JVM with the given options in a readable map format. */
fun Test.teamscaleAgent(options: Map<String, String>) {
	jvmArgs(
		"-javaagent:$agentJar=${options.entries.joinToString(separator = ",") { "${it.key}=${it.value}" }}"
	)
}
