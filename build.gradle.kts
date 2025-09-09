plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    application
}


group = "org.octsrv"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-websockets:2.3.7")
    implementation("io.ktor:ktor-server-auth:2.3.7")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.7")
    implementation("io.ktor:ktor-server-status-pages:2.3.7")

    // JSON serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

    // Database
    implementation("org.ktorm:ktorm-core:4.1.1")
    implementation("org.xerial:sqlite-jdbc:3.46.1.2")
    implementation("org.flywaydb:flyway-core:10.0.0")

    // Docker integration
    implementation("com.github.docker-java:docker-java-core:3.3.6")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // HTTP client for Discord webhooks
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.ktor:ktor-server-tests:2.3.7")
}
