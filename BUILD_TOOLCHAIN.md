# Build toolchain

- Java: Temurin 17
- Gradle: 9.4.1 in GitHub Actions
- Android compile SDK: API 35
- Android target SDK: API 35
- Android build tools: 35.0.0

CI installs the exact Android SDK platform and build-tools packages required by the project. CI installs `platforms;android-35`, `build-tools;35.0.0`, and `platform-tools`.

The app's `compileSdk` is intentionally aligned with the target SDK and CI package set to keep the build reproducible on GitHub-hosted runners.
