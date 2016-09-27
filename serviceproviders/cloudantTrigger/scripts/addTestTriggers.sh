#!/bin/bash

# Register test cloudant triggers.

# The service location (can be specified as command line parameter.)
SERVICEURL="http://172.17.0.1:11000" # default
[ $# -eq 1 ] && SERVICEURL="$1" # override in command line arg

# Delete all registry entries.
#curl -X DELETE "$SERVICEURL/cloudanttriggers"; echo

curl -X PUT -H 'Content-Type: application/json' \
        -d '{
          "accounturl": "https://houshengbo.cloudant.com",
          "dbname": "openwhiskcloudanttest",
          "user": "houshengbo",
          "pass": "000000000",
          "includeDoc": "true",
	      "callback": { "action": {"name":"hello"} }
        }' \
        "$SERVICEURL/cloudanttriggers/foo_db1"; echo
