package org.octsrv.util

import kotlinx.coroutines.runBlocking
import org.octsrv.WebHookService
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import kotlin.random.Random

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
        sb.append(Random.Default.nextInt(0, 16).toString(16))
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
            WebHookService.sendNonFatalError(e.message ?: "unknown error")
        }
        return
    }
}