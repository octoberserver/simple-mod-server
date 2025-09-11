package org.octsrv

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.ktorm.dsl.from
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.octsrv.schema.MPJavaVersion
import org.octsrv.schema.Modpack
import org.octsrv.schema.Modpacks
import org.octsrv.schema.Server
import org.octsrv.schema.Servers
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import kotlin.random.Random
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class ErrorWStatus(val message: String, val code: HttpStatusCode) {}

fun isValidPath(path: String): Boolean {
    return try {
        Paths.get(path)
        true
    } catch (e: InvalidPathException) {
        false
    }
}

fun randomHex(length: Int): String {
    val sb = StringBuilder()
    repeat(length) {
        sb.append(Random.nextInt(0, 16).toString(16))
    }
    return sb.toString()
}

inline fun logIfError(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        System.err.println("Non Fatal Error! Continuing Task...")
        System.err.println(e.message)
        e.printStackTrace()
        runBlocking {
            WebHookService.sendNonFatalError(e.message?:"unknown error")
        }
        return
    }
}

fun getServerFromId(id: String): Server? = database.from(Servers)
    .select()
    .map { row -> Server(
        row[Servers.id]?:"",
        row[Servers.currentSeason]?:"",
        row[Servers.proxyHostname]?:"",
    ) }
    .find {
        it.id == id
    }

fun getModpackFromId(id: String): Modpack? = database.from(Modpacks)
    .select()
    .map { row -> Modpack(
        row[Modpacks.id]?:"",
        row[Modpacks.startupScript]?:"",
        MPJavaVersion.get(row[Modpacks.javaVersion])
    ) }
    .find {
        it.id == id
    }

fun addServerToProxy(proxyHostname: String, containerName: String) {
    if (proxyHostname.isEmpty())
        return

    try {
        val file = File(MC_ROUTER_HOSTS_FILE_PATH)

        // Read and parse the existing JSON file
        val json = try {
            Json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            JsonObject(mapOf())
        }
        val updatedMappings = json["mappings"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        // Add the new server mapping
        updatedMappings[proxyHostname] = JsonPrimitive("$containerName:25565")
        val updatedJson = json.toMutableMap()
        updatedJson["mappings"] = JsonObject(updatedMappings)

        // Save the updated JSON back to the file
        file.writeText(Json.encodeToString(JsonElement.serializer(), JsonObject(updatedJson)))


    } catch (e: IOException) {
        runBlocking {
            WebHookService.sendFatalError("Failed to update /mc-router/hosts.json: ${e.message}")
        }
        System.err.println("Failed to update /mc-router/hosts.json: ${e.message}")
        e.printStackTrace()
    } catch (e: Exception) {
        runBlocking {
            WebHookService.sendFatalError("Unexpected error: ${e.message}")
        }
        System.err.println("Unexpected error: ${e.message}")
        e.printStackTrace()
    }
}