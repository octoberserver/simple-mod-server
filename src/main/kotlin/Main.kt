package org.octsrv

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.octsrv.Migration.migrateTable
import org.octsrv.schema.*
import org.octsrv.schema.TServers.id
import org.octsrv.util.logIfError
import org.octsrv.util.serverFromIdParam
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import kotlin.text.get

//const val MC_ROUTER_HOSTS_FILE_PATH = "/app/config/hosts.json"
const val MC_ROUTER_HOSTS_FILE_PATH = "./build/config/hosts.json"

val database = Database.connect(
    url = "jdbc:sqlite:main.db",
    driver = "org.sqlite.JDBC"
)

val dockerClient: DockerClient = run {
    val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig)
        .maxConnections(100)
        .connectionTimeout(Duration.ofSeconds(30))
        .responseTimeout(Duration.ofSeconds(45))
        .build()
    DockerClientImpl.getInstance(config, httpClient)
}

fun main() {
    val file = File(MC_ROUTER_HOSTS_FILE_PATH)
    file.parentFile.mkdirs()
    file.createNewFile()

    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    migrateTable(database, TServers)

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/") {
            call.respondText("Hello, Ktor!")
        }

        get("/json") {
            call.respond(mapOf("message" to "Hello, JSON!"))
        }

        post("/s") {
            val server = runCatching {
                call.receive<Server>()
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to it))
            }

            dockerClient.createVolumeCmd()
                .withName(server.volumeName)
                .exec()

            database.insert(TServers) {
                set(it.id, server.id)
                set(it.startupScript, server.startupScript)
                set(it.javaVersion, server.javaVersion.number())
                set(it.proxyHostname, server.proxyHostname)
            }
        }

        get("/s") {
            call.respond(database.from(TServers).select().map { row -> Server(
                row[TServers.id]?:"",
                row[TServers.startupScript]?:"",
                MPJavaVersion.parse(row[TServers.javaVersion]),
                row[TServers.proxyHostname]?:""
            ) })
        }

        patch("/s/{id}") {
            // TODO:
            // 1. Allow modification of id, reverse proxy hostname, startup script, java ver
            // 2. Modify / Recreate container and rename volume after change if needed
        }

        delete("/s/{id}") {
            val id = serverFromIdParam(call.parameters["id"]).getOrElse {
                return@delete call.respond(it.status, mapOf("error" to it.error))
            }.id

            database.delete(TServers) {
                it.id eq id
            }

            return@delete call.respond(HttpStatusCode.OK)
        }

        post("/s/{id}/start") {
            val server = serverFromIdParam(call.parameters["id"]).getOrElse {
                return@post call.respond(it.status, mapOf("error" to it.error))
            }
        }
    }
}