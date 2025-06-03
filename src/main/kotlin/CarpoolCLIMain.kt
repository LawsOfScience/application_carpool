import org.bread_experts_group.Flag
import org.bread_experts_group.readArgs
import org.bread_experts_group.logging.ColoredLogger
import org.bread_experts_group.stringToBoolean
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.util.logging.Level
import kotlin.io.path.exists
import kotlin.io.path.notExists
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
        "socket",
        "The location of the socket used to talk with the supervisor daemon.",
        default = Path.of("./carpool.sock"),
        conv = Path::of
    ),
)
val LOGGER = ColoredLogger.newLogger("ApplicationCarpool_CLI")

fun main(args: Array<String>) {
    LOGGER.info("- Reading arguments")

    val (singleArgs, _) = readArgs(args, FLAGS, "Application Carpool", "Test")

    LOGGER.level = singleArgs["log_level"] as Level
    val start = singleArgs["start"] as Boolean
    val socket = singleArgs["socket"] as Path

    if (start)
        spawnSupervisor(socket)
    else if (socket.notExists()) {
        LOGGER.severe("The supervisor daemon does not appear to be running. Please start it with -start.")
        exitProcess(1)
    }
}

fun spawnSupervisor(socketPath: Path) {
    LOGGER.info("Attempting to start supervisor daemon...")
    if (socketPath.exists()) {
        LOGGER.severe("You have asked to start the supervisor daemon, but it appears to already be running.")
        exitProcess(1)
    }

    val classPath = ManagementFactory.getRuntimeMXBean().classPath
    val supervisor = Runtime.getRuntime().exec(arrayOf("java", "-cp", classPath, "CarpoolSupervisorMainKt"))

    LOGGER.info("Supervisor daemon started - PID ${supervisor.pid()}.")
}
