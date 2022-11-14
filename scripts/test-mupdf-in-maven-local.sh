#!/bin/bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_DIR=$(dirname "$SCRIPT_DIR")
MUPDF_DIR="$PROJECT_DIR/thirdparty/mupdf-jni"

if [[ -z "$MAVEN_LOCAL_REPOSITORY" ]]; then
  MAVEN_LOCAL_REPOSITORY="$HOME/.m2/repository"
fi

MUPDF_VERSION=$( sed -n "s/version = '\(.*\)'/\1/p"  "$MUPDF_DIR/build.gradle")

test -e "$MAVEN_LOCAL_REPOSITORY/com/artifex/mupdf/fitz/$MUPDF_VERSION"
exit $?