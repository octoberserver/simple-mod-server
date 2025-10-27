package org.octsrv.schema

import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.varchar

object TServers : Table<Nothing>("modpacks") {
    val id = varchar("id").primaryKey()
    val name = varchar("name")
    val startupScript = varchar("startup_script") // Path to .sh file in volume
    val javaVersion = int("java_version")
    val proxyHostname = varchar("proxy_hostname")
}