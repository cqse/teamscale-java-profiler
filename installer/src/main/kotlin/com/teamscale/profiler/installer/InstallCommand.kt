package com.teamscale.profiler.installer

import okhttp3.HttpUrl
import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * CLI command that installs the profiler.
 */
@CommandLine.Command(name = "install", description = ["Installs the profiler."])
class InstallCommand : Callable<Int> {
	@CommandLine.Parameters(
		index = "0",
		converter = [HttpUrlTypeConverter::class],
		description = ["The URL of your Teamscale instance."]
	)
	private var teamscaleUrl: HttpUrl? = null

	@CommandLine.Parameters(
		index = "1",
		description = ["The user used to access your Teamscale instance and upload coverage to your projects."]
	)
	private var userName: String? = null

	@CommandLine.Parameters(index = "2", description = ["The access key of the given user. NOT the password!"])
	private var accessKey: String? = null

	override fun call(): Int {
		try {
			return Installer.install(TeamscaleCredentials(teamscaleUrl, userName, accessKey))
		} catch (t: Throwable) {
			t.printStackTrace(System.err)
			System.err.println(
				"""
					
					
					Installation failed due to an internal error. This is likely a bug, please report the entire console output to support@teamscale.com
					""".trimIndent()
			)
			return RootCommand.EXIT_CODE_PROGRAMMING_ERROR
		}
	}
}
