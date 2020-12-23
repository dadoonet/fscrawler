FROM openjdk:14-alpine AS builder
ENV LANG C.UTF-8
ENV MAVEN_OPTS "-Xmx256m -Xms256m"
RUN apk add --update --no-cache openssl wget maven
WORKDIR /usr/src/
RUN wget https://github.com/dadoonet/fscrawler/archive/master.zip && unzip master.zip
WORKDIR /usr/src/fscrawler-master
RUN mvn clean package

FROM openjdk:14-alpine

ENV ES es7
ENV VERSION 2.7-SNAPSHOT
ENV FSCRAWLER_FNAME fscrawler-$ES-$VERSION

RUN apk add --update \
 && addgroup -S fscrawler && adduser -S -G fscrawler fscrawler
WORKDIR /usr/app
COPY --from=builder /usr/src/fscrawler-master/distribution/es7/target/$FSCRAWLER_FNAME.zip .
RUN unzip $FSCRAWLER_FNAME.zip && rm $FSCRAWLER_FNAME.zip \
 && mkdir -p data/idx \
 && mkdir config \
 && chown -R fscrawler:fscrawler .

VOLUME [ "/usr/app/config", "/usr/app/data" ]

ENTRYPOINT $FSCRAWLER_FNAME/bin/fscrawler --config_dir /usr/app/config idx --rest
