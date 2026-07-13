Release the project
-------------------

To release the project, run::

    $ release.sh

The release script will:

* Create a release branch
* Replace SNAPSHOT version by the final version number
* Commit the change
* Run tests against all supported elasticsearch series
* Build the final artifacts using release profile (signing artifacts and generating all needed files)
* Tag the version
* Prepare the announcement email
* Deploy to `Maven Central <https://central.sonatype.com/>`_ using the ``central-publishing-maven-plugin``
* Prepare the next SNAPSHOT version
* Commit the change
* Merge the release branch to the branch we started from
* Push the changes to origin
* Announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

You will be guided through all the steps.

Before releasing, verify that the project builds correctly with the release profile::

    mvn clean install -Prelease

You can add some maven options while executing the release script such as ``-DskipTests`` if you want to skip
the tests while building the release.

After deployment, check the publishing status on
`Central Portal <https://central.sonatype.com/publishing/deployments>`_.
The ``central-publishing-maven-plugin`` is configured with ``autoPublish`` enabled, so artifacts are published
automatically once validation succeeds.

.. note::

    Only developers with write rights to the Sonatype Central namespace under ``fr.pilato``
    can perform the release.

    Only developers with write rights to the `DockerHub repository <https://hub.docker.com/r/dadoonet/fscrawler/>`_
    can push the Docker images.
