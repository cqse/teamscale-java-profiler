package com.teamscale.aggregation.junit

import org.gradle.api.tasks.Sync
import org.gradle.work.DisableCachingByDefault

/** Task used to collect JUnit reports. This is a subclass of [Sync] to act as a marker in the [TeamscaleUpload] task. */
@DisableCachingByDefault(because = "Sync tasks are not cacheable")
abstract class JUnitReportCollectionTask : Sync()
