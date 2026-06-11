import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.the
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Synchronizes access to ports for the system tests so two system tests running in parallel don't
 * accidentally try to use the same port.
 */
abstract class SystemTestPorts : BuildService<BuildServiceParameters.None> {

	private val nextFreePort = AtomicInteger(6000)

	/**
	 * Used ports should be unique for each system test to avoid collisions during parallel execution.
	 * Thus, we assign a new port each time this is called.
	 * Since tests may run in different workers that don't share the #nextFreePort variable, we use the worker number
	 * to avoid collisions (given that we don't have more than 100 system tests per worker).
	 */
	fun pickFreePort(): Int = nextFreePort.getAndIncrement()

	companion object {

		/** Registers the SystemTestPorts with the given project. */
		fun registerWith(project: Project): Provider<SystemTestPorts> {
			return project.gradle.sharedServices.registerIfAbsent("system-test-ports", SystemTestPorts::class.java) {}
		}
	}
}


abstract class PortsExtension @Inject constructor(val portProvider: Provider<SystemTestPorts>) : Serializable {

	/** The port that the fake Teamscale should use during the system test. Guaranteed conflict-free. */
	abstract val teamscalePort: Property<Int>

	/** The port that the agent should use during the system test. Guaranteed conflict-free. */
	abstract val agentPort: Property<Int>

	fun pickFreePort(): Int = portProvider.get().pickFreePort()

}

val Test.ports: PortsExtension
	get() = the<PortsExtension>()

/** The port that the fake Teamscale should use during the system test. Guaranteed conflict-free. */
val Test.teamscalePort: Int
	get() = ports.teamscalePort.get()

/** The port that the agent should use during the system test. Guaranteed conflict-free. */
val Test.agentPort: Int
	get() = ports.agentPort.get()
