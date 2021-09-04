FROM bellsoft/liberica-openjdk-alpine:17

RUN mkdir "/bot" && chown -R 1000:1000 "/bot"

USER 1000

ADD ./build/distributions/github.releases.tgbotapi.sender.tar /bot

ENTRYPOINT ["/bot/github.releases.tgbotapi.sender/bin/github.releases.tgbotapi.sender", "/bot/config.json"]
