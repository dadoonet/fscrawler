(layout)=
# Directory layout

The directory layout of the project is as follows:

```none
 .
 ├── NOTICE
 ├── LICENSE
 ├── README.md
 ├── bin
 │   ├── fscrawler
 │   └── fscrawler.bat
 ├── config
 │   ├── log4j2.xml
 │   └── log4j2-file.xml
 ├── external
 ├── lib
 └── logs
     ├── bulk-failures.log
     ├── documents.log
     └── fscrawler.log
```

The `bin` directory contains the scripts to run FSCrawler.

The `lib` directory contains the FSCrawler jar file and all the dependencies.

```{versionadded} 3.0
```

The `config` directory contains the configuration files. See {ref}`logger`.

The `external` directory is for optional JARs (e.g. for JPEG2000 support in PDFs). See {ref}`local-installation` for
details and how to add libraries such as `jai-imageio-jpeg2000`.

As this directory is empty by default, you can also mount it when using Docker images:

```sh
docker run -it --rm \
     -v ~/.fscrawler:/root/.fscrawler \
     -v ~/tmp:/tmp/es:ro \
     -v "$PWD/external:/usr/share/fscrawler/external" \
     dadoonet/fscrawler
```

See also {ref}`docker`, {ref}`docker-compose` and {ref}`local-installation`.

The `logs` directory contains the log files. See {ref}`logger`.
