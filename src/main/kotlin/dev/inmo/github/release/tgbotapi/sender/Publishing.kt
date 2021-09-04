package dev.inmo.github.release.tgbotapi.sender

import dev.inmo.micro_utils.repos.set
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.send.*
import dev.inmo.tgbotapi.extensions.api.send.media.sendDocument
import dev.inmo.tgbotapi.extensions.api.send.media.sendDocumentsGroup
import dev.inmo.tgbotapi.extensions.utils.formatting.boldMarkdownV2
import dev.inmo.tgbotapi.extensions.utils.formatting.linkMarkdownV2
import dev.inmo.tgbotapi.requests.abstracts.FileId
import dev.inmo.tgbotapi.requests.abstracts.FileUrl
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.media.TelegramMediaDocument
import dev.inmo.tgbotapi.types.mediaCountInMediaGroup
import dev.inmo.tgbotapi.types.message.MarkdownV2
import dev.inmo.tgbotapi.utils.bold
import dev.inmo.tgbotapi.utils.extensions.escapeMarkdownV2Common
import dev.inmo.tgbotapi.utils.link
import org.kohsuke.github.GHRelease

suspend fun TelegramBot.publishRelease(
    release: GHRelease,
    repo: PublishedRepo,
    targetChatId: ChatId
) {
    val ownerRepo = release.owner

    val sent = send(
        targetChatId
    ) {
        bold(ownerRepo.fullName) + ": " + link(release.name, release.htmlUrl.toString())
    }
    release.listAssets().map {
        it.browserDownloadUrl
    }.chunked(mediaCountInMediaGroup.last).forEach {
        if (it.size == 1) {
            replyWithDocument(
                sent,
                FileUrl(it.first())
            )
        } else {
            replyWithDocuments(
                sent,
                it.map { url ->
                    TelegramMediaDocument(FileUrl(url))
                }
            )
        }
    }

    repo.set(ownerRepo.id, release.id)
}
