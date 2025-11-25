package com.teamscale.tia.runlistener

import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.tia.client.TiaAgent
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import java.util.*

/**
 * [TestExecutionListener] that uses the [TiaAgent] to record test-wise coverage.
 */
class JUnit5TestwiseCoverageExecutionListener : TestExecutionListener {
	private val bridge = RunListenerAgentBridge.create<JUnit5TestwiseCoverageExecutionListener>()

	override fun executionStarted(testIdentifier: TestIdentifier) {
		if (!testIdentifier.isTest) return
		val uniformPath = testIdentifier.getUniformPath()
		bridge.testStarted(uniformPath)
	}

	private fun TestIdentifier.getUniformPath() =
		source.flatMap { source ->
			source.parse()
		}.orElse(displayName)

	private fun TestSource.parse() =
		when (this) {
			is ClassSource -> Optional.of(className.replace('.', '/'))
			is MethodSource -> Optional.of(
				"${className.replace('.', '/')}/$methodName($methodParameterTypes)"
			)

			else -> Optional.empty()
		}

	override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
		if (!testIdentifier.isTest) {
			return
		}
		val uniformPath = testIdentifier.getUniformPath()
		val result = when (testExecutionResult.status) {
			TestExecutionResult.Status.SUCCESSFUL -> ETestExecutionResult.PASSED
			TestExecutionResult.Status.ABORTED -> ETestExecutionResult.ERROR
			TestExecutionResult.Status.FAILED -> ETestExecutionResult.FAILURE
		}

		bridge.testFinished(uniformPath, result)
	}

	override fun executionSkipped(testIdentifier: TestIdentifier, reason: String) {
		if (testIdentifier.isContainer) return
		val uniformPath = testIdentifier.getUniformPath()
		bridge.testSkipped(uniformPath, reason)
	}

	override fun testPlanExecutionFinished(testPlan: TestPlan) {
		bridge.testRunFinished()
	}
}
