package com.teamscale.report.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClasspathWildcardIncludeFilterTest {

	/** Patterns must match the class name with and without leading `*`. */
	@Test
	fun testIncludes() {
		val filter = ClasspathWildcardIncludeFilter("com.company.*", null)
		assertThat(filter.isIncluded("com/company/Foo")).isTrue()
		assertThat(filter.isIncluded("com/company/sub/Foo")).isTrue()
		assertThat(filter.isIncluded("org/company/Foo")).isFalse()
		assertThat(filter.isIncluded("org/com/company/Foo")).isFalse()

		val filterWithLeadingStar = ClasspathWildcardIncludeFilter("*com.company.*", null)
		assertThat(filterWithLeadingStar.isIncluded("com/company/Foo")).isTrue()
		assertThat(filterWithLeadingStar.isIncluded("org/com/company/Foo")).isTrue()
	}

	@Test
	fun testExcludes() {
		val filter = ClasspathWildcardIncludeFilter(null, "org.junit.*")
		assertThat(filter.isIncluded("org/junit/jupiter/engine/Constants")).isFalse()
		assertThat(filter.isIncluded("com/company/Foo")).isTrue()

		val filter2 = ClasspathWildcardIncludeFilter("com.company.*", "com.company.generated.*")
		assertThat(filter2.isIncluded("com/company/Foo")).isTrue()
		assertThat(filter2.isIncluded("com/company/generated/Foo")).isFalse()
	}

	/** Like JaCoCo, nested classes keep their `$`. */
	@Test
	fun testNestedClasses() {
		assertThat(ClasspathWildcardIncludeFilter("com.company.Outer$*", null).isIncluded("com/company/Outer\$Inner"))
			.isTrue()
		assertThat(ClasspathWildcardIncludeFilter("com.company.Outer.Inner", null).isIncluded("com/company/Outer\$Inner"))
			.isFalse()
	}

	@Test
	fun testMultiplePatterns() {
		val filter = ClasspathWildcardIncludeFilter("com.*:de.*", null)
		assertThat(filter.isIncluded("com/foo/Bar")).isTrue()
		assertThat(filter.isIncluded("de/foo/Bar\$Goo")).isTrue()
		assertThat(filter.isIncluded("org/foo/Bar")).isFalse()
	}
}
