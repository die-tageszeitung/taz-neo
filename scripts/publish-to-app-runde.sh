#!/bin/bash
set -e
set -o xtrace



RECENT_TAG=`git describe --abbrev=0`
export RELEASE_MESSAGE=`git tag -l --format='%(contents)' ${RECENT_TAG}`

export FREE_APP_URL=`curl --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_MANUAL_UPDATE_RELEASE_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u ${NEXTCLOUD_RELEASE_USER}:${NEXTCLOUD_RELEASE_PASSWORD} | jq -r .ocs.data.url`
export FREE_APP_VERSION=${CI_COMMIT_TAG}
export FREE_APP_VERSION_CODE=${APP_VERSION_CODE:-`/opt/android/sdk/build-tools/29.0.2/aapt dump badging app/build/outputs/apk/freeTazProduction/manualUpdateRelease/app-free-taz-production-manualUpdateRelease.apk | grep "VersionCode" | sed -e "s/.*versionCode='//" -e "s/' .*//"`}

export DEBUG_APP_URL=`curl --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_DEBUG_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u ${NEXTCLOUD_RELEASE_USER}:${NEXTCLOUD_RELEASE_PASSWORD} | jq -r .ocs.data.url`
export DEBUG_APP_VERSION=${CI_COMMIT_TAG}
export DEBUG_APP_VERSION_CODE=${APP_VERSION_CODE:-`/opt/android/sdk/build-tools/29.0.2/aapt dump badging app/build/outputs/apk/freeTazProduction/debug/app-free-taz-production-debug.apk | grep "VersionCode" | sed -e "s/.*versionCode='//" -e "s/' .*//"`}

http -v --ignore-stdin https://toolbox.alt.coop/misc/api/taz-release staging_debug_link="${STAGING_DEBUG_APP_URL}" debug_link="${DEBUG_APP_URL}" free_link="${FREE_APP_URL}" version_code="${APP_VERSION_CODE}" version_name="${APP_VERSION}" release_note="${RELEASE_MESSAGE}" Authorization:"Token ${TOOLBOX_API_TOKEN}"
