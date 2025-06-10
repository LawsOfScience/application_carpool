package org.bread_experts_group.application_carpool.rmi

import rmi.ApplicationInfo
import rmi.ApplicationNotFoundException
import java.rmi.Remote
import java.rmi.RemoteException

interface Supervisor : Remote {
    @Throws(RemoteException::class)
    fun status(): StatusResult

    @Throws(RemoteException::class)
    fun stop()

    @Throws(RemoteException::class)
    fun listApplications(): List<ApplicationInfo>

    @Throws(RemoteException::class)
    fun addApplication(commandArray: Array<String>): Long

    @Throws(RemoteException::class, ApplicationNotFoundException::class)
    fun removeApplication(pid: Long)
}