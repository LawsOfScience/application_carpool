package org.bread_experts_group.application_carpool.rmi

import java.rmi.Remote
import java.rmi.RemoteException

interface Supervisor : Remote {
    @Throws(RemoteException::class)
    fun status(): StatusResult
}