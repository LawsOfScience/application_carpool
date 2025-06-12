package org.bread_experts_group.application_carpool.supervisor

import org.bread_experts_group.application_carpool.rmi.Supervisor
import org.bread_experts_group.command_line.Flag
import org.bread_experts_group.command_line.readArgs
import org.bread_experts_group.command_line.stringToInt
import rmi.ApplicationInfo
import rmi.ApplicationNotFoundException
import java.nio.file.Path
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import kotlin.io.path.Path
import kotlin.io.path.outputStream
import kotlin.system.exitProcess

private val FLAGS = listOf(
    Flag(
        "log_level",
        "Log level",
        default = Level.INFO,
        conv = Level::parse
    ),
    Flag(
        "port",
        "Port",
        default = 1099,
        conv = stringToInt()
    ),
    Flag(
        "log_dir",
        "Log directory",
        default = Path("./logs/"),
        conv = { Path(it) }
    )
)
private val LOGGER = Logger.getLogger("Application Carpool Supervisor")

class CarpoolSupervisor(val pid: Long, val logDir: Path) : UnicastRemoteObject(0), Supervisor {
    val applications = mutableMapOf<Long, ApplicationEntry>()

    override fun status() = pid

    override fun stop() {
        LOGGER.info("Stop request received, shutting down applications")
        for (app in applications.values)  {
            app.handle.destroy()
            app.logFileTransferHandle.interrupt()
        }
        LOGGER.info("Applications shut down, exiting supervisor daemon")
        exitProcess(0)
    }

    override fun listApplications(): List<ApplicationInfo> {
        return applications.values.map {
            ApplicationInfo(
                it.handle.pid(),
                it.commandString,
                it.handle.isAlive,
                it.logFile.toString()
            )
        }
    }

    override fun addApplication(commandArray: Array<String>): Long {
        val commandString = commandArray.joinToString(" ")
        LOGGER.info { "Starting application $commandString" }

        val app = Runtime.getRuntime().exec(commandArray)

        LOGGER.fine("Creating log file")
        val appLogFile = logDir.resolve("${app.pid()}_log.txt")
        val transferThread = Thread.ofVirtual().start {
            app.inputStream.transferTo(appLogFile.outputStream())
        }

        applications[app.pid()] = ApplicationEntry(app, commandString, appLogFile, transferThread)
        LOGGER.info { "Started app [${commandString}] -- PID ${app.pid()}" }
        return app.pid()
    }

    override fun removeApplication(pid: Long) {
        if (!applications.containsKey(pid)) {
            LOGGER.warning { "Could not find application with PID $pid" }
            throw ApplicationNotFoundException(pid)
        }

        val app = applications[pid]!!
        app.handle.destroy()
        applications.remove(pid)
        LOGGER.info { "Removed application with PID $pid (${app.commandString})" }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val args = readArgs(
                args,
                FLAGS,
                "Application Carpool Supervisor",
                "Application Carpool Supervisor process"
            )
            val pid = ProcessHandle.current().pid()
            val port = args.getRequired<Int>("port")

            val logFileHandler = FileHandler(
                args.getRequired<Path>("log_dir").resolve("supervisor-log.txt").toString(),
            )
            logFileHandler.formatter = SimpleFormatter()

            LOGGER.useParentHandlers = false
            LOGGER.addHandler(logFileHandler)
            LOGGER.level = args.getRequired<Level>("log_level")
            LOGGER.info { "Starting supervisor daemon -- PID $pid" }

            var registry = LocateRegistry.getRegistry(port)
            val supervisorStub = CarpoolSupervisor(pid, args.getRequired<Path>("log_dir"))

            try {
                registry.bind("CarpoolSupervisor", supervisorStub)
                LOGGER.fine("Connected to the local RMI registry")
            } catch (e: Exception) {
                try {
                    LOGGER.log(Level.FINE, e) {
                        "Could not connect to the local RMI registry on port $port, attempting to create our own"
                    }
                    registry = LocateRegistry.createRegistry(port)
                    registry.bind("CarpoolSupervisor", supervisorStub)
                    LOGGER.fine { "Connected to the self-made RMI registry on port $port" }
                } catch (e: Exception) {
                    LOGGER.log(Level.SEVERE, e) {
                        "Could not connect to an RMI registry. If you have your own local registry, " +
                                "please ensure you run it with -J-Djava.rmi.server.codebase=file:<YOUR CLASSPATH>/"
                    }
                    exitProcess(1)
                }
            }

            LOGGER.info("Supervisor daemon started")
        }
    }

    data class ApplicationEntry(
        val handle: Process,
        val commandString: String,
        val logFile: Path,
        val logFileTransferHandle: Thread
    )
}