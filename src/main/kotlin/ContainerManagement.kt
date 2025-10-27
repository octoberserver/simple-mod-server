package org.octsrv

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.octsrv.schema.Server
import org.octsrv.schema.ServerStatus
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import java.time.Duration
import kotlin.compareTo
import kotlin.time.Duration.Companion.seconds
import kotlin.times

// blocking
object ContainerManagement {
    const val STOP_TIMEOUT = 10
    const val STARTUP_LIFE_CHECK = 10

    val dockerClient: DockerClient = run {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()
        DockerClientImpl.getInstance(config, httpClient)
    }

    fun createVolume(server: Server): Result<Nothing?> = runCatching {
        dockerClient.createVolumeCmd()
            .withName(server.volumeName)
            .exec()
        null
    }

    fun createContainer(server: Server): Result<CreateContainerResponse> = runCatching {
        dockerClient.createContainerCmd(server.javaVersion.getImage()) // image
            .withName(server.containerName)
            .withHostConfig(
                HostConfig.newHostConfig()
                    .withNetworkMode("epoxi-ingress")
                    .withMounts(listOf(Mount()
                        .withTarget("/server")
                        .withSource(server.volumeName)
                        .withType(MountType.VOLUME)
                    ))
            )
            .withWorkingDir("/server")
            .withExposedPorts(ExposedPort(25565))
            .withCmd(Paths.get("/server", server.startupScript).toString())
            .withTty(true)
            .withStdinOpen(true)
            .exec()
    }

    fun startContainer(server: Server): Result<Nothing?> = runCatching {
        dockerClient.startContainerCmd(server.containerName).exec()
        runBlocking {
            delay(STARTUP_LIFE_CHECK.seconds)
            if (!dockerClient.inspectContainerCmd(server.containerName).exec().state.running!!)
                WebHookService.sendFatalError("server container did not survive $STARTUP_LIFE_CHECK seconds after creation")
        }
        null
    }

    fun stopContainer(server: Server): Result<Nothing?> = runCatching {
        dockerClient.attachContainerCmd(server.containerName)
            .withStdIn(ByteArrayInputStream("stop\n".toByteArray()))
            .exec(ResultCallback.Adapter())

        runBlocking { delay(STOP_TIMEOUT.seconds) }

        // return if container successfully stopped
        if (!dockerClient.inspectContainerCmd(server.containerName).exec().state.running!!)
            return@runCatching null

        dockerClient.stopContainerCmd(server.containerName).exec()
        null
    }

    fun removeContainer(server: Server): Result<Nothing?> = runCatching {
        dockerClient.removeContainerCmd(server.containerName)
            .withForce(true)   // remove even if running
            .exec()
        null
    }

    fun getServerStatus(server: Server): ServerStatus = buildList<Statistics> {
        dockerClient.statsCmd(server.containerName)
            .withNoStream(true)
            .exec(object : ResultCallback.Adapter<Statistics>() {
                override fun onNext(stats: Statistics) {
                    add(stats)
                }
            }).awaitCompletion()
    }.firstOrNull()?.let { stats ->
        val memoryUsage = stats.memoryStats.usage ?: 0L
        ServerStatus(
            server = server,
            running = dockerClient.inspectContainerCmd(server.containerName).exec().state.running == true,
            cpuPercent = calculateCpuPercent(stats),
            memoryPercent = stats.memoryStats.limit?.let { limit ->
                if (limit > 0) (memoryUsage.toDouble() / limit) * 100.0 else 0.0
            } ?: 0.0,
            memoryUsage = memoryUsage
        )
    } ?: ServerStatus(server, false, 0.0, 0.0, 0L)

    private fun calculateCpuPercent(stats: Statistics): Double {
        val cpuDelta = (stats.cpuStats.cpuUsage?.totalUsage ?: 0L) - (stats.preCpuStats.cpuUsage?.totalUsage ?: 0L)
        val systemDelta = (stats.cpuStats.systemCpuUsage?: 0L) - (stats.preCpuStats.systemCpuUsage?: 0L)
        val numberOfCores = stats.cpuStats.cpuUsage.percpuUsage?.size ?: 1

        return if (systemDelta > 0 && cpuDelta > 0) {
            (cpuDelta.toDouble() / systemDelta.toDouble()) * numberOfCores * 100.0
        } else 0.0
    }
}