package org.octsrv.schema

import kotlinx.serialization.Serializable

@Serializable
enum class MPJavaVersion {
    JAVA_8,
    JAVA_11,
    JAVA_17,
    JAVA_21;

    fun getImage(): String =
        "eclipse-temurin:${this.number()}-jre-alpine"

    fun number(): Int = when (this) {
        JAVA_8 -> 8
        JAVA_11 -> 11
        JAVA_17 -> 17
        JAVA_21 -> 21
    }

    companion object {
        fun parse(v: Int?): MPJavaVersion = when (v) {
            8 -> JAVA_8
            11 -> JAVA_11
            17 -> JAVA_17
            21 -> JAVA_21
            else -> JAVA_21
        }
    }
}