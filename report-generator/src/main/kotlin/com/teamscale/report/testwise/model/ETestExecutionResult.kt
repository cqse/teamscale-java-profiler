/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2005-2018 The ConQAT Project                               |
|                                                                          |
| Licensed under the Apache License, Version 2.0 (the "License");          |
| you may not use this file except in compliance with the License.         |
| You may obtain a copy of the License at                                  |
|                                                                          |
|    http://www.apache.org/licenses/LICENSE-2.0                            |
|                                                                          |
| Unless required by applicable law or agreed to in writing, software      |
| distributed under the License is distributed on an "AS IS" BASIS,        |
| WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. |
| See the License for the specific language governing permissions and      |
| limitations under the License.                                           |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.report.testwise.model

/** The result of a test execution.  */
enum class ETestExecutionResult {
	/** Test execution was successful.  */
	PASSED,

	/** The test is currently marked as "do not execute" (e.g. JUnit @Ignore).  */
	IGNORED,

	/** Caused by a failing assumption.  */
	SKIPPED,

	/** Caused by a failing assertion.  */
	FAILURE,

	/** Caused by an error during test execution (e.g. exception thrown in the test runner code, not the test itself).  */
	ERROR,

	/** The test was executed, but its actual result is ambiguous. */
	INCONCLUSIVE
}
