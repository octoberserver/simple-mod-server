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
import org.flywaydb.core.Flyway
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.octsrv.Migration.migrateTable
import org.octsrv.schema.*
import java.io.ByteArrayInputStream
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    migrateTable(database, Modpacks)
    migrateTable(database, Servers)
    migrateTable(database, Seasons)

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

        post("/new-modpack") {
            val modpack = call.receive<Modpack>()
            modpack.isValid()?.let {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to it))
            }

            dockerClient.createVolumeCmd()
                .withName("epoxi-mp_${modpack.id}")
                .exec()

            database.insert(Modpacks) {
                set(it.id, modpack.id)
                set(it.startupScript, modpack.startupScript)
            }
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


            val modpack = database.from(Modpacks)
                .select()
                .map { row -> Modpack(
                    row[Modpacks.id]?:"",
                    row[Modpacks.startupScript]?:"",
                    MPJavaVersion.get(row[Modpacks.javaVersion])
                ) }
                .find { it.id == packId }
                ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "pack not found!"))

            val server = database.from(Servers)
                .select()
                .map { row -> Server(
                    row[Servers.id]?:"",
                    row[Servers.currentSeason]?:"",
                    row[Servers.proxyHostname]?:"",
                ) }
                .find { it.id == serverId }
                ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "pack not found!"))

            // 1. schedule job
            launch {
                val nowEpochSeconds = System.currentTimeMillis() / 1000

                var delaySeconds = scheduledTime - nowEpochSeconds
                if (delaySeconds < 0) delaySeconds = 0
                delay(delaySeconds.seconds)

                try {
                    newSeason(server, modpack)
                } catch (e: Exception) {
                    e.printStackTrace()
                    WebHookService.sendErrorWebhook(e.message?:"unknown error")
                }
            }

            // 2. send webhook
            WebHookService.sendScheduledNewSeason()
            // 3. send in game message
            dockerClient.attachContainerCmd(server.containerName)
                .withStdIn(ByteArrayInputStream("say 伺服器在預定時間會換包!\n".toByteArray()))
                .exec(ResultCallback.Adapter())

            return@put call.respond(HttpStatusCode.OK)
        }
    }
}