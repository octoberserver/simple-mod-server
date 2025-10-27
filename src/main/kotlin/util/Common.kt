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

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }

    return "${"%.2f".format(size)} ${units[unitIndex]}"
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