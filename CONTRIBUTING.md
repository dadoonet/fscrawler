# Developing on fscrawler project

If you are thinking of contributing on the project, which is highly appreciated, here are
some tricks which might help.

## Building

The project is built using [Apache Maven](https://maven.apache.org/).
It needs Java >= 1.8.

To build it, just run:

```sh
git clone https://github.com/dadoonet/fscrawler.git
cd fscrawler
mvn clean install
```

You can also skip running tests with:

```sh
mvn clean install -DskipTests
```

## Testing

### Integration tests

Unless a node is already running at this address, integration tests use by default a Docker configuration
which starts a local node running at [127.0.0.1:9200](http://127.0.0.1:9200).
The elasticsearch version used is defined in the `pom.xml` file.

By default, it will run integration tests against elasticsearch 7.x, 6.x and 5.x series.

You can also run test using security feature by using the `security` maven profile:

```sh
mvn install -Psecurity
```

### Running tests against an external cluster

By default, FS Crawler will run tests against a cluster running at `127.0.0.1` on port `9200`
which is started by the maven plugin.

If you started manually an elasticsearch cluster locally, integration tests can use it if you
define the `tests.cluster.url` setting.
This could speed up dramatically running the integration while developing on FSCrawler.

If you want to run the test suite against an external cluster, using other credentials, you can use the following
system parameters:

* `tests.cluster.url`: http://127.0.0.1:9200 (if set, local Docker instance won't be started)
* `tests.cluster.user`: username (defaults to `elastic`)
* `tests.cluster.pass`: password (defaults to `changeme`)

For example, if you have a cluster running on [Elastic Cloud](https://cloud.elastic.co/), you can use:

```sh
mvn clean install -Dtests.cluster.url=https://CLUSTERID.eu-west-1.aws.found.io:9243 -Dtests.cluster.user=elastic -Dtests.cluster.pass=GENERATEDPASSWORD
```

### Running REST tests using another port

By default, FS crawler will run the integration tests using port `8080` for the REST service.
If you want to change it because this port is already used, you can start your tests with `-Dtests.rest.port=8280` which
will start the REST service on port `8280`


### Randomized testing

FS Crawler uses [Randomized testing framework](https://github.com/randomizedtesting/randomizedtesting).
In case of failure, it will print a line like:

```
REPRODUCE WITH:
mvn test -Dtests.seed=AC6992149EB4B547 -Dtests.class=fr.pilato.elasticsearch.crawler.fs.test.unit.tika.TikaDocParserTest -Dtests.method="testExtractFromRtf" -Dtests.locale=ga-IE -Dtests.timezone=Canada/Saskatchewan
```


It also exposes some parameters you can use at build time:

* `tests.output`: display test output. For example:

```sh
mvn install -Dtests.output=always
mvn install -Dtests.output=onError
```

* `tests.locale`: run the tests using a given Locale. For example:

```sh
mvn install -Dtests.locale=random
mvn install -Dtests.locale=fr-FR
```

* `tests.timezone`: run the tests using a given Timezone. For example:

```sh
mvn install -Dtests.timezone=random
mvn install -Dtests.timezone=CEST
mvn install -Dtests.timezone=-0200
```

* `tests.verbose`: adds running tests details while executing tests

```sh
mvn install -Dtests.verbose
```

* `tests.parallelism`: number of JVMs to start to run tests

```sh
mvn install -Dtests.parallelism=auto
mvn install -Dtests.parallelism=max
mvn install -Dtests.parallelism=1
```

* `tests.seed`: specify the seed to use to run the test suite if you need to reproduce a failure
given a specific test context.

```sh
mvn test -Dtests.seed=E776CE45185A6E7A
```

* `tests.leaveTemporary`: leave temporary files on disk

```sh
mvn test -Dtests.leaveTemporary
```


## Releasing

To release a version, just run:

```sh
./release.sh
```

The release script will:

* Create a release branch
* Replace SNAPSHOT version by the final version number
* Commit the change
* Run tests against all supported elasticsearch series
* Build the final artifacts using release profile (signing artifacts and generating all needed files)
* Tag the version
* Prepare the announcement email
* Deploy to https://oss.sonatype.org/
* Prepare the next SNAPSHOT version
* Commit the change
* Release the Sonatype staging repository
* Merge the release branch to the branch we started from
* Push the changes to origin
* Announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

You will be guided through all the steps.

You can add some maven options while executing the release script such as `-DskipTests` if you want to skip
the tests while building the release.
