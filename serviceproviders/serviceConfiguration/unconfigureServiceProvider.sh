#!/bin/bash

# Parse a property file to read the parameters necessary to configure this service provider.

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


# Disable the rule for the trigger and action
eval "$WSK_CLI rule disable $RULE_NAME"

# Delete the rule
eval "$WSK_CLI rule delete $RULE_NAME"

# Delete the antion
eval "$WSK_CLI action delete $ACTION_NAME"

# Remove the package binding to the parameters and the trigger associating with the feed.
eval "$WSK_CLI trigger delete $TRIGGER_NAME"
eval "$WSK_CLI package delete $PACKAGE_BINDING_NAME -u $AUTH_KEY"
