package org.bread_experts_group.application_carpool.rmi

import rmi.ServiceInfo
import java.rmi.Remote
import java.rmi.RemoteException

interface Supervisor : Remote {
    @Throws(RemoteException::class)
    fun status(): StatusResult

    @Throws(RemoteException::class)
    fun stop()

    @Throws(RemoteException::class)
    fun listServices(): List<ServiceInfo>

    @Throws(RemoteException::class)
    fun addService(commandArray: Array<String>): Long
}