package rmi

import java.io.Serializable

data class ApplicationInfo(val pid: Long, val commandString: String, val isRunning: Boolean) : Serializable