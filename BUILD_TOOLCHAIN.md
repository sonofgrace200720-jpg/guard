# Android build toolchain

The app uses `tor-android:0.4.9.11`, which requires compileSdk 37. On the current Android SDK repository used by GitHub-hosted runners, the API 37 platform may be published as `platforms;android-37.0` rather than the integer `platforms;android-37` package identifier.

CI installs `platforms;android-37.0` and `build-tools;37.0.0`. It then creates a local `android-37` symlink to the installed `android-37.0` platform directory so Gradle/AGP can resolve `compileSdk = 37`.

This avoids suppressing AAR metadata validation and keeps the real Guardian Project Tor Android 0.4.9.11 engine.
