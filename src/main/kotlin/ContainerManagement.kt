package org.octsrv

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.octsrv.schema.Server
import org.octsrv.util.logIfError
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

// blocking
object ContainerManagement {
    const val STOP_TIMEOUT = 10
    const val STARTUP_LIFE_CHECK = 10

    fun createContainer(server: Server) {
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

    fun runContainer(server: Server) = logIfError {
        dockerClient.startContainerCmd(server.id).exec()
        runBlocking {
            delay(STARTUP_LIFE_CHECK.seconds)
            if (!dockerClient.inspectContainerCmd(server.id).exec().state.running!!)
                WebHookService.sendFatalError("server container did not survive $STARTUP_LIFE_CHECK seconds after creation")
        }
    }

    fun stopServer(server: Server) = logIfError {
        dockerClient.attachContainerCmd(server.containerName)
            .withStdIn(ByteArrayInputStream("stop\n".toByteArray()))
            .exec(ResultCallback.Adapter())

        runBlocking { delay(STOP_TIMEOUT.seconds) }

        // return if container successfully stopped
        if (!dockerClient.inspectContainerCmd(server.containerName).exec().state.running!!)
            return@logIfError

        dockerClient.stopContainerCmd(server.containerName).exec()
    }

    fun removeContainer(server: Server) = logIfError {
        logIfError {
            dockerClient.removeContainerCmd(server.containerName)
                .withForce(true)   // remove even if running
                .exec()
        }
    }
}