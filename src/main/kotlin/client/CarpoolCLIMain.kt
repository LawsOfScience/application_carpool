package org.bread_experts_group.application_carpool.client

import org.bread_experts_group.application_carpool.rmi.Supervisor
import org.bread_experts_group.Flag
import org.bread_experts_group.MultipleArgs
import org.bread_experts_group.SingleArgs
import org.bread_experts_group.application_carpool.rmi.StatusResult
import org.bread_experts_group.readArgs
import org.bread_experts_group.logging.ColoredLogger
import org.bread_experts_group.stringToBoolean
import org.bread_experts_group.stringToInt
import java.lang.management.ManagementFactory
import java.rmi.UnmarshalException
import java.rmi.registry.LocateRegistry
import java.util.logging.Level
import kotlin.system.exitProcess

val FLAGS = listOf(
    Flag(
        "log_level",
        "The logging level to use.",
        default = Level.INFO,
        conv = Level::parse
    ),
    Flag(
        "port",
        "The port to use for the RMI registry.",
        default = 1099,
        conv = ::stringToInt
    ),
    Flag(
        "start",
        "Whether to start the supervisor daemon.",
        default = false,
        conv = ::stringToBoolean
    ),
    Flag(
        "stop",
        "Command to stop the supervisor daemon.",
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
val COMMANDS = listOf("status")

fun main(args: Array<String>) {
    LOGGER.info("- Reading arguments")

    val (singleArgs, multipleArgs) = readArgs(args, FLAGS, "Application Carpool", "Test")
    LOGGER.level = singleArgs["log_level"] as Level

    val start = singleArgs["start"] as Boolean
    val stop = singleArgs["stop"] as Boolean
    val port = singleArgs["port"] as Int
    if (start) {
        if (singleArgs["stop"] as Boolean) {
            LOGGER.severe("Please only use EITHER -start or -stop.")
            exitProcess(1)
        }
        spawnSupervisor(singleArgs["log_level"] as Level, port)
    } else if (checkSupervisorStatus(port) == null) {
        LOGGER.severe("The supervisor daemon does not appear to be running. Please start it with -start.")
        exitProcess(1)
    }

    val registry = LocateRegistry.getRegistry(port)
    val supervisor = registry.lookup("CarpoolSupervisor") as Supervisor
    if (stop)
        try {
            supervisor.stop()
        } catch (_: UnmarshalException) {  // expected, so ignore
        } finally {
            LOGGER.info("Supervisor daemon stopped")
            exitProcess(0)
        }

    handleCommands(singleArgs.filterKeys { COMMANDS.contains(it) }, multipleArgs, supervisor)
}

private fun handleCommands(singleArgs: SingleArgs, multipleArgs: MultipleArgs, supervisor: Supervisor) {
    for (arg in singleArgs)
        when (arg.key) {
            "status" -> if (singleArgs["status"] as Boolean) {
                val status = try {
                    supervisor.status()
                } catch (e: Exception) {
                    LOGGER.info("There was an exception getting the supervisor's status -- is it online?")
                    LOGGER.log(Level.FINE, e) { "The produced exception was" }
                    continue
                }
                LOGGER.info(status.toString())
            }
        }
}

private fun spawnSupervisor(logLevel: Level, port: Int) {
    LOGGER.info("Attempting to start supervisor daemon...")
    val supervisorStatus = checkSupervisorStatus(port)
    if (supervisorStatus != null) {
        LOGGER.severe("You have asked to start the supervisor daemon, but it appears to already be running (PID ${supervisorStatus.pid}).")
        exitProcess(1)
    }

    val classPath = ManagementFactory.getRuntimeMXBean().classPath
    val supervisor = Runtime.getRuntime()
        .exec(arrayOf(
            "java",
            "-cp", classPath,
            "org.bread_experts_group.application_carpool.supervisor.CarpoolSupervisorMainKt",
            "$logLevel",
            "$port"
        ))

    LOGGER.info("Supervisor daemon started - PID ${supervisor.pid()}.")
}

private fun checkSupervisorStatus(port: Int): StatusResult? {
    try {
        val registry = LocateRegistry.getRegistry(port)
        val stub = registry.lookup("CarpoolSupervisor") as Supervisor
        return stub.status()
    } catch (e: Exception) {
        LOGGER.log(Level.FINE, e) { "Encountered an exception while checking the supervisor's status -- is the supervisor alive?" }
        return null
    }
}
