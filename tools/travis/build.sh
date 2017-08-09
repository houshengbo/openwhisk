#!/bin/bash
set -e

# Build script for Travis-CI.

SCRIPTDIR=$(cd $(dirname "$0") && pwd)
ROOTDIR="$SCRIPTDIR/../.."
HOMEDIR="$SCRIPTDIR/../../../"

# clone the openwhisk utilities repo.
cd $HOMEDIR
git clone https://github.com/apache/incubator-openwhisk-utilities.git

# run the scancode util. against project source code starting at its root
incubator-openwhisk-utilities/scancode/scanCode.py $ROOTDIR --config $ROOTDIR/tools/build/scanCode.cfg

cd $ROOTDIR/ansible

ANSIBLE_CMD="ansible-playbook -i environments/local -e docker_image_prefix=testing"
GRADLE_PROJS_SKIP="-x :core:pythonAction:distDocker  -x :core:python2Action:distDocker -x :core:swift3Action:distDocker -x :core:javaAction:distDocker"

$ANSIBLE_CMD setup.yml
$ANSIBLE_CMD prereq.yml
$ANSIBLE_CMD couchdb.yml
$ANSIBLE_CMD initdb.yml
$ANSIBLE_CMD apigateway.yml

cd $ROOTDIR

TERM=dumb ./gradlew distDocker -PdockerImagePrefix=testing $GRADLE_PROJS_SKIP

cd $ROOTDIR/ansible

$ANSIBLE_CMD wipe.yml
$ANSIBLE_CMD openwhisk.yml -e '{"openwhisk_cli":{"installation_mode":"remote","remote":{"name":"OpenWhisk_CLI","dest_name":"OpenWhisk_CLI","location":"https://github.com/apache/incubator-openwhisk-cli/releases/download/latest"}}}'


cd $ROOTDIR
cat whisk.properties
TERM=dumb ./gradlew :tests:testLean $GRADLE_PROJS_SKIP

cd $ROOTDIR/ansible
$ANSIBLE_CMD logs.yml

cd $ROOTDIR
tools/build/checkLogs.py logs
