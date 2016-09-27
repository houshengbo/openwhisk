#!/bin/bash

# Parse a property file to read the parameters necessary to configure the package and feed
# necessary for the service provider.

# Keep track of the directory of this configuration script
SCRIPT_DIR=$(cd $(dirname "$0") && pwd)

# Import common functions
source $SCRIPT_DIR/functions-common

if [[ ! -r $SCRIPT_DIR/serviceProvider.conf ]]; then
    die $LINENO "Missing $SCRIPT_DIR/serviceProvider.conf."
fi

source $SCRIPT_DIR/serviceProvider.conf

# Set the CLI command path with "wsk" as the default value.
WSK_CLI=${WSK_CLI:-"wsk"}

# Delete the feed under the package
FEED_CMD="$WSK_CLI -i action delete --auth $AUTH_KEY /$NAMESPACE/$PACKAGE_NAME/$FEED_NAME"
eval $FEED_CMD

# Delete the package
PACKAGE_CMD="$WSK_CLI -i package delete --auth $AUTH_KEY /$NAMESPACE/$PACKAGE_NAME"
eval $PACKAGE_CMD
