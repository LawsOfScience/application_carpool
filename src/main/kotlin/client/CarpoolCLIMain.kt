package org.bread_experts_group.application_carpool.client

import org.bread_experts_group.application_carpool.rmi.Supervisor
import org.bread_experts_group.Flag
import org.bread_experts_group.MultipleArgs
import org.bread_experts_group.SingleArgs
import org.bread_experts_group.readArgs
import org.bread_experts_group.logging.ColoredLogger
import org.bread_experts_group.stringToBoolean
import org.bread_experts_group.stringToInt
import org.bread_experts_group.stringToLong
import rmi.ApplicationNotFoundException
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.rmi.UnmarshalException
import java.rmi.registry.LocateRegistry
import java.util.logging.Level
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

val FLAGS = listOf(
    Flag(
        "log_level",
        "The logging level to use. If starting the supervisor, this level will be used for it as well.",
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
        "log_directory",
        "The directory to write logs to.",
        default = Path("./logs/"),
        conv = { Path(it) }
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
        "Command to get the supervisor's status.",
        default = false,
        conv = ::stringToBoolean
    ),
    Flag(
        "list_applications",
        "Command to list all applications managed by the supervisor.",
        default = false,
        conv = ::stringToBoolean
    ),
    Flag(
        "add_application",
        "Command to create a managed application.",
        default = "",
        repeatable = true
    ),
    Flag(
        "remove_application",
        "Command to remove a managed application.",
        default = -1,
        repeatable = true,
        conv = ::stringToLong
    )
)
private val LOGGER = ColoredLogger.newLogger("Application Carpool CLI")
val SINGLE_COMMANDS = listOf("list_applications", "status", "stop")

fun main(args: Array<String>) {
    val (singleArgs, multipleArgs) = readArgs(
        args,
        FLAGS,
        "Application Carpool",
        "A program for running your applications in the background."
    )
    LOGGER.level = singleArgs["log_level"] as Level
    LOGGER.fine("Reading arguments")

    (singleArgs["log_directory"] as Path).createDirectories()

    connectToSupervisor(singleArgs, multipleArgs, false)
}

private fun connectToSupervisor(singleArgs: SingleArgs, multipleArgs: MultipleArgs, started: Boolean) {
    LOGGER.fine("Connecting to supervisor (reconnection?: $started)")

    val port = singleArgs["port"] as Int
    val trySupervisor: Result<Supervisor> = runCatching {
        val registry = LocateRegistry.getRegistry(port)
        registry.lookup("CarpoolSupervisor") as Supervisor
    }

    if (singleArgs["start"] as Boolean && !started) {
        if (singleArgs["stop"] as Boolean) {
            LOGGER.severe("Please only use EITHER -start or -stop.")
            exitProcess(1)
        }

        trySupervisor.onSuccess {
            val supervisorPid = it.status().pid
            LOGGER.severe("You have asked to start the supervisor daemon, but it appears to already be running (PID $supervisorPid).")
            exitProcess(1)
        }

        spawnSupervisor(LOGGER.level, port, singleArgs["log_directory"] as Path)
        LOGGER.info("Giving the supervisor time to wake up...")
        Thread.sleep(500)
        LOGGER.fine("Attempting to connect to newly-started supervisor")
        connectToSupervisor(singleArgs, multipleArgs, true)
        return
    }

    trySupervisor.onSuccess { supervisor ->
        handleCommands(singleArgs.filterKeys { SINGLE_COMMANDS.contains(it) }, multipleArgs, supervisor)
    }.onFailure { e ->
        LOGGER.log(Level.SEVERE, e) { "The supervisor daemon does not appear to be running. Please start it with -start." }
        exitProcess(1)
    }
}

private fun handleCommands(singleArgs: SingleArgs, multipleArgs: MultipleArgs, supervisor: Supervisor) {
    if (singleArgs["stop"] as Boolean)
        try {
            supervisor.stop()
        } catch (_: UnmarshalException) {  // expected, so ignore
        } finally {
            LOGGER.info("Supervisor daemon stopped")
            exitProcess(0)
        }

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

                if (status.status)
                    println("Supervisor online, PID ${status.pid}")
                else
                    println("Supervisor OFFLINE -- please check your configuration")
            }
            "list_applications" -> if (singleArgs["list_applications"] as Boolean) {
                val applications = supervisor.listApplications()
                println("Currently ${applications.size} application(s)\n-------")
                for (app in applications) {
                    println("${app.commandString}\n    -PID: ${app.pid}\n    -Alive?: ${app.isRunning}")
                }
            }
        }

    for (arg in multipleArgs)
        when (arg.key) {
            "add_application" -> for (app in arg.value) {
                val asString = app as String
                if (asString == "")
                    continue

                val commandString = asString.split(" ").toTypedArray()
                val appPid = supervisor.addApplication(commandString)
                println("Started application [$asString] -- PID $appPid")
            }
            "remove_application" -> for (app in arg.value) {
                val asLong = app as Long
                if (asLong == -1L)
                    continue

                try {
                    supervisor.removeApplication(asLong)
                    println("Removed application with PID $asLong")
                } catch (anfe: ApplicationNotFoundException) {
                    LOGGER.warning("There is no application with PID $asLong")
                    LOGGER.log(Level.FINE, anfe) { "Exception info:" }
                }
            }
        }
}

private fun spawnSupervisor(logLevel: Level, port: Int, logDir: Path) {
    LOGGER.info("Attempting to start supervisor daemon...")

    val classPath = ManagementFactory.getRuntimeMXBean().classPath
    val supervisor = Runtime.getRuntime()
        .exec(arrayOf(
            "java",
            "-cp", classPath,
            "org.bread_experts_group.application_carpool.supervisor.CarpoolSupervisorMainKt",
            "$logLevel",
            "$port",
            logDir.absolutePathString()
        ))

    LOGGER.info("Supervisor daemon started - PID ${supervisor.pid()}.")
}