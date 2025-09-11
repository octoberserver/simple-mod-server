package org.octsrv

import io.ktor.http.*
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

inline fun handleNonFatalError(callback: (Exception) -> Unit, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        callback(e)
        return
    }
}