package xyz.aerasto.application_carpool.rmi

import java.rmi.Remote
import java.rmi.RemoteException

interface Supervisor : Remote {
    @Throws(RemoteException::class)
    fun status(): Long

    @Throws(RemoteException::class)
    fun stop()

    @Throws(RemoteException::class)
    fun listApplications(): List<ApplicationInfo>

    @Throws(RemoteException::class)
    fun addApplication(commandArray: Array<String>): Long

    @Throws(RemoteException::class, ApplicationNotFoundException::class)
    fun removeApplication(pid: Long)
}