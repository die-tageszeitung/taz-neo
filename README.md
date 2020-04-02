# taz/android-app

This repository contains an android reader app for the German
newspaper [taz](https://taz.de/).
The project regards the app as the primary outlet/user interface instead of
displaying digital byproducts of producing a news*paper*.

## Development

Consider the [contribution guidelines](./CONTRIBUTING.md).

## Building

### Non-included files

Some files that are required to build this project are not included, for the simple reason, that they belong to personalized services.
You will need to add a sentry configuration to the root folder `sentry.properties` with the following content:

```
dsn=[yourdsn]
defaults.url=[yoururl]
defaults.project=[yourproject]
defaults.org=[yourorg]
auth.token=[yourtoken]
```

For the google-enabled flavor you need a `google-services.json` placed in `app/src/googleTaz/google-services.json`

### gitlab-ci

We use gitlab-ci to automatically build the app.

Following ENV variables are used:

#### adb
`ADB_PRIVKEY_BASE64`
`ADB_PUBKEY`

#### nextcloud

Our builds are automatically synced with nextcloud.
The files are stored in a `release` folder for a given user.

Configuration by following ENV variables:
`NEXTCLOUD_RELEASE_URL` - The URL where the nextcloud is running
`NEXTCLOUD_RELEASE_USER` - Username of the user
`NEXTCLOUD_RELEASE_PASSWORD` - Password of the user

#### secret bundle
`SECRET_BUNDLE_BASE64`
The secret bundle contains all secret files needed to build the app.
It consists of a base64 encoded `.tar` file with following structure.
Please ensure to encode the tar without whitespace i.e. `$ base64 -w0 secret-bundle.tar`

`secret-bundle.tar`
|- keystore.properties
|- play-publish.json
|- keystore.jks
|- app/
    |- src/
        |- googleTaz/
            |- google-services.json
        |- main
            |- resources
                |- sentry.properties


`keystore.properties` defines the keystorePath and the keyAlias. I.e.:
```
keystorePath=keystore.jks
keyAlias=keyAlias
```

`play-publish.json` is used by [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
to publish the app to the play store.

`keystore.jks` is the provided keystore. Must match the path provided in `keystore.properties`.

`app/src/googleTaz/google-services.json` is used for notifications etc.

`app/src/main/resources/sentry.properties` is used to define which [sentry](https://sentry.io) server is used.
Find documentation [here](https://docs.sentry.io/clients/java/config/)

#### android keystore

Signing of the apk/bundle is done with the keystore provided in the secret bundle.
Credentials are additionally set as ENV variables:

`ANDROID_KEYSTORE_KEY_PASSWORD`
`ANDROID_KEYSTORE_PASSWORD`
