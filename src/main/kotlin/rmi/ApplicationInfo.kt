package xyz.aerasto.application_carpool.rmi

import java.io.Serializable

data class ApplicationInfo(
    val pid: Long,
    val commandString: String,
    val isRunning: Boolean,
    val logFile: String
) : Serializable