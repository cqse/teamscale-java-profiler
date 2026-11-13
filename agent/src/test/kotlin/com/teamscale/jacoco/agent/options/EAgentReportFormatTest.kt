package com.teamscale.jacoco.agent.options

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Tests the mapping from the file naming convention of the output directory back to the report format. */
class EAgentReportFormatTest {

	@Test
	fun `derives the format from the file name prefix`() {
		assertThat(EAgentReportFormat.fromFileName("cobertura-2024-01-01-12-00-00.000.xml"))
			.isEqualTo(EAgentReportFormat.COBERTURA)
		assertThat(EAgentReportFormat.fromFileName("jacoco-2024-01-01-12-00-00.000.xml"))
			.isEqualTo(EAgentReportFormat.JACOCO)
		assertThat(EAgentReportFormat.fromFileName("compact-coverage-2024-01-01-12-00-00.000.json"))
			.isEqualTo(EAgentReportFormat.TEAMSCALE_COMPACT_COVERAGE)
	}

	@Test
	fun `returns null for file names that follow no known convention`() {
		assertThat(EAgentReportFormat.fromFileName("coverage.xml")).isNull()
		assertThat(EAgentReportFormat.fromFileName("jacoco-2024-01-01.exec")).isNull()
	}

	/** JaCoCo XML and Cobertura share the `.xml` extension, so the extension alone cannot tell them apart. */
	@Test
	fun `resolves the ambiguous xml extension to JaCoCo`() {
		assertThat(EAgentReportFormat.fromFileExtension("xml")).isEqualTo(EAgentReportFormat.JACOCO)
		assertThat(EAgentReportFormat.fromFileExtension("json"))
			.isEqualTo(EAgentReportFormat.TEAMSCALE_COMPACT_COVERAGE)
		assertThat(EAgentReportFormat.fromFileExtension("exec")).isNull()
	}
}
