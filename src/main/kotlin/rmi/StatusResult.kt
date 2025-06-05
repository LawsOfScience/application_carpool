package org.bread_experts_group.application_carpool.rmi

import java.io.Serializable

data class StatusResult(val status: Boolean, val pid: Long): Serializable