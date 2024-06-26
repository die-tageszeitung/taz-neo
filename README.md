# taz/android-app

This repository contains an android reader app for the German
newspaper [taz](https://taz.de/).
The project regards the app as the primary outlet/user interface instead of
displaying digital byproducts of producing a news*paper*.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="80">](https://f-droid.org/packages/de.taz.android.app.free/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
alt="Get it on Google Play"
height="80">](https://play.google.com/store/apps/details?id=de.taz.android.app)

## Development

This repository is currently maintained by [ctrl.alt.coop](https://ctrl.alt.coop).
We gratefully accept propositions and welcome discussions.

Consider the [contribution guidelines](./CONTRIBUTING.md).

## Building

Before starting the build process it is necessary to initialize the mupdf-android-viewer submodule:
```
git submodule update --init --recursive
```

Additionally mupdf needs to be published to the local maven repository.
This step has to be repeated everytime the mupdf library is updated to a new version.
```
./scripts/publish-mupdf-to-maven-local.sh
```

With that the build process can be started.

### Non-free variant

The non-free variant includes firebase for push notification support. It also minifies sources and provides sentry mappings for
efficient error reporting. It's distribution target is the Google Play Store and therefore requires additional files for a successful build
that are not included in the repo. (Namely google credentials and sentry auth tokens to upload proguard mappings)
Because of the missing sentry token all "release" and "non-free" builds will fail or won't function properly without those present.

The non-free app is published to the Google Play Store with the [Gradle Play Publisher Plugin](https://github.com/Triple-T/gradle-play-publisher).

For internal development see [INTERNAL_DISTRIBUTION.md] for instructions to setup your development environment.

### Free variant

Anyone can produce builds for the free variant without minification enabled.
The following flavor creates a free release for *Die Tageszeitung* app:

```
./gradlew :app:assembleFreeTazUnminifiedProductionRelease
```

Be sure that your android sdk path is set correctly, for example by placing a `local.properties` file in the project root containing the following property:
```
sdk.dir=/home/me/Android/Sdk
```
Android Studio usually takes care of this if used.

Also be aware that this will produce an unsigned release. For signing during build please place a `tazkeystore.properties` in project root providing information about the signing configuration:
```
keystorePath=keystore.jks
keyAlias=keyAlias
```

## Releasing / Versioning

We have a `release` branch where the tagged releases should happen.

To roll out the release at F-Droid we need to have a `fastlane` folder in our root.
So for LMd releases rename the `fastlane_lmd` to `fastlane`. For taz releases `fastlane_taz` to `fastlane` accordingly.

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

### LMd Variant

The LMd Release release process follows the taz, but it is only triggered for tags prefixed with `lmd-`, such as:

```
git tag -a lmd-1.1.0 # ✓
git tag -a lmd-1.1.1-alpha.1 # ✓
```