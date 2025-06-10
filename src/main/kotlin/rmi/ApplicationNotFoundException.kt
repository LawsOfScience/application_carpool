package rmi

import java.io.Serializable

class ApplicationNotFoundException(pid: Long) :
    Exception("No such application with PID $pid"), Serializable