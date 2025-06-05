package org.bread_experts_group.application_carpool

import org.bread_experts_group.application_carpool.rmi.StatusResult
import org.bread_experts_group.application_carpool.rmi.Supervisor

class CarpoolSupervisor(val pid: Long) : Supervisor {
    override fun status(): StatusResult {
        return StatusResult(true, pid)
    }
}