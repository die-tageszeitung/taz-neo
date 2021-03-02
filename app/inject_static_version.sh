#!/bin/sh
VERSION_NAME=$1
VERSION_CODE=$2
BUILD_GRADLE=$3

sed -i "s/\\(versionName\\) getVersionName()/\\1 \"${VERSION_NAME}\"/" ${BUILD_GRADLE}
sed -i "s/\\(versionCode\\) getVersionCode()/\\1 ${VERSION_CODE}/" ${BUILD_GRADLE}
