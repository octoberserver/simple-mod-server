package org.octsrv.util

import io.ktor.http.*
import org.octsrv.schema.Server
import org.octsrv.schema.Validation

sealed class HttpResult<out T> {
    data class Success<out T>(val value: T) : HttpResult<T>()
    data class Error(
        val error: String,
        val status: HttpStatusCode,
    ) : HttpResult<Nothing>()

    fun getOrNull(): T? = if (this is Success) value else null
    fun getOrThrow(): T = if (this is Success) value else throw IllegalStateException("Cannot get value from Error")
    inline fun getOrElse(callback: (Error) -> Nothing): T = when (this) {
        is Success -> value
        is Error -> callback(this)
    }
    fun toErrorOrNull(): Error? = this as? Error
    fun toErrorOrThrow(): Error = toErrorOrNull() ?:
        throw IllegalStateException("Is not error")
    fun getStatusCode(): HttpStatusCode = when(this) {
        is Success -> HttpStatusCode.OK
        is Error -> status
    }
    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Error
    fun <R> map(transform: (value: T) -> R): HttpResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Error -> Error(error, status)
    }
}

fun serverFromIdParam(id: String?): HttpResult<Server> = when {
    (id == null) ->
        HttpResult.Error("id is missing!", HttpStatusCode.BadRequest)
    (!Validation.serverNameRegex.matches(id)) ->
        HttpResult.Error("invalid id!", HttpStatusCode.BadRequest)
    else -> {
        val server = Server.findByID(id)?:
            return HttpResult.Error("server not found!", HttpStatusCode.NotFound)
        HttpResult.Success(server)
    }
}