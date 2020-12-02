# taz/android-app

This repository contains an android reader app for the German
newspaper [taz](https://taz.de/).
The project regards the app as the primary outlet/user interface instead of
displaying digital byproducts of producing a news*paper*.

## Development

This repository is currently maintained by [ctrl.alt.coop](https://ctrl.alt.coop).
We gratefully accept propositions and welcome discussions.

Consider the [contribution guidelines](./CONTRIBUTING.md).

## Building

### Non-free variant

The non-free variant includes firebase for push notification support. It also minifies sources and provides sentry mappings for
efficient error reporting. It's distribution target is the Google Play Store and therefore requires additional files for a successful build
that are not included in the repo. (Namely google credentials and sentry auth tokens to upload proguard mappings)
Because of the missing sentry token all "release" and "non-free" builds will fail or won't function properly without those present.

For internal development see [INTERNAL_DISTRIBUTION.md] for instructions to setup your development environment.

### Free variant

Anyone can produce builds for the free variant without minification enabled.
The following flavor creates a free release for *Die Tageszeitung* app:

```
./gradlew :app:assembleFreeTazUnminifiedRelease
```

Be sure that your android sdk path is set correctly, for example by placing a `local.properties` file in the project root containing the following property:
```
sdk.dir=/home/me/Android/Sdk
```
Android Studio usually takes care of this if used.

Also be aware that this will produce an unsigned release. For signing during build please place a `keystore.properties` in project root providing information about the signing configuration:
```
keystorePath=keystore.jks
keyAlias=keyAlias
```

## Releasing / Versioning

Our gradle build scripts automatically create versions based on the git tag. To reduce an integer we follow a strict pattern for release tags, that are a subset of [semver](https://semver.org/lang/de/):
MAJOR.MINOR.PATCH[-PRE-RELEASE-TYPE].[PRE-RELEASE-VERSION]. Valid prerelease types are 'alpha', 'beta', 'rc'
For example:

```
git tag -a 1.1.0 # ✓
git tag -a 1.1.1-alpha.1 # ✓
git tag -a 1.2.0 # ✓
```

The build script will possibly throw an error or generate garbage versions if the last tag is wrong like those bad examples:

```

git tag -a 1.2 # ✗
git tag -a 1.1.1-alpha-1 # ✗
git tag -a testtag # ✗
```