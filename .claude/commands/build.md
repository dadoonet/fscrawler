Build the FSCrawler project using Maven.

Ask the user which type of build they want:
1. **Fast** (no tests, no Docker) — `mvn clean package -DskipTests -Ddocker.skip`
2. **Fast with Docker** (no tests) — `mvn clean package -DskipTests`
3. **Unit tests only** — `mvn clean test -DskipIntegTests`
4. **Full build** (unit + integration tests) — `mvn clean install -Dtests.parallelism=1`
5. **Distribution only** — `mvn clean package -DskipTests -pl distribution`

If the user specifies a particular Elasticsearch version, add the appropriate profile (e.g. `-Pes-8x`).

After running, report the build outcome and any failures with their module and error message.
