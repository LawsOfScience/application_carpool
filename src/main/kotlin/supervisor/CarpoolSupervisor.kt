package org.bread_experts_group.application_carpool.supervisor

import org.bread_experts_group.application_carpool.rmi.StatusResult
import org.bread_experts_group.application_carpool.rmi.Supervisor
import rmi.ServiceInfo
import rmi.ServiceNotFoundException
import java.util.logging.Logger
import kotlin.system.exitProcess

class CarpoolSupervisor(val pid: Long, val logger: Logger) : Supervisor {
    val services = mutableMapOf<Long, ServiceEntry>()

    override fun status(): StatusResult {
        return StatusResult(true, pid)
    }
    override fun stop() {
        logger.info("Stop request received, shutting down services")
        for (service in services.values) {
            service.handle.destroy()
        }
        logger.info("Services shut down, exiting supervisor daemon")
        exitProcess(0)
    }

    override fun listServices(): List<ServiceInfo> {
        return services.values.map {
            ServiceInfo(it.handle.pid(), it.commandString, it.handle.isAlive)
        }
    }

    override fun addService(commandArray: Array<String>): Long {
        val commandString = commandArray.joinToString(" ")
        logger.info("Starting service $commandString")

        val service = Runtime.getRuntime().exec(commandArray)
        services[service.pid()] = ServiceEntry(service, commandString)
        return service.pid()
    }

    override fun removeService(pid: Long) {
        if (!services.containsKey(pid)) {
            logger.warning("Could not find service with PID $pid")
            throw ServiceNotFoundException(pid)
        }

        val service = services[pid]!!
        service.handle.destroy()
        services.remove(pid)
        logger.info("Removed service with PID $pid (${service.commandString})")
    }

    data class ServiceEntry(val handle: Process, val commandString: String)
}