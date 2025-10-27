package org.octsrv.util

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.octsrv.MC_ROUTER_HOSTS_FILE_PATH
import org.octsrv.WebHookService
import java.io.File
import java.io.IOException
import kotlin.collections.set

fun addServerToProxy(proxyHostname: String, containerName: String) {
    error("Not yet activated!")

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