# Contributing

Bug reports and focused pull requests are welcome.

1. Open an issue describing the behavior and expected result.
2. Keep changes scoped and avoid including Bluetooth captures or credentials.
3. Run `./gradlew testDebugUnitTest assembleDebug` before submitting a pull
   request.
4. Explain which Karoo and camera firmware versions were tested.
5. License contributions under GPL-3.0-only, the same license as AeroCam.

Changes to the proven BLE connection lifecycle should include focused tests and
manual regression results for camera-on boot, delayed camera power-on,
out-of-range recovery, camera reboot, and deliberate camera power-off.

## Build from source

To install the published app, follow [Install on your Karoo](README.md#install-on-your-karoo).
The instructions below are for developers.

The project uses Kotlin 2.1.21, Android Gradle Plugin 8.6.1,
Gradle 8.7, and Java 17, with compile SDK 35 / target SDK 34. Karoo Extensions
remains at 1.1.9.

### Requirements

- JDK 17 and Android SDK command-line tools (Android Studio is optional)
- Android SDK 35
- A GitHub account and token with `read:packages`
- A Karoo in developer mode for USB deployment

The Gradle wrapper is included, so a separate Gradle installation is not
required.

1. Clone the repository:

   ```sh
   git clone https://github.com/mtenkoppel/AeroCam.git
   cd AeroCam
   ```

2. Copy `local.properties.example` to `local.properties` and provide access to
   Hammerhead's GitHub Package:

   ```properties
   sdk.dir=/Users/you/Library/Android/sdk
   gpr.user=your-github-username
   gpr.key=your-github-token
   ```

   `local.properties` is ignored by Git. CI can instead use `GITHUB_ACTOR` and
   `GITHUB_TOKEN`.

3. Build and test:

   ```sh
   ./gradlew testDebugUnitTest assembleDebug
   ```

   The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Running your own build

A local debug APK uses a different signing key from the published release. It
cannot update a release installation. Use a dedicated development device or a
separate app ID rather than uninstalling the rider's app and losing its settings.

For a suitable development device, enable USB debugging, connect it over USB-C,
and approve the debugging prompt. With Android platform-tools installed:

```sh
adb devices
./scripts/install-debug.sh
```

Use `./scripts/launch-app.sh` to open AeroCam and `./scripts/logcat.sh` to follow
logs. Before sharing logs, remove camera identifiers and other private data.

## Signing and publishing

See [Signing and releases](developer/SIGNING_AND_RELEASES.md) for maintainer
instructions. Signing credentials are not needed for an ordinary debug build.

## Dependencies and acknowledgements

- [Hammerhead Karoo Extension SDK](https://github.com/hammerheadnav/karoo-ext)
  (`io.hammerhead:karoo-ext`), Apache License 2.0
- Kotlin and the Android Gradle Plugin, used by the build toolchain
- JUnit 4, used only for tests

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution details.

The following projects were studied while implementing and validating the BLE
behavior. They are references, not bundled dependencies:

- [DJI Osmo GPS Controller Demo](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo)
- [ClipRide GoPro extension for Karoo](https://github.com/yrkan/clipride)
- [DJI Remote](https://github.com/rhoenschrat/DJI-Remote)
- [M5StickC Plus 2 Remote for DJI Osmo](https://github.com/theserialhobbyist/M5StickCPlus2_Remote_For_DJI_Osmo)

