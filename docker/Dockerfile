FROM java:8
ADD fscrawler-*-jar-with-dependencies.jar opt/fscrawler/app.jar
ADD jobfiles/search.json /opt/fscrawler/jobfiles/search.json
RUN bash -c 'touch /opt/fscrawler/app.jar'
ENTRYPOINT ["java", "-jar","/opt/fscrawler/app.jar", "--trace", "--config_dir", "/opt/fscrawler/jobfiles", "search"]
