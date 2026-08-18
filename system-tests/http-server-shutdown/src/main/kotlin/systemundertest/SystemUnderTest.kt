package systemundertest

/**
 * Fake system under test to generate some coverage. Exits on its own so the test can verify that the agent's HTTP
 * server does not keep the JVM alive with non-daemon threads.
 */
object SystemUnderTest {
	@JvmStatic
	fun main(args: Array<String>) {
		println("Production code: ${foo()}")
	}

	fun foo() = 2
}
