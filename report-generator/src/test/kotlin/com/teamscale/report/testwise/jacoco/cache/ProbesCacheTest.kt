package com.teamscale.report.testwise.jacoco.cache

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.assertj.core.api.Assertions.assertThat
import org.jacoco.core.data.ExecutionData
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/** Tests for the [ProbesCache] class. */
class ProbesCacheTest {

	private val logger = Mockito.mock<ILogger>()
	private val probesCache = ProbesCache(logger, EDuplicateClassFileBehavior.IGNORE)

	/** Coverage of classes that were not analyzed must only be reported as missing if the classes are included. */
	@Test
	fun reportsMissingClassesOnlyIfIncluded() {
		val filter = ClasspathWildcardIncludeFilter("com.company.*", null)

		assertThat(probesCache.getCoverage(ExecutionData(1, "com/company/Included", 1), filter)).isNull()
		assertThat(probesCache.getCoverage(ExecutionData(2, "org/other/Excluded", 1), filter)).isNull()
		probesCache.flushLogger()

		Mockito.verify(logger).warn(" - com.company.Included")
		Mockito.verify(logger, Mockito.never()).warn(Mockito.contains("org.other.Excluded"))
	}
}
