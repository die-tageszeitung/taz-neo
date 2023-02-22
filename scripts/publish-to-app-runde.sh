#!/bin/bash
set -e
set -o xtrace

# FIXME(johannes): curl v7.76 will allow us to use --fail-with-body which is preferable to --fail
#                  on REST endpoints as we will get the actual error message from the server.
#                  Once the android-docker image resp. the cimg/android updates curl we should use it

# Use the AAPT from the default Android build tools as defined on the android-docker build image
AAPT_BIN="$BUILD_TOOLS_ROOT/aapt"

RECENT_TAG=$(git describe --abbrev=0 --exclude="lmd-*")
RELEASE_MESSAGE=$(git tag -l --format='%(contents)' "$RECENT_TAG")
APP_VERSION=$CI_COMMIT_TAG
APP_VERSION_CODE=$($AAPT_BIN dump badging app/build/outputs/apk/freeTazProduction/manualUpdateRelease/app-free-taz-production-manualUpdateRelease.apk | grep "package:" | sed -e "s/.*versionCode='//" -e "s/' .*//")

FREE_APP_URL=$(curl --fail --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_MANUAL_UPDATE_RELEASE_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u "$NEXTCLOUD_RELEASE_USER:$NEXTCLOUD_RELEASE_PASSWORD" | jq -r .ocs.data.url)
DEBUG_APP_URL=$(curl --fail --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_DEBUG_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u "$NEXTCLOUD_RELEASE_USER:$NEXTCLOUD_RELEASE_PASSWORD" | jq -r .ocs.data.url)
STAGING_DEBUG_APP_URL=$(curl --fail --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_STAGING_DEBUG_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u "$NEXTCLOUD_RELEASE_USER:$NEXTCLOUD_RELEASE_PASSWORD" | jq -r .ocs.data.url)

TAZ_RELEASE_JSON=$(
  jq -n '{
    staging_debug_link: $staging_debug_link,
    debug_link: $debug_link,
    free_link: $free_link,
    version_code: $version_code,
    version_name: $version_name,
    release_note: $release_note
  }' \
    --arg staging_debug_link "$STAGING_DEBUG_APP_URL" \
    --arg debug_link "$DEBUG_APP_URL" \
    --arg free_link "$FREE_APP_URL" \
    --arg version_code "$APP_VERSION_CODE" \
    --arg version_name "$APP_VERSION" \
    --arg release_note "$RELEASE_MESSAGE"
)

curl -v \
  --fail \
  -X POST \
  --header 'Content-Type: application/json' \
  --header "Authorization: Token $TOOLBOX_API_TOKEN" \
  --data "$TAZ_RELEASE_JSON" \
  https://toolbox.alt.coop/misc/api/taz-release
