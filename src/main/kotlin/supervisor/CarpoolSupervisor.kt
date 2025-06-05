package org.bread_experts_group.application_carpool.supervisor

import org.bread_experts_group.application_carpool.rmi.StatusResult
import org.bread_experts_group.application_carpool.rmi.Supervisor
import java.util.logging.Logger
import kotlin.system.exitProcess

class CarpoolSupervisor(val pid: Long, val logger: Logger) : Supervisor {
    override fun status(): StatusResult {
        return StatusResult(true, pid)
    }
    override fun stop() {
        logger.info("Exiting supervisor daemon")
        exitProcess(0)
    }
}