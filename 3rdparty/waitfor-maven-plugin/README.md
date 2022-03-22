# waitfor-maven-plugin

Forked from https://github.com/scravy/waitfor-maven-plugin until two PR are merged:

* scravy/waitfor-maven-plugin#10
* scravy/waitfor-maven-plugin#11

Maven Coordinates:
    
    <groupId>fr.pilato.elasticsearch.crawler</groupId>
    <artifactId>waitfor-maven-plugin</artifactId>
    <version>2.10-SNAPSHOT</version>

## Minimal Configuration Example

      <plugin>
        <groupId>fr.pilato.elasticsearch.crawler</groupId>
        <artifactId>waitfor-maven-plugin</artifactId>
        <version>2.10-SNAPSHOT</version>
        <executions>
          <execution>
            <id>wait-for-environment-to-be-up</id>
            <phase>pre-integration-test</phase>
            <goals>
              <goal>waitfor</goal>
            </goals>
            <configuration>
              <checks>
                <check>
                  <url>http://localhost:8080/health</url>
                </check>
                <check>
                  <url>http://localhost:8080/loaded_components</url>
                  <expectedResponseBody>{"component_1": "loaded", "component_2": "loaded"}</expectedResponseBody>
                </check>
              </checks>
            </configuration>
          </execution>
        </executions>
      </plugin>

## Full Configuration Example

      <plugin>
        <groupId>fr.pilato.elasticsearch.crawler</groupId>
        <artifactId>waitfor-maven-plugin</artifactId>
        <version>2.10-SNAPSHOT</version>
        <executions>
          <execution>
            <id>wait-for-environment-to-be-up</id>
            <phase>pre-integration-test</phase>
            <goals>
              <goal>waitfor</goal>
            </goals>
            <configuration>
              <skip>false</skip><!-- this is the default -->
              <chatty>false</chatty><!-- this is the default -->
              <quiet>false</quiet><!-- this is the default -->
              <insecure>false</insecure><!-- this is the default -->
              <redirect>true</redirect><!-- this is the default -->
              <timeoutSeconds>30</timeoutSeconds><!-- this is the default -->
              <checkEveryMillis>500</checkEveryMillis><!-- this is the default -->
              <checks>
                <check>
                  <url>http://localhost:9090/health</url>
                  <method>GET</method><!-- this is the default -->
                  <statusCode>200</statusCode><!-- this is the default -->
                  <headers>
                    <header>
                      <name>Authorization</name>
                      <value>Bearer SOMETOKEN</value>
                    </header>
                  </headers>
                </check>
                <check>
                  <url>http://localhost:9090/resource</url>
                  <method>POST</method>
                  <statusCode>201</statusCode>
                  <requestBody>
                  {
                    "some": "thing"
                  }
                  </requestBody>
                  <headers>
                    <header>
                      <name>Content-Type</name>
                      <value>application/json</value>
                    </header>
                  </headers>
                </check>
              </checks>
            </configuration>
          </execution>
        </executions>
      </plugin>

## Options

### insecure

The `insecure` flag allows bypassing https certificate checks. This is handy when using self-signed certificates for
example.

### redirect

The `redirect` flag allows following or not the 302 REDIRECT response. Set it to `false` to avoid redirect.
If a redirect is sent by the server, the `statusCode` will be `302`.
