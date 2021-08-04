# Developper Guide

This documentation shows how to start a Workplace Search cluster.

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

## Configure FSCrawler

```sh
bin/fscrawler workplace --config_dir ./config
```

Type `Y` to create the default config file `./config/workplace/_settings.yaml` and edit it as is:

```yml
---
name: "workplace"
fs:
  url: "/tmp/es"
  update_rate: "15m"
elasticsearch:
  username: "elastic"
  password: "changeme"
workplace_search:
  name: "My fancy custom source name"
```

## Launch FSCrawler

```sh
bin/fscrawler workplace --config_dir ./config
```
