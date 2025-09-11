package org.octsrv

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Mount
import com.github.dockerjava.api.model.MountType
import kotlinx.coroutines.delay
import org.ktorm.dsl.insert
import org.ktorm.dsl.update
import org.octsrv.schema.*
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

suspend fun newSeason(server: Server, modpack: Modpack) {
    // 0. create and insert season into database
    val newSeason = Season(server.nextSeasonId(), modpack.id)
    database.insert(Seasons) {
        set(it.season, newSeason.id)
        set(it.modpackId, newSeason.modpackId)
    }

    // 1. duplicate modpack volume, new volume name: season id
    // 1.1 create volume
    logIfError {
        dockerClient.createVolumeCmd()
            .withName(newSeason.volumeName)
            .exec()
    }
    // 1.2 copy volume content
    logIfError {
        val container = dockerClient.createContainerCmd("alpine")
            .withName("volume_copy_tmp_${randomHex(4)}")
            .withCmd("sh", "-c", "cp -a /from/. /to/")
            .withHostConfig(HostConfig.newHostConfig()
                .withMounts(listOf(
                    Mount().withTarget("/from").withSource(modpack.volumeName).withType(MountType.VOLUME),
                    Mount().withTarget("/to").withSource(newSeason.volumeName).withType(MountType.VOLUME)
                ))
            )
            .exec()

        println("container id: ${container.id}")

        dockerClient.startContainerCmd(container.id).exec()

        delay(10.seconds)

        dockerClient.removeContainerCmd(container.id).exec()
    }

    // 2. send in game message
    logIfError {
        dockerClient.attachContainerCmd(server.containerName)
            .withStdIn(ByteArrayInputStream("say 伺服器要換包囉!\n".toByteArray()))
            .exec(ResultCallback.Adapter())
    }

    // 3. send discord message
    WebHookService.sendNewSeasonStart()

    // 4. stop server (with 2 stage timeout)
    logIfError {
        dockerClient.attachContainerCmd(server.containerName)
            .withStdIn(ByteArrayInputStream("stop\n".toByteArray()))
            .exec(ResultCallback.Adapter())

        delay(10.seconds)
        // return if container successfully stopped
        if (!dockerClient.inspectContainerCmd(server.containerName).exec().state.running!!)
            return@logIfError

        dockerClient.stopContainerCmd(server.containerName).exec()
    }

    // 5. remove container
    logIfError {
        dockerClient.removeContainerCmd(server.containerName)
            .withForce(true)   // remove even if running
            .exec()
    }


    // 6. change latest season id
    database.update(Servers) {
        set(it.currentSeason, newSeason.id)
    }

    // 7. create new container
    val container = dockerClient.createContainerCmd(modpack.javaVersion.getImage()) // image
        .withName(server.containerName)
        .withHostConfig(
            HostConfig.newHostConfig()
                .withNetworkMode("epoxi-ingress")
            .withMounts(listOf(Mount().withTarget("/server").withSource(newSeason.volumeName).withType(MountType.VOLUME)))
        )
        .withWorkingDir("/server")
        .withExposedPorts(ExposedPort(25565))
        .withCmd(Paths.get("/server", modpack.startupScript).toString())
        .withTty(true)
        .withStdinOpen(true)
        .exec()

    // 8. run container (monitor if container survives over 1 minute)
    dockerClient.startContainerCmd(container.id).exec()
    delay(5.seconds)
    if (!dockerClient.inspectContainerCmd(container.id).exec().state.running!!) {
        // send discord message server died
        WebHookService.sendFailedNewSeason("server container did not survive 1 minute after creation")
        return@newSeason
    }
    // 9. send discord message
    WebHookService.sendNewSeasonDone()
}