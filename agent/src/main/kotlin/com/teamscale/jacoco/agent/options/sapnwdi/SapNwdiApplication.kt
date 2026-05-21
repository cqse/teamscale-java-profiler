package com.teamscale.jacoco.agent.options.sapnwdi

import com.teamscale.jacoco.agent.options.AgentOptionParseException

/**
 * An SAP application that is identified by a [.markerClass] and refers to a corresponding Teamscale project.
 */
data class SapNwdiApplication(
	/** A fully qualified class name that is used to match a jar file to this application.  */
	val markerClass: String,
	/** The teamscale project to which coverage should be uploaded.  */
	val teamscaleProject: String
) {
	companion object {
		/** Parses an application definition string e.g. "com.package.MyClass:projectId;com.company.Main:project". */
		@Throws(AgentOptionParseException::class)
		fun parseApplications(applications: String): List<SapNwdiApplication> {
			if (applications.isBlank()) {
				throw AgentOptionParseException("Application definition is expected not to be empty.")
			}
			val markerClassAndProjectPairs = applications.split(";")

			return markerClassAndProjectPairs.map { pair ->
				if (pair.isBlank()) {
					throw AgentOptionParseException(
						"Empty entry in option 'sap-nwdi-applications'." +
								" Provide entries in the form 'com.your.MarkerClass:teamscale-project-id', separated by ';'."
					)
				}

				val parts = pair.split(":").dropLastWhile { it.isEmpty() }
				if (parts.size != 2) {
					throw AgentOptionParseException(
						"Application definition $pair is expected to contain a marker class and project separated by a colon."
					)
				}

				val markerClass = parts[0].trim()
				if (markerClass.isEmpty()) {
					throw AgentOptionParseException(
						"Option 'sap-nwdi-applications': no marker class given in entry '$pair'." +
								" Use the form 'com.your.MarkerClass:teamscale-project-id'."
					)
				}

				val teamscaleProject = parts[1].trim()
				if (teamscaleProject.isEmpty()) {
					throw AgentOptionParseException(
						"Option 'sap-nwdi-applications': no Teamscale project given in entry '$pair'." +
								" Use the form 'com.your.MarkerClass:teamscale-project-id'."
					)
				}

				SapNwdiApplication(markerClass, teamscaleProject)
			}
		}
	}
}
