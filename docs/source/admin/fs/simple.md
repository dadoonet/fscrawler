(simple_crawler)=
# The most simple crawler

You can define the most simple crawler job by writing a
`~/.fscrawler/test/_settings.yaml` file as follow:

```yaml
name: "test"
```

This will scan every 15 minutes all documents available in `/tmp/es`
dir and will index them into the `test_docs` index. FSCrawler also creates
a `test` alias so you can search with `GET test/_search`.

It connects to Elasticsearch at `https://127.0.0.1:9200` by default. Use
`elasticsearch.urls` if your cluster is elsewhere, and an API key to authenticate.
With Elastic start-local, use `http://` instead of `https://`. See
{ref}`elasticsearch-settings`.

**Note**: `name` is a mandatory field.
