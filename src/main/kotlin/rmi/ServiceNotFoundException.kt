package rmi

import java.io.Serializable

class ServiceNotFoundException(pid: Long) :
    Exception("No such service with PID $pid"), Serializable