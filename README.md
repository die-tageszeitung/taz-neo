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

