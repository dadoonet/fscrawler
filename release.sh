#!/bin/sh

# save current dir
CUR_DIR=`pwd`

SCRIPT="$0"

# SCRIPT may be an arbitrarily deep series of symlinks. Loop until we have the concrete path.
while [ -h "$SCRIPT" ] ; do
  ls=`ls -ld "$SCRIPT"`
  # Drop everything prior to ->
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    SCRIPT="$link"
  else
    SCRIPT=`dirname "$SCRIPT"`/"$link"
  fi
done

# Read a value and fallback to a default value
readvalue () {
    read -p "$1 [$2]:" value
    if [ -z "$value" ]; then
        value=$2
    fi
    echo ${value}
}

increment_version ()
{
  declare -a part=( ${1//\./ } )
  declare    new
  declare -i carry=1

  for (( CNTR=${#part[@]}-1; CNTR>=0; CNTR-=1 )); do
    len=${#part[CNTR]}
    new=$((part[CNTR]+carry))
    [ ${#new} -gt ${len} ] && carry=1 || carry=0
    [ ${CNTR} -gt 0 ] && part[CNTR]=${new: -len} || part[CNTR]=${new}
  done

  new="${part[*]}"
  echo "${new// /.}"
}

promptyn () {
    while true; do
        read -p "$1 [Y]/N? " yn
        if [ -z "$yn" ]; then
            yn="y"
        fi
        case ${yn:-$2} in
            [Yy]* ) return 0;;
            [Nn]* ) return 1;;
            * ) echo "Please answer yes or no.";;
        esac
    done
}

test_against_version () {
    if [ -z "$1" ]; then
        echo "Building and testing the release..."
        mvn clean install -Prelease >> /tmp/fscrawler-${RELEASE_VERSION}.log
    else
        echo "Building and testing against elasticsearch $1.x..."
        mvn clean verify -Pes-$1.x >> /tmp/fscrawler-${RELEASE_VERSION}.log
    fi

    if [ $? -ne 0 ]
    then
        tail -20 /tmp/fscrawler-${RELEASE_VERSION}.log
        echo "Something went wrong. Full log available at /tmp/fscrawler-$RELEASE_VERSION.log"
        exit 1
    fi
}

# determine fscrawler home
FS_HOME=`dirname "$SCRIPT"`

# make FS_HOME absolute
FS_HOME=`cd "$FS_HOME"; pwd`

DRY_RUN=0

# Enter project dir
cd "$FS_HOME"

CURRENT_BRANCH=`git rev-parse --abbrev-ref HEAD`
CURRENT_VERSION=`mvn help:evaluate -Dexpression=project.version|grep -Ev '(^\[|Download\w+:)'`

echo "Setting project version for branch $CURRENT_BRANCH. Current is $CURRENT_VERSION."
RELEASE_VERSION=$(readvalue "Enter the release version" ${CURRENT_VERSION%-SNAPSHOT})
NEXT_VERSION_P=`increment_version ${RELEASE_VERSION}`

NEXT_VERSION=$(readvalue "Enter the next snapshot version" ${NEXT_VERSION_P}-SNAPSHOT)

RELEASE_BRANCH=release-${RELEASE_VERSION}

echo "STARTING LOGS FOR $RELEASE_VERSION..." > /tmp/fscrawler-${RELEASE_VERSION}.log

# Check if the release already exists
git show-ref --tags | grep -q fscrawler-${RELEASE_VERSION}
if [ $? -eq 0 ]
then
    if promptyn "Tag fscrawler-$RELEASE_VERSION already exists. Do you want to remove it?"
    then
        git tag -d "fscrawler-$RELEASE_VERSION"
    else
        echo "To remove it manually, run:"
        echo "tag -d fscrawler-$RELEASE_VERSION"
        exit 1
    fi
fi

# Create a git branch
echo "Creating release branch $RELEASE_BRANCH..."
git branch | grep -q ${RELEASE_BRANCH}
if [ $? -eq 0 ]
then
    git branch -D ${RELEASE_BRANCH}
fi
git checkout -q -b ${RELEASE_BRANCH}

echo "Changing maven version to $RELEASE_VERSION..."
mvn versions:set -DnewVersion=${RELEASE_VERSION} >> /tmp/fscrawler-${RELEASE_VERSION}.log

# Git commit release
git commit -q -a -m "prepare release fscrawler-$RELEASE_VERSION"

# Testing against different elasticsearch versions
test_against_version 1
test_against_version 2

# The actual build is made against latest version
test_against_version

# Just display the end of the build
tail -7 /tmp/fscrawler-${RELEASE_VERSION}.log

# Tagging
echo "Tag version with fscrawler-$RELEASE_VERSION"
git tag -a fscrawler-${RELEASE_VERSION} -m "Release FsCrawler version $RELEASE_VERSION"
if [ $? -ne 0 ]
then
    tail -20 /tmp/fscrawler-${RELEASE_VERSION}.log
    echo "Something went wrong. Full log available at /tmp/fscrawler-$RELEASE_VERSION.log"
    exit 1
fi

# Preparing announcement
echo "Preparing announcement"
mvn changes:announcement-generate >> /tmp/fscrawler-${RELEASE_VERSION}.log

echo "Check the announcement message"
cat target/announcement/announcement.vm

if promptyn "Is message ok?"
then
    echo "Message will be sent after the release"
else
    exit 1
fi

# Do we really want to publish artifacts?
RELEASE=0
if promptyn "Everything is ready and checked. Do you want to release now?"
then
    RELEASE=1
    # Deploying the version to final repository
    echo "Deploying artifacts to remote repository"
    if [ ${DRY_RUN} -eq 0 ]
    then
        mvn deploy -DskipTests -Prelease >> /tmp/fscrawler-${RELEASE_VERSION}.log
        if [ $? -ne 0 ]
        then
            tail -20 /tmp/fscrawler-${RELEASE_VERSION}.log
            echo "Something went wrong. Full log available at /tmp/fscrawler-$RELEASE_VERSION.log"
            exit 1
        fi
    fi
fi

echo "Changing maven version to $NEXT_VERSION..."
mvn versions:set -DnewVersion=${NEXT_VERSION} >> /tmp/fscrawler-${RELEASE_VERSION}.log
git commit -q -a -m "prepare for next development iteration"

# git checkout branch we started from
git checkout -q ${CURRENT_BRANCH}

if [ ${DRY_RUN} -eq 0 ]
then
    echo "Inspect Sonatype staging repositories"
    open https://oss.sonatype.org/#stagingRepositories

    if promptyn "Is the staging repository ok?"
    then
        echo "releasing the nexus repository"
        mvn nexus-staging:release >> /tmp/fscrawler-${RELEASE_VERSION}.log
    else
        echo "dropping the nexus repository"
        RELEASE=0
        mvn nexus-staging:drop >> /tmp/fscrawler-${RELEASE_VERSION}.log
    fi
fi

# We are releasing, so let's merge into the original branch
if [ ${RELEASE} -eq 1 ]
then
    echo "Merging changes into ${CURRENT_BRANCH}"
    git merge -q ${RELEASE_BRANCH}
    git branch -q -d ${RELEASE_BRANCH}
    echo "Push changes to origin"
    if [ ${DRY_RUN} -eq 0 ]
    then
        git push origin ${CURRENT_BRANCH} fscrawler-${RELEASE_VERSION}
        if promptyn "Do you want to announce the release?"
        then
            # We need to checkout the tag, announce and checkout the branch we started from
            git checkout -q fscrawler-${RELEASE_VERSION}
            mvn changes:announcement-mail >> /tmp/fscrawler-${RELEASE_VERSION}.log
            git checkout -q ${CURRENT_BRANCH}
        else
            echo "Message not sent. You can send it manually using:"
            echo "mvn changes:announcement-mail"
        fi
    fi
else
    if promptyn "Do you want to remove $RELEASE_BRANCH branch and fscrawler-${RELEASE_VERSION} tag?"
    then
        git branch -q -D ${RELEASE_BRANCH}
        git tag -d fscrawler-${RELEASE_VERSION}
    fi
fi

# Go back in current dir
cd "$CUR_DIR"
