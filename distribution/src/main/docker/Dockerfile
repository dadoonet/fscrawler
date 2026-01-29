FROM eclipse-temurin:25-jre-alpine

RUN set -ex \
    && apk add --no-cache \
    procps \
    curl

COPY maven /usr/share/fscrawler
ENTRYPOINT [ "/usr/share/fscrawler/bin/fscrawler" ]
