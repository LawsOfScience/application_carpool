package org.bread_experts_group.application_carpool

import org.bread_experts_group.application_carpool.rmi.Supervisor
import org.bread_experts_group.Flag
import org.bread_experts_group.application_carpool.rmi.StatusResult
import org.bread_experts_group.readArgs
import org.bread_experts_group.logging.ColoredLogger
import org.bread_experts_group.stringToBoolean
import java.lang.management.ManagementFactory
import java.rmi.registry.LocateRegistry
import java.util.logging.Level
import kotlin.system.exitProcess

val FLAGS = listOf(
    Flag(
        "log_level",
        "The logging level to use.",
        default = Level.WARNING,
        conv = Level::parse
    ),
    Flag(
        "start",
        "Whether to start the supervisor daemon.",
        default = false,
        conv = ::stringToBoolean
    ),
    Flag(
        "stop",
        "Whether to stop the supervisor daemon.",
        default = false,
        conv = ::stringToBoolean
    ),
    Flag(
        "status",
        "Command to get the supervisor's status",
        default = false,
        conv = ::stringToBoolean
    )
)
private val LOGGER = ColoredLogger.newLogger("ApplicationCarpool_CLI")

fun main(args: Array<String>) {
    LOGGER.info("- Reading arguments")

    val (singleArgs, _) = readArgs(args, FLAGS, "Application Carpool", "Test")

    LOGGER.level = singleArgs["log_level"] as Level
    val start = singleArgs["start"] as Boolean

    if (start)
        spawnSupervisor(singleArgs["log_level"] as Level)
    else if (checkSupervisorStatus() == null) {
        LOGGER.severe("The supervisor daemon does not appear to be running. Please start it with -start.")
        exitProcess(1)
    }

    if (singleArgs["status"] as Boolean) {
        LOGGER.fine(checkSupervisorStatus().toString())
    }
}

private fun spawnSupervisor(logLevel: Level) {
    LOGGER.info("Attempting to start supervisor daemon...")
    val supervisorStatus = checkSupervisorStatus()
    if (supervisorStatus != null) {
        LOGGER.severe("You have asked to start the supervisor daemon, but it appears to already be running (PID ${supervisorStatus.pid}).")
        exitProcess(1)
    }

    val classPath = ManagementFactory.getRuntimeMXBean().classPath
    val supervisor = Runtime.getRuntime()
        .exec(arrayOf(
            "java",
            "-cp", classPath,
            "org.bread_experts_group.application_carpool.CarpoolSupervisorMainKt",
            logLevel.toString()
        ))

    LOGGER.info("Supervisor daemon started - PID ${supervisor.pid()}.")
}

private fun checkSupervisorStatus(): StatusResult? {
    try {
        val registry = LocateRegistry.getRegistry(9085)
        val stub = registry.lookup("CarpoolSupervisor") as Supervisor
        return stub.status()
    } catch (e: Exception) {
        LOGGER.log(Level.FINE, e) { "Encountered an exception while checking the supervisor's status -- is the supervisor alive?" }
        return null
    }
}
