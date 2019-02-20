#!/bin/sh
set -e -x

# check existence and permissions for the keystore file
echo "checking \$MY_KEYSTORE:"
test -f "$MY_KEYSTORE"

P1=$(dirname "$MY_KEYSTORE")
P2=$(dirname "$P1")
if [ "$(uname)" = "Darwin" ]; then
  [ $(stat -f %p "$MY_KEYSTORE") = 100400 ]
  [ $(stat -f %p "$P1")          = 40700  ]
  [ $(stat -f %p "$P2")          = 40700  ]
else
  [ $(stat -c %a "$MY_KEYSTORE") = 400 ]
  [ $(stat -c %a "$P1")          = 700 ]
  [ $(stat -c %a "$P2")          = 700 ]
fi

# check the other variables
echo "checking \$KEYSTORE_PASSWORD:"
[ ${#KEYSTORE_PASSWORD} = 7 ]
echo "checking \$KEYSTORE_ALIAS:"
[ ${#KEYSTORE_ALIAS} = 15 ]

# keep location of the keystore file for the next step
echo "$MY_KEYSTORE" > keystore-path
