FROM openjdk:8-jdk

ARG langsPkg
RUN set -ex \
    && apt-get update \
    && apt-get install -y \
        gettext-base \
        ${langsPkg} \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY maven /usr/share/fscrawler
RUN set -ex \
    && ln -sn /usr/share/fscrawler/bin/fscrawler /usr/bin/

WORKDIR /usr/share/fscrawler
