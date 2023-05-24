#!/bin/bash
set -e
set -o xtrace

# Some distros deploy SNAIL as mailx.
SNAIL="s-nail"

RECENT_TAG=$(git describe --abbrev=0 --exclude="lmd-*")
RELEASE_MESSAGE=$(git tag -l --format='%(contents)' "$RECENT_TAG")
APP_VERSION=$CI_COMMIT_TAG

$SNAIL -t <<EOF
To: app-runde@taz.de
Subject: Neues Android App Release $APP_VERSION

Hallo liebe taz,

ein neues Release von der taz app ist verfügbar.
Folgende Neuerungen sind enthalten:

$RELEASE_MESSAGE

Version: $APP_VERSION

Die Version sollte in den nächsten Stunden im internen Testtrack im Google Play Store bereitstehen.

Beste Grüße vom,
ctrl.alt.coop release bot
EOF
