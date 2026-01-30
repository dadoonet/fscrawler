FROM eclipse-temurin:25-jre-alpine

RUN set -ex \
    && apk add --no-cache \
    procps \
    curl \
    && addgroup -g 10001 fscrawler \
    && adduser -u 10001 -G fscrawler -h /home/fscrawler -D fscrawler

COPY --chown=fscrawler:fscrawler maven /usr/share/fscrawler

USER fscrawler
WORKDIR /home/fscrawler

ENTRYPOINT [ "/usr/share/fscrawler/bin/fscrawler" ]
