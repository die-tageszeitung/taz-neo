#!/bin/sh
TAZ_VERSION_NAME=$1
TAZ_VERSION_CODE=$2
LMD_VERSION_NAME=$3
LMD_VERSION_CODE=$4
BUILD_GRADLE=$5

sed -i "s/\\(versionName\\) tazVersionName/\\1 \"${TAZ_VERSION_NAME}\"/" ${BUILD_GRADLE}
sed -i "s/\\(versionCode\\) tazVersionCode/\\1 ${TAZ_VERSION_CODE}/" ${BUILD_GRADLE}
sed -i "s/\\(versionName\\) lmdVersionName/\\1 \"${LMD_VERSION_NAME}\"/" ${BUILD_GRADLE}
sed -i "s/\\(versionCode\\) lmdVersionCode/\\1 ${LMD_VERSION_CODE}/" ${BUILD_GRADLE}
