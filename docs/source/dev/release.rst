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
    (no Maven Central, Docker Hub, ``git push``, GitHub release, or production email).

``--skip-tests``
    Adds ``-DskipTests`` to Maven build commands (also prefilled in the Extra Maven options
    prompt).

``--dry-run``
    Simulates the workflow without running git or Maven commands.

``--rollback``
    Undoes a local or failed release using the ``.release`` state file (gitignored). Deletes
    the local release branch and tag, checks out the original branch, and removes ``.release``.

Typical local rehearsal::

    $ cp .env.example .env
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
* Prepare release notes from ``docs/source/release/{version}.md`` and GitHub API
* Deploy to `Maven Central <https://central.sonatype.com/>`_ using the ``central-publishing-maven-plugin``
* Prepare the next SNAPSHOT version
* Commit the change
* Merge the release branch into the branch you started from
* Push the changes and the tag to origin
* Create a GitHub release with ``gh release create``
* Optionally announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

You will be guided through all the steps.

Release notes
^^^^^^^^^^^^^

Release notes live in Markdown under ``docs/source/release/`` (for example ``3.0.md``).
They are included in the ReadTheDocs documentation via MyST Parser and reused by the release
script to build ``/tmp/fscrawler-{version}-release-notes.md`` (outside ``target/`` so ``mvn clean``
does not remove them).

The final notes combine:

* A usage header (``scripts/templates/release-header.md``)
* The version-specific Markdown file
* Merged pull requests from ``gh api .../releases/generate-notes``

To update notes after a GitHub release was already published, edit the Markdown file and run
``gh release edit fscrawler-{version} --notes-file /tmp/fscrawler-{version}-release-notes.md``.

Before releasing
^^^^^^^^^^^^^^^^

Verify that the project builds correctly with the release profile::

    $ mvn clean install -Prelease -DskipTests

Prerequisites:

* Copy ``.env.example`` to ``.env`` and configure SMTP credentials and ``GITHUB_REPO``
* ``python3`` and the GitHub CLI (``gh auth login``)
* A clean-ish git working tree on your integration branch
* GPG signing configured for the Maven ``release`` profile
* ``~/.m2/settings.xml`` with a ``central`` server entry (Sonatype Central token) for production deploy
* Docker Hub credentials when pushing images (or pass ``-Ddocker.skip``)

Environment variables (``.env``)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

See ``.env.example`` at the repository root:

+----------------+----------------------------------------------------------+
| Variable       | Purpose                                                  |
+================+==========================================================+
| GITHUB_REPO    | GitHub repository (``dadoonet/fscrawler``)               |
| SMTP_HOST      | SMTP server hostname                                     |
| SMTP_PORT      | SMTP port (465 for SSL)                                  |
| SMTP_USER      | SMTP username                                            |
| SMTP_PASS      | Mailbox password (quote special chars: ``SMTP_PASS='...'``) |
| SMTP_SECURITY  | Optional: ``ssl`` (port 465) or ``starttls`` (port 587)      |
| ANNOUNCE_FROM  | Sender address (must match ``SMTP_USER`` for Ionos)        |
| ANNOUNCE_TO    | Recipient (personal address for local, Elastic list for  |
|                | production)                                              |
+----------------+----------------------------------------------------------+

After deployment, check the publishing status on
`Central Portal <https://central.sonatype.com/publishing/deployments>`_.
The ``central-publishing-maven-plugin`` is configured with ``autoPublish`` enabled, so artifacts
are published automatically once validation succeeds.

Release Drafter
^^^^^^^^^^^^^^^

The repository uses `Release Drafter <https://github.com/release-drafter/release-drafter>`_ to
maintain a **draft** GitHub release on each push to ``master``. Tags follow the ``fscrawler-{version}``
convention. The final published release uses the hybrid notes assembled by ``release.sh``.

Logs are written to ``/tmp/fscrawler-<release-version>.log``. On failure, the script prints the
last lines of the log and suggests ``./release.sh --rollback``.

.. note::

    Only developers with write rights to the Sonatype Central namespace under ``fr.pilato``
    can perform the release.

    Only developers with write rights to the `DockerHub repository <https://hub.docker.com/r/dadoonet/fscrawler/>`_
    can push the Docker images.
