package org.octsrv

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.octsrv.Migration.migrateTable
import org.octsrv.schema.*
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    migrateTable(database, Modpacks)
    migrateTable(database, Servers)
    migrateTable(database, Seasons)

    install(ContentNegotiation) {
        json()
    }

    @Serializable
    data class NewServerReq(val id: String, val proxyHostname: String) {
        fun validate(): String? {
            if (!serverIdRegex.matches(id))
                return "Invalid server id!"
            if (!domainRegex.matches(proxyHostname))
                return "Invalid proxy hostname!"
            return null
        }
    }

    routing {
        get("/") {
            call.respondText("Hello, Ktor!")
        }

        get("/json") {
            call.respond(mapOf("message" to "Hello, JSON!"))
        }

        post("/new-modpack") {
            val modpack = call.receive<Modpack>()
            modpack.validate()?.let {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to it))
            }

            dockerClient.createVolumeCmd()
                .withName(modpack.volumeName)
                .exec()

            database.insert(Modpacks) {
                set(it.id, modpack.id)
                set(it.startupScript, modpack.startupScript)
            }
        }

        get("/modpacks") {
            call.respond(database.from(Modpacks).select().map { row -> Modpack(
                row[Modpacks.id]?:"",
                row[Modpacks.startupScript]?:"",
                MPJavaVersion.get(row[Modpacks.javaVersion])
            ) })
        }

        delete("/modpack/{id}") {
            val id = call.parameters["id"] ?:
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is missing!"))
            if (!modpackIdRegex.matches(id))
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is invalid!"))

            getModpackFromId(id)?:
                return@delete call.respond(HttpStatusCode.NotFound)

            database.delete(Modpacks) {
                it.id eq id
            }

            // remove volume
            logIfError {
                dockerClient.removeVolumeCmd(id).exec()
            }

            return@delete call.respond(HttpStatusCode.OK)
        }

        post("/server") {
            val req = call.receive<NewServerReq>()
            req.validate()?.let {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to it))
            }

            getServerFromId(req.id)?.let {
                return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "server id already exists!"))
            }

            val server = Server(
                req.id,
                "${req.id}.000.00",
                req.proxyHostname
            )

            database.insert(Servers) {
                set(it.id, server.id)
                set(it.currentSeason, server.currentSeason)
                set(it.proxyHostname, server.proxyHostname)
            }

            addServerToProxy(req.proxyHostname, server.containerName)

            return@post call.respond(HttpStatusCode.OK)
        }

        get("/servers") {
            call.respond(database.from(Servers).select().map { row -> Server(
                row[Servers.id]?:"",
                row[Servers.currentSeason]?:"",
                row[Servers.proxyHostname]?:"",
            ) })
        }

        patch("/server/{id}") {
            val id = call.parameters["id"] ?:
                return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is missing!"))
            if (!serverIdRegex.matches(id))
                return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is invalid!"))

            getServerFromId(id)?:
                return@patch call.respond(HttpStatusCode.NotFound)

            val req = call.receive<NewServerReq>()
            req.validate()?.let {
                return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to it))
            }

            val server = getServerFromId(id) ?:
                return@patch call.respond(HttpStatusCode.NotFound, mapOf("error" to "server not found!"))
            addServerToProxy(server.id, server.containerName)

            database.update(Servers) {
                set(it.proxyHostname, req.proxyHostname)
                where {
                    it.id eq id
                }
            }
        }

        delete("/server/{id}") {
            val id = call.parameters["id"] ?:
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is missing!"))
            if (!serverIdRegex.matches(id))
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "id is invalid!"))

            getServerFromId(id)?:
                return@delete call.respond(HttpStatusCode.NotFound)

            database.delete(Servers) {
                it.id eq id
            }

            return@delete call.respond(HttpStatusCode.OK)
        }

        put("/server/{serverId}/new-season/{packId}") {
            val serverId = run {
                val id = call.parameters["serverId"] ?:
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "serverId is missing!"))
                if (!serverIdRegex.matches(id))
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "serverId is invalid!"))
                id
            }
            val packId = run {
                val id = call.parameters["packId"] ?:
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "packId is missing!"))
                if (!modpackIdRegex.matches(id))
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "packId is invalid!"))
                id
            }

            val scheduledTime: Long = call.request.queryParameters["time"]
                ?.toLongOrNull() ?: ((System.currentTimeMillis() / 1000) + 5)

            val modpack = getModpackFromId(packId)?:
                return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "pack not found!"))

            val server = getServerFromId(serverId)?:
                return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "server not found!"))

            // 1. schedule job
            launch {
                // calculate wait time
                val nowEpochSeconds = System.currentTimeMillis() / 1000

                var delaySeconds = scheduledTime - nowEpochSeconds
                if (delaySeconds < 0) delaySeconds = 0
                // wait until scheduled time
                delay(delaySeconds.seconds)

                // run and catch errors
                try {
                    newSeason(server, modpack)
                } catch (e: Exception) {
                    e.printStackTrace()
                    WebHookService.sendFailedNewSeason(e.message?:"unknown error")
                }
            }

            // 2. send webhook
            WebHookService.sendScheduledNewSeason()

            // 3. send in game message
            logIfError {
                dockerClient.attachContainerCmd(server.containerName)
                    .withStdIn(ByteArrayInputStream("say 伺服器在預定時間會換包!\n".toByteArray()))
                    .exec(ResultCallback.Adapter())
            }
            return@put call.respond(HttpStatusCode.OK)
        }
    }
}