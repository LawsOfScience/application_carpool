package org.bread_experts_group.application_carpool.supervisor

import org.bread_experts_group.application_carpool.rmi.Supervisor
import rmi.ApplicationInfo
import rmi.ApplicationNotFoundException
import java.rmi.server.UnicastRemoteObject
import java.util.logging.Logger
import kotlin.system.exitProcess

class CarpoolSupervisor(val pid: Long, val logger: Logger) : UnicastRemoteObject(0), Supervisor {
    val applications = mutableMapOf<Long, ApplicationEntry>()

    override fun status() = pid

    override fun stop() {
        logger.info("Stop request received, shutting down applications")
        for (app in applications.values) app.handle.destroy()
        logger.info("Applications shut down, exiting supervisor daemon")
        exitProcess(0)
    }

    override fun listApplications(): List<ApplicationInfo> {
        return applications.values.map {
            ApplicationInfo(it.handle.pid(), it.commandString, it.handle.isAlive)
        }
    }

    override fun addApplication(commandArray: Array<String>): Long {
        val commandString = commandArray.joinToString(" ")
        logger.info { "Starting application $commandString" }

        val app = Runtime.getRuntime().exec(commandArray)
        applications[app.pid()] = ApplicationEntry(app, commandString)
        return app.pid()
    }

    override fun removeApplication(pid: Long) {
        if (!applications.containsKey(pid)) {
            logger.warning { "Could not find application with PID $pid" }
            throw ApplicationNotFoundException(pid)
        }

        val app = applications[pid]!!
        app.handle.destroy()
        applications.remove(pid)
        logger.info { "Removed application with PID $pid (${app.commandString})" }
    }

    data class ApplicationEntry(val handle: Process, val commandString: String)
}