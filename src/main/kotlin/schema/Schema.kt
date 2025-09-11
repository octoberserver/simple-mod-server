package org.octsrv.schema

import org.ktorm.schema.*
import org.octsrv.isValidPath

object Modpacks : Table<Nothing>("modpacks") {
    val id = varchar("id").primaryKey()
    val startupScript = varchar("startup_script") // Path to .sh file in volume
    val javaVersion = int("java_version")
}

object Servers : Table<Nothing>("servers") {
    val id = varchar("id") // Primary key, two digits: "01", "02"
    val currentSeason = varchar("current_season") // References seasons.season
    val proxyHostname = varchar("proxy_hostname") // e.g., "server01.octsrv.org"
}

object Seasons : Table<Nothing>("seasons") {
    val season = varchar("season").primaryKey() // Primary key, format: "01.001.00"
    val modpackId = varchar("modpack_id")
}

val modpackIdRegex = "^[a-z0-9_]+$".toRegex()
val serverIdRegex = "^[0-9]{2}$".toRegex()
val seasonIdRegex = "^[0-9]{2}.[0-9]{3}.[0-9]{2}\$".toRegex()
val domainRegex = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$".toRegex()

data class Modpack(val id: String, val startupScript: String, val javaVersion: MPJavaVersion) {
    val volumeName = "epoxi-modpack_${id}"
    init {
        this.validate()?.let {
            throw IllegalArgumentException("Error creating Modpack object, Invalid $it")
        }
    }
    fun validate(): String? {
        if (!modpackIdRegex.matches(id)) return "name"
        if (isValidPath(startupScript)) return "version"
        return null
    }
}

data class Server(val id: String, val currentSeason: String, val proxyHostname: String) {
    val containerName = "epoxi-server-$id"
    init {
        this.validate()?.let {
            throw IllegalArgumentException("Error creating Server object, Invalid $it")
        }
    }
    fun validate(): String? {
        if (!serverIdRegex.matches(id)) return "name"
        if (!seasonIdRegex.matches(currentSeason)) return "season"
        if (!isValidPath(proxyHostname)) return "hostname"
        return null
    }
    fun nextSeasonId(): String = currentSeason.split(".").let {
        "${it[0]}.${(it[1].toInt() + 1).toString().padStart(3, '0')}.${it[2]}"
    }
}
data class Season(val id: String, val modpackId: String) {
    val volumeName = "epoxi-season_${id}"
    init {
        this.validate()?.let {
            throw IllegalArgumentException("Error creating Season object, Invalid $it")
        }
    }
    fun validate(): String? {
        if (!seasonIdRegex.matches(id)) return "name"
        if (!modpackIdRegex.matches(modpackId)) return "modpack"
        return null
    }
}

enum class MPJavaVersion {
    JAVA_8,
    JAVA_11,
    JAVA_17,
    JAVA_21;


    fun getImage(): String = when (this) {
        JAVA_8 -> "eclipse-temurin:8-jre-alpine"
        JAVA_11 -> "eclipse-temurin:11-jre-alpine"
        JAVA_17 -> "eclipse-temurin:17-jre-alpine"
        JAVA_21 -> "eclipse-temurin:21-jre-alpine"
    }

    companion object {
        fun get(v: Int?): MPJavaVersion = when (v) {
            8 -> JAVA_8
            11 -> JAVA_11
            17 -> JAVA_17
            21 -> JAVA_21
            else -> JAVA_21
        }
    }
}