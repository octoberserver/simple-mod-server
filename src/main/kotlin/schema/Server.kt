package org.octsrv.schema

import org.ktorm.dsl.QueryRowSet
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.octsrv.database
import org.octsrv.util.isValidPath

data class Server(
    val id: String,
    val name: String,
    val startupScript: String,
    val javaVersion: MPJavaVersion,
    val proxyHostname: String
) {
    @Transient val volumeName = "quack_$id"
    @Transient val containerName = "quack_$id"

    init {
        this.validate()?.let {
            throw IllegalArgumentException("Error creating Server object, Invalid $it")
        }
    }

    fun validate(): String? {
        if (!Validation.serverNameRegex.matches(name)) return "name"
        if (!Validation.domainRegex.matches(proxyHostname)) return "proxy hostname"
        if (isValidPath(startupScript)) return "startup script"
        return null
    }

    companion object {
        fun fromRow(row: QueryRowSet): Server =
            Server(
                row[TServers.id]!!,
                row[TServers.name]!!,
                row[TServers.startupScript]!!,
                MPJavaVersion.parse(row[TServers.javaVersion]),
                row[TServers.proxyHostname]!!
            )

        fun of(
            id: String,
            name: String,
            startupScript: String,
            javaVersion: MPJavaVersion,
            proxyHostname: String
        ): Result<Server> = runCatching {
            Server(
                id,
                name,
                startupScript,
                javaVersion,
                proxyHostname
            )
        }

        fun findByID(id: String): Server? = database.from(TServers)
            .select()
            .where { TServers.name eq id }
            .map(::fromRow)
            .firstOrNull()
    }
}