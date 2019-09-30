FROM openjdk:8-jdk

ARG langsPkg
RUN set -ex \
    && apt update \
    && apt install -y --no-install-recommends \
        "gettext-base=0.19.*" \
        ${langsPkg} \
    && apt clean \
    && rm -rf /var/lib/apt/lists/*

COPY maven /usr/share/fscrawler
RUN set -ex \
    && ln -sn /usr/share/fscrawler/bin/fscrawler /usr/bin/

WORKDIR /usr/share/fscrawler
