package dev.inmo.github.release.tgbotapi.sender

import korlibs.time.*
import dev.inmo.krontab.*
import dev.inmo.micro_utils.repos.MapKeyValueRepo
import dev.inmo.micro_utils.repos.exposed.keyvalue.ExposedKeyValueRepo
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.MilliSeconds
import kotlinx.serialization.*
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub

@Serializable
data class Config(
    private val botToken: String,
    private val personalGithubToken: String,
    @SerialName("repos")
    private val reposNames: List<String>,
    val targetChatId: ChatId,
    val debug: Boolean = false,
    private val databaseConfig: DatabaseConfig? = null,
    val maxReleasesPublish: Int? = null,
    private val checkDelay: MilliSeconds? = null,
    private val krontab: KrontabTemplate? = null,
    val supervisorChatId: ChatId? = null
) {
    @Transient
    val bot: TelegramBot = telegramBot(botToken)
    @Transient
    val gitHub: GitHub = GitHub.connectUsingOAuth(personalGithubToken)
    @Transient
    val repos: List<GHRepository> = reposNames.map(gitHub::getRepository)
    @Transient
    val publishedVersionsRepo: PublishedRepo = databaseConfig?.database ?.let {
        ExposedKeyValueRepo(
            it,
            { long("repo_id") },
            { long("release_id") },
            "PublishedReleasesTable"
        )
    } ?: MapKeyValueRepo()

    @Transient
    val kronScheduler = krontab ?.let(::buildSchedule) ?: checkDelay?.let { millis ->
        KronScheduler {
            it + millis.milliseconds
        }
    } ?: error("It is required to declare one of the fields: checkDelay (in millis) or krontab template")
}
