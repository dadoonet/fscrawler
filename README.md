# File System Crawler for Elasticsearch

Welcome to the FS Crawler for [Elasticsearch](https://elastic.co/)

This crawler helps to index binary documents such as PDF, Open Office, MS Office.

![FSCrawler Explained - Generated with Gemini](fscrawler-explained.png)

**Main features**:

* Local file system (or a mounted drive) crawling and index new files, update existing ones and removes old ones.
* Remote file system over SSH/FTP crawling.
* REST interface to let you "upload" your binary documents to elasticsearch.

## Latest versions

Current "most stable" versions are:

| Elasticsearch | FS Crawler    | Released   | Docs                                                                          |
|---------------|---------------|------------|-------------------------------------------------------------------------------|
| 7.x, 8.x, 9.x | 2.10-SNAPSHOT |            | [2.10-SNAPSHOT](https://fscrawler.readthedocs.io/en/latest/)                  |

[![Maven Central](https://img.shields.io/maven-central/v/fr.pilato.elasticsearch.crawler/fscrawler-distribution)](https://repo1.maven.org/maven2/fr/pilato/elasticsearch/crawler/fscrawler-distribution/)
![GitHub Release Date](https://img.shields.io/github/release-date/dadoonet/fscrawler)
[![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fs01.oss.sonatype.org%2Fcontent%2Frepositories%2Fsnapshots%2Ffr%2Fpilato%2Felasticsearch%2Fcrawler%2Ffscrawler-distribution%2Fmaven-metadata.xml&label=Latest%20SNAPSHOT&link=https%3A%2F%2Fs01.oss.sonatype.org%2Fcontent%2Frepositories%2Fsnapshots%2Ffr%2Fpilato%2Felasticsearch%2Fcrawler%2Ffscrawler-distribution%2F)](https://s01.oss.sonatype.org/content/repositories/snapshots/fr/pilato/elasticsearch/crawler/fscrawler-distribution/)
![GitHub last commit](https://img.shields.io/github/last-commit/dadoonet/fscrawler)

![Docker Pulls](https://img.shields.io/docker/pulls/dadoonet/fscrawler)
![Docker Image Size (tag)](https://img.shields.io/docker/image-size/dadoonet/fscrawler/2.10-SNAPSHOT?label=Docker%20image%20size)
![Docker Image Version (latest semver)](https://img.shields.io/docker/v/dadoonet/fscrawler)

## Build and Quality Status

[![Build](https://github.com/dadoonet/fscrawler/actions/workflows/maven.yml/badge.svg)](https://github.com/dadoonet/fscrawler/actions/workflows/maven.yml)
[![Documentation Status](https://readthedocs.org/projects/fscrawler/badge/?version=latest)](https://fscrawler.readthedocs.io/en/latest/?badge=latest)

[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)

[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=bugs)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=dadoonet_fscrawler&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)

## GitHub stats

![GitHub commits since latest release (by SemVer including pre-releases)](https://img.shields.io/github/commits-since/dadoonet/fscrawler/latest/master)
![GitHub commit activity (branch)](https://img.shields.io/github/commit-activity/t/dadoonet/fscrawler)
![GitHub contributors](https://img.shields.io/github/contributors/dadoonet/fscrawler)

![GitHub issues](https://img.shields.io/github/issues/dadoonet/fscrawler)
![GitHub pull requests](https://img.shields.io/github/issues-pr/dadoonet/fscrawler)

## Documentation

The guide has been moved to [ReadTheDocs](https://fscrawler.readthedocs.io/en/latest/).

![X (formerly Twitter) Follow](https://img.shields.io/twitter/follow/dadoonet)

## Contribute

Works on my machine - and yours ! Spin up pre-configured, standardized dev environments of this repository, by clicking on the button below.

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#/https://github.com/dadoonet/fscrawler)

# License

![GitHub](https://img.shields.io/github/license/dadoonet/fscrawler)

Read more about the [Apache2 License](https://fscrawler.readthedocs.io/en/latest/index.html#license).

# Thanks

Thanks to [JetBrains](https://www.jetbrains.com/?from=FSCrawler) for the IntelliJ IDEA License!

Thanks to SonarCloud for the free analysis!

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/summary/new_code?id=dadoonet_fscrawler)
