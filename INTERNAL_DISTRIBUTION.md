## Sentry

For Non-Free release builds we use sentry for efficient error reporting.
Therefore part of our secret bundle is a `sentry.properties` file that is placed in the project root
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

The secret bundle contains all non public secret files needed to build the app.
It is stored on our nextcloud and the containing folder be accessed by using the `BUNDLE_DIR_SHARETOKEN_AND_PASS` defined in the gitlab CI/CD variables
The secret bundle **shall not be modified** but a new version should be created and referenced from `.gitlab-ci.yml` by setting the `BUNDLE_VERSION` correctly.

The bundle has to be named `bundle-$VERSION.tar` with the following structure:

```
`bundle-001.tar`
|- lmdkeystore.properties
|- lmdkeystore.jks
|- lmd-play-publish.json
|- sentry.properties
|- tazkeystore.properties
|- tazkeystore.jks
|- taz-play-publish.json
|- app/
    |- src/
        |- nonfree/taz/
            |- google-services.json
```


* `taz-play-publish.json` and `lmd-play-publish.json` is used by [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)
to publish the app to the play store.

* `tazkeystore.properties` and `lmdkeystore.properties` ` defines the keystorePath and the keyAlias. I.e.:
```
keystorePath=keystore.jks
keyAlias=keyAlias
```

* `tazkeystore.properties` and `lmdkeystore.properties`  is the provided keystore. Must match the path provided in `*keystore.properties`.

* `app/src/googleTaz/google-services.json` is used for notifications etc.

* `sentry.properties` is used during build time to upload debug symbols of new releases.
   It is **not** used for sending events during the apps runtime.
   This is defined by the `dsn` option set in `SentryProvider.kt`.

   Find documentation [here](https://docs.sentry.io/clients/java/config/)

#### Android Keystore

Signing of the apk/bundle is done with the keystore provided in the secret bundle.
Credentials are additionally set as ENV variables:

`ANDROID_KEYSTORE_KEY_PASSWORD`
`ANDROID_KEYSTORE_PASSWORD`
`ANDROID_LMD_KEYSTORE_KEY_PASSWORD`
`ANDROID_LMD_KEYSTORE_PASSWORD`


### Local Release Builds

To be able to build a local release app you have to provide some `signingConfig` via the `tazkeystore.properties` and `lmdkeystore.properties` files. 
For testing you can simply copy the `debugkeystore.properties` over. But be aware that such builds shall not be sent out for testing to anyone.
Rather use the builds from the CI in these cases.
