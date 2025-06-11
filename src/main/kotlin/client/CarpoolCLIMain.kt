package org.bread_experts_group.application_carpool.client

import org.bread_experts_group.application_carpool.rmi.Supervisor
import org.bread_experts_group.command_line.ArgumentContainer
import org.bread_experts_group.command_line.Flag
import org.bread_experts_group.command_line.readArgs
import org.bread_experts_group.logging.ColoredHandler
import org.bread_experts_group.command_line.stringToBoolean
import org.bread_experts_group.command_line.stringToInt
import org.bread_experts_group.command_line.stringToLong
import rmi.ApplicationNotFoundException
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.rmi.UnmarshalException
import java.rmi.registry.LocateRegistry
import java.util.logging.Level
import java.util.logging.Logger
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
        conv = stringToInt()
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
        conv = stringToLong()
    )
)
private val LOGGER = Logger.getLogger("Application Carpool CLI")

fun main(args: Array<String>) {
    LOGGER.useParentHandlers = false
    LOGGER.addHandler(ColoredHandler())

    val args = readArgs(
        args,
        FLAGS,
        "Application Carpool",
        "A program for running your applications in the background."
    )
    LOGGER.level = args.getRequired<Level>("log_level")
    LOGGER.fine("Reading arguments")

    args.getRequired<Path>("log_directory").createDirectories()

    connectToSupervisor(args, false)
}

private fun connectToSupervisor(args: ArgumentContainer, started: Boolean) {
    LOGGER.fine("Connecting to supervisor (reconnection?: $started)")

    val port = args.getRequired<Int>("port")
    val trySupervisor: Result<Supervisor> = runCatching {
        val registry = LocateRegistry.getRegistry(port)
        registry.lookup("CarpoolSupervisor") as Supervisor
    }

    if (args.getRequired<Boolean>("start") && !started) {
        if (args.getRequired<Boolean>("stop")) {
            LOGGER.severe("Please only use EITHER -start or -stop.")
            exitProcess(1)
        }

        trySupervisor.onSuccess {
            val supervisorPid = it.status()
            LOGGER.severe("You have asked to start the supervisor daemon, but it appears to already be running (PID $supervisorPid).")
            exitProcess(1)
        }

        spawnSupervisor(LOGGER.level, port, args.getRequired<Path>("log_directory"))
        LOGGER.info("Giving the supervisor time to wake up...")
        Thread.sleep(500)
        LOGGER.fine("Attempting to connect to newly-started supervisor")
        connectToSupervisor(args, true)
        return
    }

    trySupervisor.onSuccess { supervisor ->
        handleCommands(args, supervisor)
    }.onFailure { e ->
        LOGGER.log(Level.SEVERE, e) { "The supervisor daemon does not appear to be running. Please start it with -start." }
        exitProcess(1)
    }
}

private fun handleCommands(args: ArgumentContainer, supervisor: Supervisor) {
    if (args.getRequired<Boolean>("stop"))
        try {
            supervisor.stop()
        } catch (_: UnmarshalException) {  // expected, so ignore
        } finally {
            LOGGER.info("Supervisor daemon stopped")
            exitProcess(0)
        }

    for (arg in args.of.keys)
        when (arg) {
            "status" -> if (args.getRequired<Boolean>("status")) {
                LOGGER.info { "Supervisor online, PID: ${supervisor.status()}" }
            }
            "list_applications" -> if (args.getRequired<Boolean>("list_applications")) {
                val applications = supervisor.listApplications()
                LOGGER.info("Currently ${applications.size} application(s)")
                for (app in applications)
                    LOGGER.info("${app.commandString}\n    -PID: ${app.pid}\n    -Alive?: ${app.isRunning}")
            }
            "add_application" -> for (app in args.getsRequired<String>("add_application")) {
                if (app.isEmpty()) continue

                val commandString = app.split(" ").toTypedArray()
                val appPid = supervisor.addApplication(commandString)
                LOGGER.info("Started application [$app] -- PID $appPid")
            }
            "remove_application" -> for (appPid in args.getsRequired<Long>("remove_application")) {
                if (appPid == -1L) continue

                try {
                    supervisor.removeApplication(appPid)
                    LOGGER.info("Removed application with PID $appPid")
                } catch (anfe: ApplicationNotFoundException) {
                    LOGGER.warning("There is no application with PID $appPid")
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