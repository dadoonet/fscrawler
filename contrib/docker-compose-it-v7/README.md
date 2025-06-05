# Developer Guide

This documentation shows how to easily start a Workplace Search cluster.

## Launch Elastic Stack

Go to the `contrib/docker-compose-it-v7` dir and type:

```sh
docker-compose up
```

Wait for everything to start. It could take several minutes.
Then you can open Kibana at https://localhost:5601 and log in with `elastic` / `changeme` account.

**Note:** the cluster will start with a `trial` license to run integration tests.
Those are using some APIs which are available with a `trial` license. If you want to just run
with a basic (free) license, run the cluster with:

```sh
LICENSE_TYPE=basic docker-compose up
```

## Configure FSCrawler

```sh
bin/fscrawler --setup --config_dir ./config
```

Type `Y` to create the default config file `./config/fscrawler/_settings.yaml` and edit it if needed.

## Launch FSCrawler

```sh
bin/fscrawler --config_dir ./config
```

## Stop Elastic Stack

Go to the `contrib/docker-compose-it` dir and type:

```sh
docker-compose down
```

