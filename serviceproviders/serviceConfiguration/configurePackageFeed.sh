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

# Install the package
PACKAGE_CMD="$WSK_CLI -i package update --auth $AUTH_KEY --shared yes /$NAMESPACE/$PACKAGE_NAME -a description \"$PACKAGE_DESCRIPTION\" -p package_endpoint \"$PACKAGE_ENDPOINT\" -a parameters $PACKAGE_PARAMETERS"
eval $PACKAGE_CMD

# Install the feed under the package
FEED_CMD="$WSK_CLI -i action update --auth $AUTH_KEY --shared yes /$NAMESPACE/$PACKAGE_NAME/$FEED_NAME \"$FEED_PATH\" -t $FEED_TIMEOUT -a feed true -a description \"$FEED_DESCRIPTION\" -a parameters $FEED_PARAMETERS"
eval $FEED_CMD
