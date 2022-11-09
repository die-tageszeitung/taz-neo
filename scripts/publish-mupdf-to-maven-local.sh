#!/bin/bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_DIR=$(dirname "$SCRIPT_DIR")
MUPDF_DIR="$PROJECT_DIR/thirdparty/mupdf-jni"

cd "$MUPDF_DIR"

# Try to get the ANDROID_HOME from the base projects local properties
if [[ -z "$ANDROID_HOME" ]]; then
  export ANDROID_HOME=$( sed -n 's#sdk.dir=\(.*\)#\1#p' "$PROJECT_DIR/local.properties" )
fi

# Reset the build file and apply the mavenLocal patch
git checkout build.gradle
git apply "$SCRIPT_DIR/mupdf-mavenlocal.patch"

# Ensure an (empty) settings.gradle file exists so that gradle won't use the parent projects one
touch "$MUPDF_DIR/settings.gradle"

# Build the library and publish it to mavenLocal
make -C libmupdf generate
./gradlew --no-daemon --console=plain \
          assembleRelease \
          publishReleasePublicationToMavenLocal
