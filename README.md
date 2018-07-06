# File System Crawler for Elasticsearch

Welcome to the FS Crawler for [Elasticsearch](https://elastic.co/)

This crawler helps to index binary documents such as PDF, Open Office, MS Office.

**Main features**:

* Local file system (or a mounted drive) crawling and index new files, update existing ones and removes old ones.
* Remote file system over SSH crawling.
* REST interface to let you "upload" your binary documents to elasticsearch.

You need to install a version matching your Elasticsearch version:

|    Elasticsearch   |  FS Crawler | Released |                                       Docs                                   |
|--------------------|-------------|----------|------------------------------------------------------------------------------|
| 2.x, 5.x, 6.x      | 2.5-SNAPSHOT|          |[2.5-SNAPSHOT](https://fscrawler.readthedocs.io/en/latest/)                   |
| 2.x, 5.x, 6.x      | **2.4**     |2017-08-11|[2.4](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.4/README.md)     |
| 2.x, 5.x, 6.x      | 2.3         |2017-07-10|[2.3](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.3/README.md)     |
| 1.x, 2.x, 5.x      | 2.2         |2017-02-03|[2.2](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.2/README.md)     |
| 1.x, 2.x, 5.x      | 2.1         |2016-07-26|[2.1](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.1/README.md)     |
|    es-2.0          | 2.0.0       |2015-10-30|[2.0.0](https://github.com/dadoonet/fscrawler/blob/fscrawler-2.0.0/README.md) |

## Build and Quality Status

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/fr.pilato.elasticsearch.crawler/fscrawler/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/fr.pilato.elasticsearch.crawler/fscrawler/)
[![Travis](https://secure.travis-ci.org/dadoonet/fscrawler.png)](http://travis-ci.org/dadoonet/fscrawler)
[![Documentation Status](https://readthedocs.org/projects/fscrawler/badge/?version=latest)](https://fscrawler.readthedocs.io/en/latest/?badge=latest)

[![Lines](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=ncloc)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Duplicated Lines](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=duplicated_lines_density)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Maintainability](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=sqale_rating)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=sqale_index)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Reliability](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=reliability_rating)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)

[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=vulnerabilities)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler&resolved=false&types=VULNERABILITY)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=bugs)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=alert_status)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=code_smells)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=fr.pilato.elasticsearch.crawler:fscrawler&metric=coverage)](https://sonarcloud.io/project/issues?id=fr.pilato.elasticsearch.crawler%3Afscrawler)

The guide has been moved to [ReadTheDocs](https://fscrawler.readthedocs.io/en/latest/).

# License

Read more about the [License](https://fscrawler.readthedocs.io/en/latest/index.html#license).
