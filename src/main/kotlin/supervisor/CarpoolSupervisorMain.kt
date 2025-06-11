package org.bread_experts_group.application_carpool.supervisor

import java.io.PrintStream
import java.lang.Integer.parseInt
import java.nio.file.StandardOpenOption
import java.rmi.registry.LocateRegistry
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.system.exitProcess

private val LOGGER = Logger.getLogger("Application Carpool Supervisor")

fun main(args: Array<String>) {
    val pid = ProcessHandle.current().pid()
    val port = parseInt(args[1])
    val logDir = Path(args[2])

    val logFile = logDir.resolve("supervisor-log.txt")
    if (logFile.notExists())
        logFile.createFile()

    System.setOut(PrintStream(logFile
        .outputStream(StandardOpenOption.APPEND, StandardOpenOption.WRITE)))

    LOGGER.level = Level.parse(args[0])
    LOGGER.info("Starting supervisor daemon -- PID $pid")

    var registry = LocateRegistry.getRegistry(port)
    val supervisorStub = CarpoolSupervisor(pid, LOGGER)

    try {
        registry.bind("CarpoolSupervisor", supervisorStub)
        LOGGER.fine("Connected to the local RMI registry")
    } catch (e: Exception) {
        try {
            LOGGER.log(Level.FINE, e) { "Could not connect to the local RMI registry on port $port, attempting to create our own" }
            registry = LocateRegistry.createRegistry(port)
            registry.bind("CarpoolSupervisor", supervisorStub)
            LOGGER.fine("Connected to the self-made RMI registry on port $port")
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, e) { "Could not connect to an RMI registry. If you have your own local registry, please ensure you run it with -J-Djava.rmi.server.codebase=file:<YOUR CLASSPATH>/" }
            exitProcess(1)
        }
    }

    LOGGER.info("Supervisor daemon started")
}