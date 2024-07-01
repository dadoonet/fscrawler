FROM eclipse-temurin:17-jdk

ARG langsPkg

RUN set -ex \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
    gettext-base \
    procps \
    curl \
    zip \
    unzip \
    net-tools \
    ${langsPkg} \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
COPY --from=hengyunabc/arthas:latest /opt/arthas /opt/arthas
COPY maven /usr/share/fscrawler
RUN set -ex \
    && ln -sn /usr/share/fscrawler/bin/fscrawler /usr/bin/
WORKDIR /usr/share/fscrawler
