// Names of the configurations through which projects share build artifacts with each other.
//
// Producers expose an artifact under these names, consumers depend on the producing project targeting the
// same name. Sharing artifacts this way instead of reaching into another project's tasks is what keeps the
// build compatible with project isolation.

/** The shaded agent jar, produced by :agent and consumed via the com.teamscale.agent-jar convention plugin. */
const val AGENT_JAR_CONFIGURATION = "agentJarElements"

/** The jlink runtime image of the installer, produced by :installer and shipped in the :agent distribution. */
const val JLINK_IMAGE_CONFIGURATION = "jlinkImageElements"
