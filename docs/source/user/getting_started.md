(getting-started)=
# Getting Started

The fastest way to try FSCrawler is the {ref}`tutorial`: Elastic
[start-local](https://www.elastic.co/docs/deploy-manage/deploy/self-managed/local-development-installation-quickstart)
plus the Docker image.

This page is the local ZIP path.

Coming from 2.9? There is no in-place upgrade. See {ref}`upgrade-from-2.9`.

## Prerequisites

* **Java** {{ java_version }} or later, with `JAVA_HOME` pointing at that JDK. For example on macOS
  with sdkman, in `~/.bash_profile`:

```sh
export JAVA_HOME="~/.sdkman/candidates/java/current"
```

* Elasticsearch 7.17+, 8.x, or 9.x, reachable from this machine. An **API key** is the recommended
  way to authenticate. See {ref}`credentials`.
* The FSCrawler ZIP. See {ref}`local-installation`.

## Create a job

On first run, create the default job configuration:

```sh
$ bin/fscrawler --setup
17:40:33,905 INFO  [f.console] You can edit the settings in [~/.fscrawler/fscrawler/_settings.yaml]. Then, you can run again fscrawler without the --setup option.
```

Edit `~/.fscrawler/fscrawler/_settings.yaml`:

```yaml
name: "fscrawler"
fs:
  url: "/tmp/es"
elasticsearch:
  urls:
    - "https://127.0.0.1:9200"
  api_key: "YOUR_API_KEY"
```

Leave `elasticsearch.index` unset. FSCrawler indexes into `fscrawler_docs` and creates a
`fscrawler` alias.

```{note}
The default Elasticsearch URL is `https://127.0.0.1:9200`. With Elastic start-local, use
`http://127.0.0.1:9200` instead. Unknown keys such as `elasticsearch.nodes` are ignored.
See {ref}`elasticsearch-settings`.
```

Create `/tmp/es` (or `c:\tmp\es` on Windows), add files to index, then start:

```sh
$ bin/fscrawler
17:41:45,395 INFO  [f.p.e.c.f.FsCrawlerImpl] FSCrawler is now connected to Elasticsearch version [9.0.0]
17:41:45,395 INFO  [f.p.e.c.f.FsCrawlerImpl] FSCrawler started in watch mode. It will run unless you stop it with CTRL+C.
17:41:45,395 INFO  [f.p.e.c.f.FsParser] FS crawler started for [fscrawler] for [/tmp/es] every [15m]
```

If the directory does not exist, FSCrawler warns until you create it:

```none
17:41:45,396 INFO  [f.p.e.c.f.FsParser] Run #1: job [fscrawler]: starting...
17:41:45,397 WARN  [f.p.e.c.f.FsParser] Error while crawling /tmp/es: /tmp/es doesn't exists.
```

## Searching for docs

Search on the job alias (`fscrawler` by default), not on a type name:

```none
GET fscrawler/_search
{
  "query": {
    "match": {
      "content": "I am searching for something!"
    }
  }
}
```

In Kibana, you can also use ES|QL:

```sql
FROM fscrawler
| WHERE content : "something"
```

See {ref}`search-examples` for more examples. If Kibana 9.5+ is running, FSCrawler can create a
default dashboard on startup. See {ref}`kibana-settings`.

## Ignoring folders

If you would like to ignore some folders to be scanned, just add a `.fscrawlerignore` file in it.
The folder content and all sub folders will be ignored.

For more information, read {ref}`includes_excludes`.
