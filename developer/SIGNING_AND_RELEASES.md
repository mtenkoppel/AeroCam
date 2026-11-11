# Signing and releases

Maintainer documentation. Riders should use the published APK and follow
[Install on your Karoo](../README.md#install-on-your-karoo). Source setup and
contribution checks are in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Signing identity

Android accepts an in-place update only when it uses the same application ID
and signing key as the installed app, and an acceptable version code. Preserve
the established release key and keep an encrypted backup outside this repository.
A local Android debug key cannot update a published release installation.

Never commit a keystore, its passwords, tokens, or populated local configuration.
The names below are configuration references, not credential values.

The [Publish release workflow](../.github/workflows/release.yml) reads these GitHub Actions
secrets:

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | Signing key password |

The workflow restores the keystore to a temporary file, builds the release APK,
checks the signing certificate against the expected identity, and removes the
temporary keystore afterward.

For a local signed release build, the Gradle configuration reads
`AEROCAM_KEYSTORE_PATH`, `AEROCAM_KEYSTORE_PASSWORD`, `AEROCAM_KEY_ALIAS`, and
`AEROCAM_KEY_PASSWORD` from the environment. With these set securely, run:

```sh
./gradlew testDebugUnitTest assembleRelease
```

The output is `app/build/outputs/apk/release/app-release.apk`. Keep credentials
out of shell history, logs, and build artifacts. Do not change the release key to
resolve an update problem; that would break in-place updates for existing users.

## Build for Karoo

1. Open **Actions → Signed test build → Run workflow** and choose the trusted
   branch to test. Enable **release_signed_debug** if you need a debuggable APK.
   The workflow verifies the APK and uploads it with its checksum.
2. Download and verify the artifact. Install the APK with
   `./scripts/install-release.sh /path/to/app.apk`. Check the saved camera,
   settings and the behavior under test. Installation restarts the app, which
   can then reconnect to the saved camera.

Automatic CI runs on PR updates and master pushes. It runs unit tests and builds
a debug APK. A signed build is requested separately for device testing.

## Version numbers

Use `MAJOR.MINOR.PATCH` for new stable releases. PATCH is for compatible fixes,
MINOR for compatible features, and MAJOR for breaking changes. Keep the
historical `v1.0` tag. The first patch uses `versionName = "1.0.1"` and tag
`v1.0.1`. Android's integer `versionCode` is independent of that label. Build
43 is reserved, so the next normal build starts at 44 or higher.

## Publish an approved release

1. Choose `MAJOR.MINOR.PATCH` and set `versionName` and `versionCode` in
   `app/build.gradle.kts`. Use a higher version code than any previously
   distributed build, including device test and reserved recovery builds.
2. Move the rider-visible entries from Unreleased to the new version heading in
   [CHANGELOG.md](../CHANGELOG.md). Verify that release blockers are fixed in
   the candidate. Complete CI and the relevant checks on Karoo.
3. Merge the approved changes. Check that the intended master commit matches
   the tested changes and has passed CI.
4. With the owner's approval for that commit, open **Actions → Publish release →
   Run workflow**, select **master**, and enter its full approved SHA. The
   workflow tests, builds and verifies the release APK, then creates the matching
   tag and publishes the APK, checksum and changelog notes to GitHub.

The README and landing page use `/releases/latest`. GitHub points that URL to
the current stable release. Published tags and APKs stay immutable. For a fix or
revert, publish a new patch with a higher Android version code.

For the Karoo Extension Library, add a one-line summary under the new stable
version heading in CHANGELOG.md. Focus on what riders can do or what works
better. The comment stays hidden in the rendered GitHub release notes.

```markdown
<!-- karoo-release-notes: See camera battery and storage at a glance. -->
```

The published manifest omits release notes when a new release has no summary.

## Official beta releases

Use `1.1.0-beta.1` (tag `v1.1.0-beta.1`) and a version code above all distributed builds. The same approved-master workflow accepts `MAJOR.MINOR.PATCH-beta.N`, verifies the established certificate and publishes with `--prerelease --latest=false`. Stable versions retain `--latest`. Beta downloads must link to their explicit release page; `/releases/latest` continues to serve the stable release. Each beta gets a new immutable tag and higher version code.
