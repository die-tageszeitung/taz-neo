#!/bin/bash
set -e
set -o xtrace
RECIPIENT="app-runde@taz.de"



RECENT_TAG=`git describe --abbrev=0`
export APP_URL=`curl --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_RELEASE_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u ${NEXTCLOUD_RELEASE_USER}:${NEXTCLOUD_RELEASE_PASSWORD} | jq -r .ocs.data.url`

export APP_RELEASE_MESSAGE=`git tag -l --format='%(contents)' ${RECENT_TAG}`
export APP_VERSION=${CI_COMMIT_TAG}
export APP_VERSION_CODE=${APP_VERSION_CODE:-`/opt/android/sdk/build-tools/29.0.2/aapt dump badging app/build/outputs/apk/freeTaz/unminifiedRelease/app-free-taz-unminifiedRelease.apk | grep "VersionCode" | sed -e "s/.*versionCode='//" -e "s/' .*//"`}

http -v --ignore-stdin https://toolbox.alt.coop/misc/api/taz-release recipients:="[\"${RECIPIENT}\"]" version_code="${APP_VERSION_CODE}" version_name="${APP_VERSION}" release_note="${APP_RELEASE_MESSAGE}" link=${APP_URL} Authorization:"Token ${TOOLBOX_API_TOKEN}"
