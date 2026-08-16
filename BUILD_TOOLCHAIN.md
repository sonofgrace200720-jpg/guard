# Android Build Toolchain

This project uses the Guardian Project Tor Android dependency `info.guardianproject:tor-android:0.4.9.11`, which declares a minimum compile SDK of Android API 37.

The project therefore uses:

- Android Gradle Plugin 9.2.1
- Gradle 9.4.1
- Compile SDK 37
- Target SDK 35
- Minimum SDK 26
- JDK 17
- Built-in Kotlin support from AGP 9.x
- Kotlin Compose compiler plugin 2.3.21

AGP 9.2.x supports API 37 and requires Gradle 9.4.1. The GitHub Actions workflows install Android API 37 and use Gradle 9.4.1.

`targetSdk` remains 35 deliberately: the Tor dependency requires a newer **compile SDK**, but that requirement does not require opting the application into API 37 runtime behavior.
