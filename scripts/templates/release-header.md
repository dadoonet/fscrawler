The FSCrawler team is pleased to announce the **FSCrawler {VERSION}** release!

FSCrawler is a crawler for Elasticsearch that helps to index binary documents such as PDF, MS Office, and more.

## Usage

Download [FSCrawler {VERSION}]({DOWNLOAD_URL}):

```sh
wget {DOWNLOAD_URL}
unzip fscrawler-distribution-{VERSION}.zip
cd fscrawler-distribution-{VERSION}
```

On first run, create the default job configuration:

```sh
bin/fscrawler --setup
```

Create a directory such as `/tmp/es`, add files to index, then start FSCrawler:

```sh
bin/fscrawler
```

Or with Docker:

```sh
docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     dadoonet/fscrawler:{VERSION}
```

On first run with Docker, add `--setup` to create the configuration.

More details in the [documentation](https://fscrawler.readthedocs.io/en/fscrawler-{VERSION}/).
