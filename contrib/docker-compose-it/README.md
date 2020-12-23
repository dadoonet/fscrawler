# Developper Guide

This documentation shows how to manually test the Workplace Search integration until we can make all that automatic.

## Launch Elastic Stack

Go to the `contrib/docker-compose-it` dir and type:

```sh
docker-compose \
    -f docker-compose-elasticsearch.yml \
    -f docker-compose-enterprise-search.yml \
    up
```

If you want to also start the stack with Kibana, run instead:

```sh
docker-compose \
    -f docker-compose-elasticsearch.yml \
    -f docker-compose-kibana.yml \
    -f docker-compose-enterprise-search.yml \
    up
```

Wait for everything to start. It could take several minutes:

```
enterprisesearch_1  | 2020-07-20 13:55:08.199:INFO:oejs.ServerConnector:main: Started ServerConnector@252570b9{HTTP/1.1}{0.0.0.0:3002}
enterprisesearch_1  | 2020-07-20 13:55:08.199:INFO:oejs.Server:main: Started @342868ms
```

Then you can open http://localhost:3002 and log in with `enterprise_search` / `changeme` account.
Then launch [Workplace Search](http://localhost:3002/ws) and [add a custom source](http://localhost:3002/ws/org/sources#/add/custom).
Name it `fscrawler`.

You will be able to retrieve your Access Token and the Key.

* `0bbc4c1c20ad6088e719154d8ebef41b4302677fe93710f9b06295a139b10d1e`
* `5f15a47dd26b57eaa88fb196`

## Build FSCrawler

```sh
mvn clean install -DskipTests
cd distribution/es7/target/
unzip fscrawler-es7-2.7-SNAPSHOT.zip
```


## Configure FSCrawler

```sh
fscrawler-es7-2.7-SNAPSHOT/bin/fscrawler workplace --config_dir ./config
```

Type `Y` to create the default config file `./config/workplace/_settings.yaml` and edit it as is:

```yml
---
name: "workplace"
fs:
  url: "/tmp/es"
  update_rate: "15m"
elasticsearch:
  nodes:
  - url: "http://127.0.0.1:9200"
  username: "elastic"
  password: "changeme"
workplace_search:
  content_source_key: "0bbc4c1c20ad6088e719154d8ebef41b4302677fe93710f9b06295a139b10d1e"
  access_token: "5f15a47dd26b57eaa88fb196"
```

## Launch FSCrawler

```sh
fscrawler-es7-2.7-SNAPSHOT/bin/fscrawler workplace --config_dir ./config
```


