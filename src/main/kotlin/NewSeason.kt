package org.octsrv

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import kotlinx.coroutines.delay
import org.ktorm.dsl.insert
import org.ktorm.dsl.update
import org.octsrv.schema.*
import java.io.ByteArrayInputStream

suspend fun newSeason(server: Server, modpack: Modpack) {
    WebHookService.sendNewSeasonStart()

    // 0. create and insert season into database
    val newSeason = Season(server.nextSeasonId(), modpack.id)
    database.insert(Seasons) {
        set(it.season, newSeason.id)
        set(it.modpackId, newSeason.modpackId)
    }

    // 1. duplicate modpack volume, new volume name: season id
    run {
        dockerClient.createVolumeCmd()
            .withName(newSeason.id)
            .exec()

        val container = dockerClient.createContainerCmd("alpine")
            .withName("volume_copy_tmp_${randomHex(4)}")
            .withCmd("sh", "-c", "cp -a /from/. /to/")
            .withVolumes(Volume.parse(mapOf(
                modpack.id to "/from",
                newSeason.id to "/to"
            )))
            .exec()

        dockerClient.removeContainerCmd(container.id).withForce(true).exec()
    }

    // 2. send in game message
    dockerClient.attachContainerCmd(server.containerName)
        .withStdIn(ByteArrayInputStream("say 伺服器要換包囉!\n".toByteArray()))
        .exec(ResultCallback.Adapter())

    // 3. send discord message
    WebHookService.sendScheduledNewSeason()
    // 4. stop server (with 2 stage timeout)
    run {
        dockerClient.attachContainerCmd(server.containerName)
            .withStdIn(ByteArrayInputStream("stop\n".toByteArray()))
            .exec(ResultCallback.Adapter())

        delay(30_000)
        if (!dockerClient.inspectContainerCmd(server.containerName).exec().state.running!!)
            return

        dockerClient.stopContainerCmd(server.containerName).exec()
    }
    // 5. remove container
    dockerClient.removeContainerCmd(server.containerName)
        .withForce(true)   // remove even if running
        .exec()

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
        )
        .withVolumes(Volume.parse(mapOf(newSeason.id to "/server")))
        .withWorkingDir("/server")
        .withExposedPorts(ExposedPort(25565))
        .withTty(true)
        .withStdinOpen(true)
        .exec()

    // 8. run container (monitor if container survives over 1 minute)
    dockerClient.startContainerCmd(container.id).exec()
    delay(60_000)
    if (!dockerClient.inspectContainerCmd(container.id).exec().state.running!!) {
        // send discord message server died
        WebHookService.sendErrorWebhook("server container did not survive 1 minute after creation")
        return
    }
    // 9. send discord message
    WebHookService.sendNewSeasonDone()

    // if any stage fails, send discord message
}