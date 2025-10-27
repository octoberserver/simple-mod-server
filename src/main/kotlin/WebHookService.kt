package org.octsrv

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*

object WebHookService {
    private object URL {
        const val ADMIN = "https://discord.com/api/webhooks/1414791590295113811/NdZaPDj0hKBBav81LxT7JzYrsK28bAbvIiOB_LBK__KCEytEOswAfYS_ChDfioLYCw5l"
    }

    suspend fun sendFatalError(error: String) {
        sendDiscordWebhook(URL.ADMIN, "致命錯誤，錯誤訊息：$error")
    }

    suspend fun sendNonFatalError(error: String) {
        sendDiscordWebhook(URL.ADMIN, "系統出現非致命錯誤：$error")
    }

    private suspend fun sendDiscordWebhook(webhookUrl: String, message: String) {
        val client = HttpClient(CIO)

        client.post(webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"content": "${message.take(2000)}"}""")
        }

        client.close()
    }
}