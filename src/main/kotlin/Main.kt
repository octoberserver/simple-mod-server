package org.octsrv

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
import org.octsrv.ContainerManagement.createContainer
import org.octsrv.ContainerManagement.createVolume
import org.octsrv.ContainerManagement.dockerClient
import org.octsrv.ContainerManagement.getServerStatus
import org.octsrv.ContainerManagement.removeContainer
import org.octsrv.ContainerManagement.startContainer
import org.octsrv.ContainerManagement.stopContainer
import org.octsrv.Migration.migrateTable
import org.octsrv.schema.*
import org.octsrv.util.randomHex
import org.octsrv.util.serverFromIdParam
import java.io.File

//const val MC_ROUTER_HOSTS_FILE_PATH = "/app/config/hosts.json"
const val MC_ROUTER_HOSTS_FILE_PATH = "./build/config/hosts.json"

val database = Database.connect(
    url = "jdbc:sqlite:main.db",
    driver = "org.sqlite.JDBC"
)

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
            return@get call.respondText("Hello, Ktor!")
        }

        get("/json") {
            return@get call.respond(mapOf("message" to "Hello, JSON!"))
        }

        post("/s") {
            val server = runCatching {
                call.receive<Server>()
            }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to it))
            }

            createVolume(server).onFailure {
                it.printStackTrace()
                WebHookService.sendNonFatalError("Failed to create volume for ${server.name}: ${it.message}")
                return@post call.respond(HttpStatusCode.InternalServerError, mapOf("error" to it))
            }
            createContainer(server).onFailure {
                it.printStackTrace()
                WebHookService.sendNonFatalError("Failed to create container for ${server.name}: ${it.message}")
                return@post call.respond(HttpStatusCode.InternalServerError, mapOf("error" to it))
            }

            database.insert(TServers) {
                set(it.id, randomHex(8))
                set(it.name, server.name)
                set(it.startupScript, server.startupScript)
                set(it.javaVersion, server.javaVersion.number())
                set(it.proxyHostname, server.proxyHostname)
            }
        }

        get("/s") {
            return@get call.respond(HttpStatusCode.OK, database
                .from(TServers)
                .select()
                .map(Server::fromRow)
                .map(::getServerStatus)
            )
        }

        get("/s/{id}") {
            val server = serverFromIdParam(call.parameters["id"]).getOrElse {
                return@get call.respond(it.status, mapOf("error" to it.error))
            }
            return@get call.respond(
                HttpStatusCode.OK,
                getServerStatus(server)
            )
        }

        patch("/s/{id}") {
            // TODO:
            // 1. Allow modification of id, reverse proxy hostname, startup script, java ver
            // 2. Modify / Recreate container and rename volume after change if needed
        }

        delete("/s/{id}") {
            val server = serverFromIdParam(call.parameters["id"]).getOrElse {
                return@delete call.respond(it.status, mapOf("error" to it.error))
            }

            removeContainer(server).onFailure {
                it.printStackTrace()
                WebHookService.sendNonFatalError("Failed to create container for ${server.name}: ${it.message}")
                return@delete call.respond(HttpStatusCode.InternalServerError, mapOf("error" to it))
            }

            database.delete(TServers) {
                it.id eq server.id
            }

            return@delete call.respond(HttpStatusCode.OK)
        }

        post("/s/{id}/start") {
            val server = serverFromIdParam(call.parameters["id"]).getOrElse {
                return@post call.respond(it.status, mapOf("error" to it.error))
            }
            startContainer(server).onFailure {
                it.printStackTrace()
                WebHookService.sendNonFatalError("Failed to start container for ${server.name}: ${it.message}")
                return@post call.respond(HttpStatusCode.InternalServerError, mapOf("error" to it))
            }
            return@post call.respond(HttpStatusCode.OK)
        }

        post("/s/{id}/stop") {
            val server = serverFromIdParam(call.parameters["id"]).getOrElse {
                return@post call.respond(it.status, mapOf("error" to it.error))
            }
            stopContainer(server).onFailure {
                it.printStackTrace()
                WebHookService.sendNonFatalError("Failed to stop container for ${server.name}: ${it.message}")
                return@post call.respond(HttpStatusCode.InternalServerError, mapOf("error" to it))
            }
            return@post call.respond(HttpStatusCode.OK)
        }
    }
}