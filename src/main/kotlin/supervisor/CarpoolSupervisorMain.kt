package org.bread_experts_group.application_carpool.supervisor

import org.bread_experts_group.command_line.Flag
import org.bread_experts_group.command_line.readArgs
import org.bread_experts_group.command_line.stringToInt
import java.nio.file.Path
import java.rmi.registry.LocateRegistry
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.Path
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

fun main(args: Array<String>) {
    val args = readArgs(
        args,
        FLAGS,
        "Application Carpool Supervisor",
        "Application Carpool Supervisor process"
    )
    val pid = ProcessHandle.current().pid()
    val port = args.getRequired<Int>("port")

    LOGGER.useParentHandlers = false
    LOGGER.addHandler(FileHandler(
        args.getRequired<Path>("log_dir").resolve("supervisor-log.txt").toString()
    ))
    LOGGER.level = args.getRequired<Level>("log_level")
    LOGGER.info { "Starting supervisor daemon -- PID $pid" }

    var registry = LocateRegistry.getRegistry(port)
    val supervisorStub = CarpoolSupervisor(pid, LOGGER)

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