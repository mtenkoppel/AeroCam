# AeroCam

AeroCam is an independent Karoo extension for controlling a DJI Osmo Action
camera over Bluetooth Low Energy. It provides in-ride camera controls, automatic
reconnection, GPS telemetry, video highlights, and a configurable keep-awake
mode.

> [!IMPORTANT]
> AeroCam is an independent community project. It is not affiliated with,
> endorsed by, or sponsored by DJI or Hammerhead. DJI and Osmo are trademarks
> of their respective owners.

## Install

Download the newest APK from [GitHub Releases](https://github.com/mtenkoppel/AeroCam/releases/latest).
On the Karoo, allow installation from the source you use to transfer the APK,
then open the file and install it. Updates must be signed with the same key as
the installed version; otherwise Android requires the existing app to be
uninstalled first, which also removes AeroCam settings and its saved camera.

This project is currently in beta. Review the release notes before updating.

## Build from source

### Requirements

- Android Studio with JDK 17
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

4. Connect the Karoo over USB-C, approve USB debugging, and install without
   clearing existing app data:

   ```sh
   adb devices
   ./scripts/install-debug.sh
   ```

To open an installed build, run `./scripts/launch-app.sh`. To follow AeroCam's
logs, run `./scripts/logcat.sh` or use Android Studio's Logcat window.

### Signing

Android accepts an update only when it is signed by the same key as the
installed app. Local debug builds normally use `~/.android/debug.keystore`.
Public releases must use one dedicated release key that is backed up securely;
losing it prevents future in-place updates.

The release workflow expects these GitHub Actions secrets:
`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
and `RELEASE_KEY_PASSWORD`. Never commit a keystore or its passwords.

## Releases

Source code is versioned with Git tags such as `v0.9.0-beta.1`. Installable APKs
belong on the corresponding GitHub Release rather than in Git history. Each
release should contain release notes, the APK, and its SHA-256 checksum.

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

## AI-assisted development

AeroCam was developed with substantial assistance from AI coding tools. Its
features have been tested by a human on real Karoo and DJI hardware, but that
testing cannot cover every device state, firmware version, or riding condition.
Review changes and test them before relying on the software.

## Warranty and liability

AeroCam is provided **as is**, without warranty of any kind. The authors and
copyright holders are not liable for damage, data loss, missed recordings,
device behavior, or other claims arising from its use. The complete legal terms
are in the [GNU General Public License v3.0](LICENSE).

## License

Copyright (C) 2026 Maurice ten Koppel.

AeroCam is licensed under the [GNU General Public License v3.0](LICENSE)
(`GPL-3.0-only`). Distributed modified versions must provide corresponding
source code under the same license. GPLv3 permits commercial use and resale;
its purpose here is to ensure that distributed derivatives remain open.
