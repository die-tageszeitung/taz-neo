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
