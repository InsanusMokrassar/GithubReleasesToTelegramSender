package dev.inmo.github.release.tgbotapi.sender

import dev.inmo.micro_utils.repos.KeyValueRepo

typealias RepoId = Long
typealias ReleaseId = Long
typealias PublishedRepo = KeyValueRepo<RepoId, ReleaseId>
