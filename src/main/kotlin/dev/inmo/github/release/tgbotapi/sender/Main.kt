package dev.inmo.github.release.tgbotapi.sender

import dev.inmo.krontab.doInfinity
import dev.inmo.kslog.common.*
import dev.inmo.micro_utils.coroutines.CoroutineScope
import dev.inmo.micro_utils.coroutines.launchSafelyWithoutExceptions
import dev.inmo.micro_utils.coroutines.runCatchingSafely
import dev.inmo.micro_utils.repos.pagination.getAll
import dev.inmo.micro_utils.repos.set
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.commands.BotCommandScope
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.kohsuke.github.GHRelease
import java.io.File
import java.util.logging.*

suspend fun main(args: Array<String>) {
    val config: Config = Json {
        ignoreUnknownKeys = true
    }.decodeFromString(Config.serializer(), File(args.first()).readText())

    val scope = CoroutineScope(Dispatchers.IO) {
        it.printStackTrace()
    }

    val logger = Logger.getLogger("KSLog").apply {
        level = Level.ALL
        handlers.onEach {
            it.level = Level.ALL
        }.ifEmpty {
            addHandler(
                ConsoleHandler().apply {
                    formatter = SimpleFormatter()
                    level = Level.ALL
                }
            )
        }
    }

    if (config.debug) {
        KSLog.default = KSLog("publisher", logger, LogLevel.DEBUG)
    } else {
        KSLog.default = KSLog("publisher", logger, LogLevel.VERBOSE)
    }

    suspend fun Config.updateVersions() {
        repos.forEach { repo ->
            runCatchingSafely {
                val latestRelease = repo.latestRelease ?: return@runCatchingSafely
                KSLog.v(repo.name) { "Latest release is ${latestRelease.name}" }
                when (val latestPublished = publishedVersionsRepo.get(repo.id)) {
                    null -> {
                        KSLog.v(repo.name) { "Publish first release (${latestRelease.name})" }
                        bot.publishRelease(
                            latestRelease,
                            publishedVersionsRepo,
                            targetChatId
                        )
                    }
                    latestRelease.id -> {
                        KSLog.v("version of ${repo.fullName} is actual and equal to ${latestRelease.name}")
                        return@runCatchingSafely
                    }
                    else -> {
                        KSLog.v(repo.name) { "Start checking of releases" }
                        repo.listReleases().filter {
                            it.id > latestPublished
                        }.distinctBy { it.name }.sortedBy { it.createdAt.time }.let { releases ->
                            config.maxReleasesPublish ?.let {
                                releases.takeLast(it)
                            } ?: releases
                        }.also {
                            KSLog.v("Start publishing of next release: ${it.joinToString { it.name }}")
                        }.onEach { release ->
                            KSLog.v(repo.name) { "publishing of ${release.name}" }
                            bot.publishRelease(
                                release,
                                publishedVersionsRepo,
                                targetChatId
                            )
                            publishedVersionsRepo.set(repo.id, release.id)
                        }
                    }
                }
            }.onFailure {
                KSLog.e("Something went wrong", it)
            }
        }
    }

    config.apply {
        KSLog.v("Current internal state of repos: ${publishedVersionsRepo.getAll { keys(it) }.joinToString { "${it.first}: ${it.second}" }}")
        KSLog.v("Start handling of repos ${repos.joinToString { it.fullName }}")
        scope.launch {
            kronScheduler.doInfinity {
                updateVersions()
            }
        }
    }

    config.supervisorChatId ?.let { supervisorId ->
        config.bot.buildBehaviourWithLongPolling {
            onCommand("force_refresh", initialFilter = { it.chat.id == supervisorId }) {
                reply(it, "Launching refresh")
                scope.launchSafelyWithoutExceptions {
                    config.updateVersions()
                }
            }

            setMyCommands(
                BotCommand("force_refresh", "Force refresh of versions"),
                scope = BotCommandScope.Chat(supervisorId)
            )
        }
    }

    scope.coroutineContext.job.join()
}
