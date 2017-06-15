#!/bin/sh
set -e -x

# check existence and permissions for the keystore file
echo "checking \$MY_KEYSTORE:" 
test -f "$MY_KEYSTORE"
[ $(stat -c %a "$MY_KEYSTORE")               = 400 ]
[ $(stat -c %a $(dirname "$MY_KEYSTORE"))    = 700 ]
[ $(stat -c %a $(dirname "$MY_KEYSTORE")/..) = 700 ]

# check the other variables
echo "checking \$KEYSTORE_PASSWORD:" 
[ ${#KEYSTORE_PASSWORD} = 7 ]
echo "checking \$KEYSTORE_ALIAS:" 
[ ${#KEYSTORE_ALIAS} = 15 ]

# keep location of the keystore file for the next step
echo "$MY_KEYSTORE" > keystore-path
