package org.octsrv

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*

object WebHookService {
    private object URL {
        const val ADMIN = "https://discord.com/api/webhooks/1414791590295113811/NdZaPDj0hKBBav81LxT7JzYrsK28bAbvIiOB_LBK__KCEytEOswAfYS_ChDfioLYCw5l"
        const val BROADCAST = "https://discord.com/api/webhooks/1414791590295113811/NdZaPDj0hKBBav81LxT7JzYrsK28bAbvIiOB_LBK__KCEytEOswAfYS_ChDfioLYCw5l"
    }

    private object BroadCast {
        const val SCHEDULED_NEW_SEASON = "已排成換季"
        const val NEW_SEASON_START = "開始換季"
        const val NEW_SEASON_DONE = "換季完成"
        const val FAILED = "換季失敗"
    }

    suspend fun sendFatalErrorWebhook(error: String) {
        sendDiscordWebhook(URL.BROADCAST, BroadCast.FAILED)
        sendDiscordWebhook(URL.ADMIN, "伺服器炸了，錯誤訊息：$error")
    }

    suspend fun sendNonFatalErrorWebhook(error: String) {
        sendDiscordWebhook(URL.ADMIN, "系統出現非致命錯誤：$error")
    }

    suspend fun sendScheduledNewSeason() =
        sendDiscordWebhook(URL.BROADCAST, BroadCast.SCHEDULED_NEW_SEASON)

    suspend fun sendNewSeasonStart() =
        sendDiscordWebhook(URL.BROADCAST, BroadCast.NEW_SEASON_START)

    suspend fun sendNewSeasonDone() =
        sendDiscordWebhook(URL.BROADCAST, BroadCast.NEW_SEASON_DONE)

    private suspend fun sendDiscordWebhook(webhookUrl: String, message: String) {
        val client = HttpClient(CIO)

        client.post(webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody("""{"content": "$message"}""")
        }

        client.close()
    }
}