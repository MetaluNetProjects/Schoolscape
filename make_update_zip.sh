#!/bin/bash

VERSION=`head -n 1 pd/VERSION`
ZIPFILE=update-v$VERSION.zip

echo Version: $VERSION
rm -f $ZIPFILE
zip -r $ZIPFILE pd/ -x pd/data/\*

