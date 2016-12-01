#!/bin/bash
#
# use the command line interface to install standard actions deployed
# automatically
#
# To run this command
# ./uninstallObjectStorage.sh  <AUTH> <APIHOST> <NAMESPACE> <WSK_CLI>
# AUTH, APIHOST and NAMESPACE are found in $HOME/.wskprops
# WSK_CLI="$OPENWHISK_HOME/bin/wsk"

set -e
set -x

if [ $# -eq 0 ]
then
echo "Usage: ./uninstallObjectStorage.sh AUTHKEY APIHOST NAMESPACE PATH_TO_WSK_CLI"
fi

AUTH="$1"
APIHOST="$2"
NAMESPACE="$3"
WSK_CLI="$4"