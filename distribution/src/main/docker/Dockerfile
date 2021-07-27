FROM openjdk:15.0.2-jdk-slim-buster

ARG langsPkg

RUN set -ex \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
    "gettext-base=0.19.*" \
    ${langsPkg} \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY maven /usr/share/fscrawler
RUN set -ex \
    && ln -sn /usr/share/fscrawler/bin/fscrawler /usr/bin/

RUN mv /usr/share/fscrawler/config/log4j2.xml /usr/share/fscrawler/config/log4j2-file.xml \
    && mv /usr/share/fscrawler/config/log4j2-console.xml /usr/share/fscrawler/config/log4j2.xml

WORKDIR /usr/share/fscrawler
