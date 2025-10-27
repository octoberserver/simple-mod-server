package org.octsrv.schema

data class ServerStatus(
    val server: Server,
    val running: Boolean,
    val cpuPercent: Double,
    val memoryPercent: Double,
    val memoryUsage: Long
)