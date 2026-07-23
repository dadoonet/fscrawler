# Starting with a REST gateway

```{versionadded} 2.2
```

FSCrawler can be a nice gateway to elasticsearch if you want to upload
binary documents and index them into elasticsearch without writing by
yourself all the code to extract data and communicate with
elasticsearch.

To start FSCrawler with the REST service, use the `--rest` option. A
good idea is also to combine it with `--loop 0` so you won’t index
local files but only listen to incoming REST requests:

```sh
$ bin/fscrawler --loop 0 --rest
18:55:37,851 INFO  [f.p.e.c.f.FsCrawlerImpl] Starting FS Crawler
18:55:39,237 INFO  [f.p.e.c.f.r.RestServer] FSCrawler Rest service started on [http://127.0.0.1:8080]
```

Check the service is working with:

```sh
curl http://127.0.0.1:8080/
```

It will give you back a JSON document.

Then you can start uploading your binary files:

```sh
echo "This is my text" > test.txt
curl -F "file=@test.txt" "http://127.0.0.1:8080/_document"
```

For password-protected files, pass `password` as a multipart form field, request header, or query
parameter. If more than one is present, FSCrawler uses form first, then header, then query.

It will index the file into elasticsearch and will give you back the
elasticsearch URL for the created document, like:

```json
{
  "filename" : "test.txt",
  "ok" : true,
  "url" : "https://127.0.0.1:9200/fscrawler_docs/_doc/dd18bf3a8ea2a3e53e2661c7fb53534"
}
```

The default REST base URL is `http://127.0.0.1:8080`. If you configure a path in `rest.url`,
such as `http://127.0.0.1:8080/fscrawler`, prefix every endpoint with that path.

To enable CORS (Cross-Origin Request Sharing) functionality you will
need to set `enable_cors: true` in your job settings.

Read the {ref}`rest-service` chapter for more information.
