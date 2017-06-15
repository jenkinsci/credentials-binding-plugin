#!/bin/sh
set -e -x

# get path of the keystore file
keystore_path=$(cat keystore-path)

# check it was where we would expect it to be
echo "$keystore_path" | grep -q '/workspace/p@tmp/secretFiles/[-0-9a-f]\{36\}/keystore-MY_KEYSTORE$'

# check it has been deleted
if [ -e "$keystore_path" ] ; then
  echo "$keystore_path still exists!!!" >&2
  exit 1
fi
