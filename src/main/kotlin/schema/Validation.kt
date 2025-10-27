package org.octsrv.schema

object Validation {
    val serverNameRegex = "^[a-z0-9_]+$".toRegex()
    val domainRegex = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$".toRegex()
}