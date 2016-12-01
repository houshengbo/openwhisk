#!/bin/bash
#
# use the command line interface to install standard actions deployed
# automatically
#
# To run this command
# ./installObjectStorage.sh  <AUTH> <APIHOST> <NAMESPACE> <WSK_CLI>
# AUTH, APIHOST and NAMESPACE are found in $HOME/.wskprops
# WSK_CLI="$OPENWHISK_HOME/bin/wsk"

set -e
set -x

if [ $# -eq 0 ]
then
echo "Usage: ./installObjectStorage.sh AUTHKEY APIHOST NAMESPACE PATH_TO_WSK_CLI"
fi

AUTH="$1"
APIHOST="$2"
NAMESPACE="$3"
WSK_CLI="$4"

WHISKPROPS_FILE="$OPENWHISK_HOME/whisk.properties"
DB_PROVIDER=`fgrep db.provider= $WHISKPROPS_FILE | cut -d'=' -f2`
DB_HOST=`fgrep db.host= $WHISKPROPS_FILE | cut -d'=' -f2`
DB_PORT=`fgrep db.port= $WHISKPROPS_FILE | cut -d'=' -f2`
DB_PROTOCOL=`fgrep db.protocol= $WHISKPROPS_FILE | cut -d'=' -f2`
DB_USERNAME=`fgrep db.username= $WHISKPROPS_FILE | cut -d'=' -f2`
DB_PASSWORD=`fgrep db.password= $WHISKPROPS_FILE | cut -d'=' -f2`
DB_OBJECTSTORAGE=`fgrep db.whisk.objectstorage= $WHISKPROPS_FILE | cut -d'=' -f2`

OS_SERVICE=`fgrep os.service= $WHISKPROPS_FILE | cut -d'=' -f2`
OS_PORT=`fgrep os.port= $WHISKPROPS_FILE | cut -d'=' -f2`
OS_USERNAME=`fgrep os.username= $WHISKPROPS_FILE | cut -d'=' -f2`
OS_PASSWORD=`fgrep os.password= $WHISKPROPS_FILE | cut -d'=' -f2`
OS_POLICY_ID=`fgrep os.policy.id= $WHISKPROPS_FILE | cut -d'=' -f2`
OS_POLICY=`fgrep os.policy= $WHISKPROPS_FILE | cut -d'=' -f2`

# If the auth key file exists, read the key in the file. Otherwise, take the
# first argument as the key itself.
if [ -f "$AUTH" ]; then
    AUTH=`cat $AUTH`
fi

export WSK_CONFIG_FILE= # override local property file to avoid namespace clashes

echo Installing object storage package.
$WSK_CLI -i --apihost "$APIHOST" package update --auth "$AUTH" --shared yes "$NAMESPACE/objectstorage" \
-a description "This package manages the object storage configuration." \
-p dbProvider $DB_PROVIDER \
-p dbHost $DB_HOST \
-p dbPort $DB_PORT \
-p dbProtocol $DB_PROTOCOL \
-p dbUsername $DB_USERNAME \
-p dbPassword $DB_PASSWORD \
-p dbname $DB_OBJECTSTORAGE \
-p osService $OS_SERVICE \
-p osPort $OS_PORT \
-p osUsername $OS_USERNAME \
-p osPassword $OS_PASSWORD \
-p osPolicyID $OS_POLICY_ID \
-p osPolicy "$OS_POLICY"

echo Installing object storage actions
$WSK_CLI -i --apihost "$APIHOST" action update --auth "$AUTH" --shared yes "$NAMESPACE/objectstorage/createTrigger" "$OPENWHISK_HOME/ansible/roles/objectstorage/files/objectStorageFeed.js" \
-a feed true \
-a description 'Create the feed to for object storage container' \
-a parameters '[ {"name":"containerID", "required":true, "bindTime":true, "description": "The container ID in the object storage."}, {"name":"supportedEventType", "required":false, "bindTime":true, "description": "The field to specify the supported event types."} ]'

