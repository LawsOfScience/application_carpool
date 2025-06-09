package rmi

import java.io.Serializable

data class ServiceInfo(val pid: Long, val commandString: String, val isRunning: Boolean) : Serializable