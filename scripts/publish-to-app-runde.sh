#!/bin/bash
set -e
set -o xtrace

# Some distros deploy SNAIL as mailx.
SNAIL="s-nail"

# FIXME(johannes): curl v7.76 will allow us to use --fail-with-body which is preferable to --fail
#                  on REST endpoints as we will get the actual error message from the server.
#                  Once the android-docker image resp. the cimg/android updates curl we should use it

# Use the AAPT from the default Android build tools as defined on the android-docker build image
AAPT_BIN="$BUILD_TOOLS_ROOT/aapt"


APP_VERSION=$CI_COMMIT_TAG
APP_VERSION_CODE=$($AAPT_BIN dump badging app/build/outputs/apk/nonfreeTazProduction/manualUpdateRelease/app-nonfree-taz-production-manualUpdateRelease.apk | grep "package:" | sed -e "s/.*versionCode='//" -e "s/' .*//")

# Get the share links to the APKs on cloud.alt.coop
MANUAL_UPDATE_APP_URL=$(curl --fail --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${NONFREE_MANUAL_UPDATE_RELEASE_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u "$NEXTCLOUD_RELEASE_USER:$NEXTCLOUD_RELEASE_PASSWORD" | jq -r .ocs.data.url)
DEBUG_APP_URL=$(curl --fail --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_DEBUG_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u "$NEXTCLOUD_RELEASE_USER:$NEXTCLOUD_RELEASE_PASSWORD" | jq -r .ocs.data.url)
STAGING_DEBUG_APP_URL=$(curl --fail --header 'Accept: application/json' --header 'OCS-APIRequest: true' --header 'Content-Type: application/json' --data "{\"path\":\"${FREE_STAGING_DEBUG_APK_PATH}\",\"shareType\":\"3\"}" https://cloud.alt.coop/ocs/v2.php/apps/files_sharing/api/v1/shares -u "$NEXTCLOUD_RELEASE_USER:$NEXTCLOUD_RELEASE_PASSWORD" | jq -r .ocs.data.url)

# Send APK links to app-entwickler@taz.de
$SNAIL -t <<EOF
To: app-entwickler@taz.de
Subject: Neues Android App Release $APP_VERSION - die APKs

Hallo liebe taz-Entwickler*innen,

hier haben wir für das neue Release die entsprechenden APKs bereitgestellt:

Version: $APP_VERSION
Versions-Code: $APP_VERSION_CODE

Variante für dl.taz.de (In-App Updates):
$MANUAL_UPDATE_APP_URL
========================================
Debug Variante:
$DEBUG_APP_URL
========================================
Debug Variante für Test Server (Staging):
$STAGING_DEBUG_APP_URL
========================================

Beste Grüße vom,
ctrl.alt.coop release bot
EOF

