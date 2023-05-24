#!/bin/bash

if [ -z "$MAILER_USER" ]; then
  echo '$MAILER_USER must be defined' >&2
  exit 1
fi

if [ -z "$MAILER_PASSWORD" ]; then
  echo '$MAILER_PASSWORD must be defined' >&2
  exit 1
fi

# Create the s-nail .mailrc config for mailbox org
TMP_MAILRC=`mktemp`
cat <<EOF >$TMP_MAILRC
set v15-compat
account mailbox {
  set mta=smtps://smtp.mailbox.org:465
  set user="$MAILER_USER"
  set from="$MAILER_USER"
  set password="$MAILER_PASSWORD"
}
account mailbox
EOF
mv -f "$TMP_MAILRC" "$HOME/.mailrc"
