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

The [Release workflow](../.github/workflows/release.yml) reads these GitHub Actions
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

## Prepare a release

1. Review the changes and update `versionName` and `versionCode` in
   `app/build.gradle.kts`. Use a new version code higher than previous distributed
   builds so existing users can update in place.
2. Run the contribution checks and verify the release-signed APK on hardware,
   including an update over the existing installation that preserves settings
   and the paired camera.
3. Prepare clear release notes covering user-visible changes, compatibility,
   and any known limitations.
4. After the owner approves publication, tag the intended commit using a
   matching version such as `v1.1.0`, and push that tag. Pushing a `v*` tag starts
   the Release workflow and creates a **draft** GitHub release.
5. Review the draft, release notes, APK signing identity, and attached checksum.
   Publish the draft only when the release is approved.

## Published files

The workflow attaches `AeroCam-<tag>.apk` and its `.sha256` checksum. The APK is
what riders install; GitHub's automatically generated source archives are for
developers. Keep APKs on GitHub Releases rather than committing them to Git.

Tags containing a hyphen are marked as prereleases by the workflow. The README
links to `/releases/latest` so riders reach the current stable release without
needing to choose among development builds.
