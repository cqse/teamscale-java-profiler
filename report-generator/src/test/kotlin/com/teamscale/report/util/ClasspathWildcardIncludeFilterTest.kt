package com.teamscale.report.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ClasspathWildcardIncludeFilterTest {

	/** Tests the include and exclude patterns against JVM internal class names. */
	@ParameterizedTest
	@CsvSource(
		// Patterns must match the class name with and without leading `*`
		"com.company.*, , com/company/Foo, true",
		"com.company.*, , com/company/sub/Foo, true",
		"com.company.*, , org/company/Foo, false",
		"com.company.*, , org/com/company/Foo, false",
		"*com.company.*, , com/company/Foo, true",
		"*com.company.*, , org/com/company/Foo, true",
		// Excludes overrule includes
		", org.junit.*, org/junit/jupiter/engine/Constants, false",
		", org.junit.*, com/company/Foo, true",
		"com.company.*, com.company.generated.*, com/company/Foo, true",
		"com.company.*, com.company.generated.*, com/company/generated/Foo, false",
		// Like JaCoCo, nested classes keep their `$`
		"com.company.Outer$*, , com/company/Outer\$Inner, true",
		"com.company.Outer.Inner, , com/company/Outer\$Inner, false",
		// Multiple patterns are separated by colons
		"com.*:de.*, , com/foo/Bar, true",
		"com.*:de.*, , de/foo/Bar\$Goo, true",
		"com.*:de.*, , org/foo/Bar, false",
	)
	fun testIsIncluded(includes: String?, excludes: String?, internalClassName: String, expectedIncluded: Boolean) {
		assertThat(ClasspathWildcardIncludeFilter(includes, excludes).isIncluded(internalClassName))
			.isEqualTo(expectedIncluded)
	}
}
