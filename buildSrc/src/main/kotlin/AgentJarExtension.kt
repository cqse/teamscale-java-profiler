import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import java.io.File
import java.io.Serializable

/** Determines the path under which the com.teamscale.agent-jar plugin stored the agent jar. */
val Task.agentJar: File
	get() = this.temporaryDir.resolve("libs/agent.jar")

val Test.logFilePath
	get() = "logTest"

/** Adds a convenient way to attach the Teamscale JaCoCo agent to the JVM with the given options in a readable map format. */
fun JavaExec.teamscaleAgent(options: Map<String, String>) {
	addTeamscaleAgent(options)
}

/** Adds a convenient way to attach the Teamscale JaCoCo agent to the test JVM with the given options in a readable map format. */
fun Test.teamscaleAgent(options: Map<String, String>) {
	addTeamscaleAgent(options)
}

/**
 * Attaches the agent through a [CommandLineArgumentProvider], which puts its `-javaagent` option behind every
 * ordinary JVM argument. Do not turn this back into a [JavaForkOptions.jvmArgs] call.
 *
 * The JVM starts its JVMTI agents in the order in which they appear on the command line, and that is where both a
 * debugger and a Java agent's `premain` do their work. A debugger behind the profiler therefore only attaches once
 * `PreMain` has finished, and no breakpoint in the agent's startup code is ever hit. Keeping the profiler last is
 * what lets the debugger option the IDE appends to [JavaForkOptions.jvmArgs] come first;
 * [startDebuggerBeforeProfiler] does the same for `--debug-jvm`.
 */
private fun <T> T.addTeamscaleAgent(options: Map<String, String>) where T : Task, T : JavaForkOptions {
	jvmArgumentProviders.add(
		TeamscaleAgentArgumentProvider(
			"-javaagent:$agentJar=${options.entries.joinToString(separator = ",") { "${it.key}=${it.value}" }}"
		)
	)
}

/** Supplies the `-javaagent` option of the profiler, cf. [addTeamscaleAgent]. */
class TeamscaleAgentArgumentProvider(@get:Input val argument: String) : CommandLineArgumentProvider, Serializable {
	override fun asArguments() = listOf(argument)
}

/**
 * Makes `--debug-jvm` debug the profiler as well: Gradle appends the `-agentlib:jdwp` option it asks for behind
 * everything else, and thus behind the profiler, so we turn the request off and add an equivalent ordinary JVM
 * argument instead, which lands in front of it, cf. [addTeamscaleAgent].
 *
 * This has to happen after Gradle applied the command line option to the task, but before it finalizes the task's
 * properties — a `doFirst` is already too late — which leaves exactly the window between the task graph being ready
 * and the start of the execution phase.
 */
fun <T> T.startDebuggerBeforeProfiler() where T : Task, T : JavaForkOptions {
	project.gradle.taskGraph.whenReady {
		val options = debugOptions
		if (!options.enabled.get()) return@whenReady

		val server = if (options.server.get()) "y" else "n"
		val suspend = if (options.suspend.get()) "y" else "n"
		val address = options.host.map { "$it:" }.getOrElse("") + options.port.get()
		// Disabled so that Gradle does not append a second, conflicting option of its own.
		options.enabled.set(false)
		jvmArgs("-agentlib:jdwp=transport=dt_socket,server=$server,suspend=$suspend,address=$address")
	}
}
