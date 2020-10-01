## Sentry

For Non-Free release builds we use sentry for efficient error reporting.
Therefore part of our secret bundle is a `sentry.properties` file that is placed in the project root
containing the following properties.

```
dsn=[yourdsn]
defaults.url=[yoururl]
defaults.project=[yourproject]
defaults.org=[yourorg]
auth.token=[yourtoken]
```

For the google-enabled flavor you need a `google-services.json` placed in `app/src/googleTaz/google-services.json`

## gitlab-ci ENV variables

We use gitlab-ci to automatically build the app.
Following ENV variables are used:

#### Nextcloud

Our CI builds are automatically published on a nextcloud.
The files are stored in a `release` folder for a given user.

Configuration by following ENV variables:

`NEXTCLOUD_RELEASE_URL` - The URL where the nextcloud is running
`NEXTCLOUD_RELEASE_USER` - Username of the user
`NEXTCLOUD_RELEASE_PASSWORD` - Password of the user

#### Secret Bundle

`SECRET_BUNDLE_BASE64`

The secret bundle contains all non public secret files needed to build the app.
It consists of a base64 encoded `.tar` file with following structure.
Please ensure to encode the tar without whitespace i.e. `$ base64 -w0 secret-bundle.tar`

```
`secret-bundle.tar`
|- keystore.properties
|- play-publish.json
|- keystore.jks
|- sentry.properties
|- app/
    |- src/
        |- nonfreeTaz/
            |- google-services.json
```

* `keystore.properties` defines the keystorePath and the keyAlias. I.e.:
```
keystorePath=keystore.jks
keyAlias=keyAlias
```

* `play-publish.json` is used by [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
to publish the app to the play store.

* `keystore.jks` is the provided keystore. Must match the path provided in `keystore.properties`.

* `app/src/googleTaz/google-services.json` is used for notifications etc.

* `sentry.properties` is used to define which [sentry](https://sentry.io) server is used. It's also present in version control but without the auth.token property that is needed to upload proguard mappings for the release builds. For unminified builds this is not necessary
Find documentation [here](https://docs.sentry.io/clients/java/config/)

#### Android Keystore

Signing of the apk/bundle is done with the keystore provided in the secret bundle.
Credentials are additionally set as ENV variables:

`ANDROID_KEYSTORE_KEY_PASSWORD`
`ANDROID_KEYSTORE_PASSWORD`
