# Contributing to Pixel Notes

Pixel Notes is a fork of [ArchiveTune](https://github.com/rukamori/ArchiveTune) that plays music from
[khinsider](https://downloads.khinsider.com). Bug reports and ideas are welcome in Issues.

## Building

- JDK 21 and the Android SDK (API 37)
- `./gradlew assembleFossMobileArm64Release` builds the APK used for releases
- Point `sdk.dir` in `local.properties` at your Android SDK if Gradle can't find it

## Releases

- Every push to `main` builds an APK with GitHub Actions and publishes it as a release (`0.N`)
- Put `[dev]` in the commit message to publish a dev build (pre-release) instead
- Branches named `check/...` are compiled to check for errors but not published

## Licence

GPL-3.0, the same as ArchiveTune. Keep the licence headers at the top of source files.
