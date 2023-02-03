# File System Crawler for Elasticsearch

Welcome to the FS Crawler for [Elasticsearch](https://elastic.co/)

This crawler helps to index binary documents such as PDF, Open Office, MS Office.

**Main features**:

* Local file system (or a mounted drive) crawling and index new files, update existing ones and removes old ones.
* Remote file system over SSH/FTP crawling.
* REST interface to let you "upload" your binary documents to elasticsearch.

You need to install a version matching your Elasticsearch version:

|    Elasticsearch   | FS Crawler    | Released   | Docs                                                                          |
|--------------------|---------------|------------|-------------------------------------------------------------------------------|
| 6.x, 7.x           | 2.10-SNAPSHOT |            | [2.10-SNAPSHOT](https://fscrawler.readthedocs.io/en/latest/)                  |
| 6.x, 7.x           | **2.9**       | 2022-01-10 | [2.9](https://fscrawler.readthedocs.io/en/fscrawler-2.9/)                     |
| 6.x, 7.x           | 2.8           | 2021-12-13 | [2.8](https://fscrawler.readthedocs.io/en/fscrawler-2.8/)                     |
| 6.x, 7.x           | 2.7           | 2021-08-05 | [2.7](https://fscrawler.readthedocs.io/en/fscrawler-2.7/)                     |
| 2.x, 5.x, 6.x      | 2.6           | 2019-01-09 | [2.6](https://fscrawler.readthedocs.io/en/fscrawler-2.6)                      |
| 2.x, 5.x, 6.x      | 2.5           | 2018-08-04 | [2.5](https://fscrawler.readthedocs.io/en/fscrawler-2.5)                      |
| 2.x, 5.x, 6.x      | 2.4           | 2017-08-11 | [2.4](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.4/README.md)     |
| 2.x, 5.x, 6.x      | 2.3           | 2017-07-10 | [2.3](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.3/README.md)     |
| 1.x, 2.x, 5.x      | 2.2           | 2017-02-03 | [2.2](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.2/README.md)     |
| 1.x, 2.x, 5.x      | 2.1           | 2016-07-26 | [2.1](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.1/README.md)     |
|    es-2.0          | 2.0.0         | 2015-10-30 | [2.0.0](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.0.0/README.md) |

## Build and Quality Status

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/fr.pilato.elasticsearch.crawler/fscrawler-distribution/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/fr.pilato.elasticsearch.crawler/fscrawler-distribution/)
[![Build](https://github.com/dadoonet/fscrawler/actions/workflows/maven.yml/badge.svg)](https://github.com/dadoonet/fscrawler/actions/workflows/maven.yml)
[![Documentation Status](https://readthedocs.org/projects/fscrawler/badge/?version=latest)](https://fscrawler.readthedocs.io/en/latest/?badge=latest)
[![Code Quality: Java](https://img.shields.io/lgtm/grade/java/g/dadoonet/fscrawler.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/dadoonet/fscrawler/context:java)
[![Total Alerts](https://img.shields.io/lgtm/alerts/g/dadoonet/fscrawler.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/dadoonet/fscrawler/alerts)

[![Lines](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=ncloc)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Duplicated Lines](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=duplicated_lines_density)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=sqale_rating)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=sqale_index)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=reliability_rating)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)

[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=vulnerabilities)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent&resolved=false&types=VULNERABILITY)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=bugs)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=alert_status)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=code_smells)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler-parent&metric=coverage)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler-parent)

The guide has been moved to [ReadTheDocs](https://fscrawler.readthedocs.io/en/latest/).

## Contribute

Works on my machine - and yours ! Spin up pre-configured, standardized dev environments of this repository, by clicking on the button below.

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#/https://github.com/dadoonet/fscrawler)

# License

Read more about the [License](https://fscrawler.readthedocs.io/en/latest/index.html#license).

# Thanks

Thanks to [JetBrains](https://www.jetbrains.com/?from=FSCrawler) for the IntelliJ IDEA License!
