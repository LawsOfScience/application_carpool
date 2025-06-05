package org.bread_experts_group.application_carpool

import java.rmi.Remote
import java.rmi.RemoteException

interface Supervisor : Remote {
    @Throws(RemoteException::class)
    fun status(): Boolean
}