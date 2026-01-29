package com.teamscale.test_impacted

import java.util.*

/** Provides access to the Impacted Test Engine version at runtime. */
object BuildVersion {

    private val bundle = ResourceBundle.getBundle("com.teamscale.test_impacted.app")

    /** The version of the Teamscale Impacted Test Engine. */
    val VERSION: String = bundle.getString("version")
}
