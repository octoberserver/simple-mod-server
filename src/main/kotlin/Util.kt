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
            WebHookService.sendNonFatalErrorWebhook(e.message?:"unknown error")
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
