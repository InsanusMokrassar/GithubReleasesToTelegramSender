## GitHub Releases → Telegram Sender

A small Kotlin service that periodically checks specified GitHub repositories for new releases and posts them to a Telegram chat or channel using `dev.inmo:tgbotapi`. Each release announcement includes a link to the release and all attached assets (sent as Telegram documents). The service persists the last published release per repository to avoid duplicates and supports a supervisor-only `/force_refresh` command.

### Features

- **Monitors GitHub releases**: Watches one or more repositories
- **Publishes to Telegram**: Sends release link and attaches assets
- **Avoids duplicates**: Stores `repoId → releaseId` mapping
- **Flexible scheduling**: Simple delay or cron-like schedule
- **Supervisor command**: `/force_refresh` to trigger an immediate check
- **Runs anywhere**: Local or Docker

### Requirements

- **Java**: 17+
- **GitHub**: Personal access token (for API access)
- **Telegram**: Bot token
- **Build tool**: Gradle (for local build)

### Configuration

The application expects a JSON config file path as the only argument. You must provide either `checkDelay` (milliseconds) or `krontab` (cron template).

```json
{
  "botToken": "123456789:AA...your_bot_token...",
  "personalGithubToken": "ghp_...your_github_token...",
  "repos": [
    "owner1/repo1",
    "owner2/repo2"
  ],
  "targetChatId": 123456789,
  "debug": false,
  "databaseConfig": {
    "url": "jdbc:sqlite:/data/published.db",
    "driver": "org.sqlite.JDBC",
    "username": "",
    "password": ""
  },
  "maxReleasesPublish": 5,
  "checkDelay": 600000,
  "krontab": null,
  "supervisorChatId": 123456789
}
```

- **botToken**: Telegram Bot token obtained from `@BotFather`.
- **personalGithubToken**: GitHub token used to access repo releases (choose scopes appropriate to your repos; public repos typically need minimal scopes).
- **repos**: List of repositories in `owner/name` format.
- **targetChatId**: Target Telegram chat or channel ID to post into.
- **debug** (optional): Enables more verbose logging via KSLog.
- **databaseConfig** (optional): Storage for published release IDs. Defaults to in-memory if omitted. For persistence, use an SQLite file path as shown above.
- **maxReleasesPublish** (optional): Limit how many past releases are published on catch-up.
- **checkDelay** (optional): Interval in milliseconds between checks.
- **krontab** (optional): Cron-like schedule string using [`dev.inmo:krontab`](https://github.com/InsanusMokrassar/krontab) template. Provide either `checkDelay` or `krontab`. For constructing of your krontab use [KrontabPredictor](https://insanusmokrassar.github.io/KrontabPredictor/)
- **supervisorChatId** (optional): If set, the bot will accept `/force_refresh` in that chat.

Notes:
- For channels, ensure the bot is an admin and use the channel’s chat ID.
- If both `checkDelay` and `krontab` are null, the app will fail to start.

### Running locally

- Using Gradle run:
```bash
./gradlew run --args="/absolute/path/to/config.json"
```

- Using the installed distribution:
```bash
./gradlew installDist
./build/install/github.releases.tgbotapi.sender/bin/github.releases.tgbotapi.sender /absolute/path/to/config.json
```

### Docker

The Docker image expects the Gradle distribution tar in `build/distributions`.

1) Build distribution:
```bash
./gradlew clean distTar
```

2) Build image:
```bash
docker build -t gh-releases-to-telegram .
```

3) Run (mount config into container at `/bot/config.json` and a data directory for persistent DB):
```bash
docker run --rm \
  -v /absolute/path/to/config.json:/bot/config.json:ro \
  -v /absolute/path/to/data:/data \
  gh-releases-to-telegram
```

- Ensure your `databaseConfig.url` points to a persistent path (e.g., `jdbc:sqlite:/data/published.db`) if you want state across restarts.
- The entrypoint runs: `/bot/github.releases.tgbotapi.sender/bin/github.releases.tgbotapi.sender /bot/config.json`.

### `docker-compose.yml`

```yaml
services:
  github_releases_tgbotapi_sender:
    image: insanusmokrassar/github_releases_tgbotapi_sender
    container_name: github_releases_tgbotapi_sender
    restart: always
    volumes:
      - "${DATA_PATH}:/bot/"
```

Here `${DATA_PATH}` is environment variable with path to folder with data. Put in this folder file `config.json` with your config. It will be used by bot as user with `1000:1000` ids.

### Bot commands

If `supervisorChatId` is set, the bot registers and accepts:
- `/force_refresh` — Immediately triggers release checks and publishing.

This command is scoped only to the `supervisorChatId`.

### How it works

- Fetches latest release for each repo via `org.kohsuke:github-api`.
- Compares with the stored last-published release ID (via `micro_utils` repo storage).
- Publishes new releases in chronological order, sending:
  - A message with `owner/repo: [release name](link)`
  - All release assets as documents (grouped when possible)
- Scheduling uses `dev.inmo:krontab` or a fixed delay loop.

### Building

```bash
./gradlew build
```

Artifacts:
- Distribution tar: `build/distributions/github.releases.tgbotapi.sender.tar`
- Installed distro: `build/install/github.releases.tgbotapi.sender/`

### Troubleshooting

- “It is required to declare one of the fields: checkDelay or krontab”: Add one of them to your config.
- No messages appear: Verify `targetChatId`, bot permissions, and that the bot is not privacy-restricted in groups/channels.
- Duplicate posts: Ensure persistent `databaseConfig` so the app can remember previously published releases.

