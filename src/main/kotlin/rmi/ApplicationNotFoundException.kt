package xyz.aerasto.application_carpool.rmi

import java.io.Serializable

class ApplicationNotFoundException(pid: Long) :
    Exception("No such application with PID $pid"), Serializable