Release the project
-------------------

The release is driven by an interactive script at the root of the repository::

    $ ./release.sh

Run ``./release.sh --help`` for the full list of options.

Script options
^^^^^^^^^^^^^^

``--local``
    Full local rehearsal: release branch, Maven build with the ``release`` profile (javadoc,
    sources, GPG signing), tag, and release notes generation. Nothing is published remotely
    (no Maven Central, Docker Hub, ``git push``, or public announcement email).

``--skip-tests``
    Adds ``-DskipTests`` to Maven build commands (also prefilled in the Extra Maven options
    prompt).

``--dry-run``
    Simulates the workflow without running git or Maven commands.

``--rollback``
    Undoes a local or failed release using the ``.release`` state file (gitignored). Deletes
    the local release branch and tag, checks out the original branch, and removes ``.release``.

Typical local rehearsal::

    $ ./release.sh --local --skip-tests

If the release fails or you want to discard the local rehearsal::

    $ ./release.sh --rollback

The ``.release`` file is written as soon as the release branch is created, so ``--rollback``
works even when the build fails midway.

What the script does (production release)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

* Create a release branch
* Replace the SNAPSHOT version by the final version number
* Commit the change
* Build the final artifacts using the ``release`` profile (javadoc, sources, GPG signing)
* Tag the version
* Prepare the announcement email
* Deploy to `Maven Central <https://central.sonatype.com/>`_ using the ``central-publishing-maven-plugin``
* Prepare the next SNAPSHOT version
* Commit the change
* Merge the release branch into the branch you started from
* Push the changes and the tag to origin
* Optionally announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

You will be guided through all the steps.

Before releasing
^^^^^^^^^^^^^^^^

Verify that the project builds correctly with the release profile::

    $ mvn clean install -Prelease -DskipTests

Prerequisites:

* A clean-ish git working tree on your integration branch
* GPG signing configured for the Maven ``release`` profile
* ``~/.m2/settings.xml`` with a ``central`` server entry (Sonatype Central token) for production deploy
* Docker Hub credentials when pushing images (or pass ``-Ddocker.skip``)

After deployment, check the publishing status on
`Central Portal <https://central.sonatype.com/publishing/deployments>`_.
The ``central-publishing-maven-plugin`` is configured with ``autoPublish`` enabled, so artifacts
are published automatically once validation succeeds.

Logs are written to ``/tmp/fscrawler-<release-version>.log``. On failure, the script prints the
last lines of the log and suggests ``./release.sh --rollback``.

.. note::

    Only developers with write rights to the Sonatype Central namespace under ``fr.pilato``
    can perform the release.

    Only developers with write rights to the `DockerHub repository <https://hub.docker.com/r/dadoonet/fscrawler/>`_
    can push the Docker images.
