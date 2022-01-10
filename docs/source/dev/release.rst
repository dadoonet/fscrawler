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
* Deploy to https://s01.oss.sonatype.org/
* Prepare the next SNAPSHOT version
* Commit the change
* Release the Sonatype staging repository
* Merge the release branch to the branch we started from
* Push the changes to origin
* Announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

You will be guided through all the steps.

You can add some maven options while executing the release script such as ``-DskipTests`` if you want to skip
the tests while building the release.

.. note::

    Only developers with write rights to the sonatype repository under ``fr.pilato`` space
    can perform the release.

    Only developers with write rights to the `DockerHub repository <https://hub.docker.com/r/dadoonet/fscrawler/>`_
    can push the Docker images.
