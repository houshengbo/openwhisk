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

# Set the command for the package binding.
CMD_PACKAGE_PARAMS="$WSK_CLI package bind /$NAMESPACE/$PACKAGE_NAME $PACKAGE_BINDING_NAME -u $AUTH_KEY"
for value in "${!PARAMS_PACKAGE[@]}"; do
    CMD_PACKAGE_PARAMS="$CMD_PACKAGE_PARAMS -p $value ${PARAMS_PACKAGE[$value]}"
done

# Set the command to create the trigger.
CMD_FEED_PARAMS="$WSK_CLI trigger create $TRIGGER_NAME --feed $PACKAGE_BINDING_NAME/$FEED_NAME -u $AUTH_KEY"
for value in "${!PARAMS_FEED[@]}"; do
    CMD_FEED_PARAMS="$CMD_FEED_PARAMS -p $value ${PARAMS_FEED[$value]}"
done

# Create a new package binding to the package parameters
eval "$CMD_PACKAGE_PARAMS"

# Create the trigger
eval $CMD_FEED_PARAMS

# Create the antion
CMD_ACTION="$WSK_CLI action create $ACTION_NAME $ACTION_PATH"
eval $CMD_ACTION

# Create the rule
eval "$WSK_CLI rule create $RULE_NAME $TRIGGER_NAME $ACTION_NAME"

# Enable the rule for the trigger and action
eval "$WSK_CLI rule enable $RULE_NAME"

