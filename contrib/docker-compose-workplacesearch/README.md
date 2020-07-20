# Developper Guide

This documentation shows how to manually test the Workplace Search integration until we can make all that automatic.

## Launch Workplace Search

Go to the `contrib/docker-compose-workplacesearch` dir and type:

```sh
docker-compose up
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

## Configure FSCrawler

Create a config file for fscrawler.

TODO continue from here
