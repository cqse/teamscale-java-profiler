package dump.test

/**
 * A minimal system under test. The agent is attached with a too-small dump `interval`, and we only need the JVM to
 * start so the agent logs its startup warning.
 */
object SystemUnderTest {
	@JvmStatic
	fun main(args: Array<String>) {
		// doesn't need to do anything for this test
	}
}
