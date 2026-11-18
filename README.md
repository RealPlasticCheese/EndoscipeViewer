# PlasticCheese Endoscope Viewer

An offline Android viewer for USB-C OTG endoscopes and other USB Video Class (UVC) cameras. It shows a full-screen live feed, discovers the camera’s resolutions, and saves JPEG photos to Gallery. The app uses Kotlin, Jetpack Compose Material 3, and [UVCAndroid](https://github.com/shiyinghan/UVCAndroid).

## Install and use

1. Download the signed APK from the [project page](https://plasticcheese.com/endoscopeviewer/). Open it on the phone and allow your browser or file manager to install it when Android prompts. A SHA-256 checksum is available next to the APK.
2. Open PlasticCheese Endoscope Viewer and plug in a UVC camera. The splash screen appears while the app waits for the camera. Tap **plasticcheese.com** to open the homepage in your browser. Use a data-capable OTG adapter if the camera does not plug directly into the phone. Approve Android’s Camera and USB access prompts.
3. Tap the picture to hide or show controls. Use **Rotate**, **Mirror**, the zoom slider or pinch, and the resolution picker. Tap **Photo** to save the current view under **Pictures / Endoscope Viewer**. Rotation, mirroring and zoom are included in the JPEG.
4. Unplug and reconnect the camera to resume viewing or switch to another camera.

The APK targets ARM phones running Android 10 or later with USB host support, including compatible Samsung Galaxy models. Camera modes and available USB power vary by model. If a mode fails to stream, choose a smaller resolution or reconnect the camera. The app requests Android Camera permission for UVC access. It has no internet, microphone, or gallery-read permission, ads, or analytics.

## Build locally

Use JDK 17 and an Android SDK with Platform 36 and Build Tools 36.0.0. Recent Android Studio versions can install these using SDK Manager. Set `ANDROID_HOME` to your SDK location or create `local.properties` with its `sdk.dir` path.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` or `.\gradlew.bat` in PowerShell. The debug build uses a separate package ID so it can coexist with the release APK.

## Persistent release signing

Create a key only for the first release. If an APK is already distributed, reuse its original keystore. Android requires future updates to use the same application ID and signing certificate, with an increasing version code.

```sh
keytool -genkeypair -keystore endoscope-release.p12 -storetype PKCS12 \
  -alias release -keyalg RSA -keysize 3072 -validity 10000
```

Keep the keystore secure outside the checkout. Back up the keystore and its password in recoverable storage before distribution. Use the same key and store password for PKCS12.

Set the following secrets in the [GitHub Actions production environment](https://github.com/RealPlasticCheese/EndoscipeViewer/settings/environments). Limit deployments from this environment to the `main` branch.

| Secret | Value |
| --- | --- |
| `APK_KEYSTORE_BASE64` | The entire PKCS12 file as Base64, without line breaks |
| `APK_KEYSTORE_PASSWORD` | Keystore password |
| `APK_KEY_ALIAS` | Key alias, usually `release` |
| `APK_KEY_PASSWORD` | Private-key password |
| `PAGES_DEPLOY_KEY` | Private half of a write-enabled SSH deploy key for `RealPlasticCheese/realplasticcheese.github.io` |

Set the production environment variable `APK_CERT_SHA256` to the release certificate’s SHA-256 fingerprint, as 64 hex digits without colons. Run `keytool -list -v -keystore endoscope-release.p12 -alias release` to read it.

Use the GitHub CLI to send secrets through standard input with `gh secret set NAME --env production --repo RealPlasticCheese/EndoscipeViewer`. On Windows PowerShell, encode the keystore with `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\endoscope-release.p12).Path))` and pipe it directly into that command. Keep plaintext passwords, private keys and Base64 keystores out of commits and logs.

To build a signed release locally, set `APK_KEYSTORE_FILE`, `APK_KEYSTORE_PASSWORD`, `APK_KEY_ALIAS`, and `APK_KEY_PASSWORD` in your build environment and run `./gradlew :app:assembleRelease`. The signed file is `app/build/outputs/apk/release/app-release.apk`. Verify its fingerprint with `apksigner verify --print-certs` from the Android SDK Build Tools. The first local release defaults to version code 1. Set `APK_VERSION_CODE` explicitly for subsequent manual releases.

## Automatic Pages publishing

The **Android build** workflow builds, tests, and lints each pull request. On `main`, it also builds a signed release with an increasing version code, verifies the expected signing certificate and APK alignment, and uploads it as an Actions artifact.

Generate a dedicated ED25519 deploy key for CI and register its public half on the Pages repository. A write-enabled deploy key grants write access to that repository. Store its private half as the `PAGES_DEPLOY_KEY` production secret.

```sh
ssh-keygen -t ed25519 -N '' -f endoscope-pages-deploy
gh repo deploy-key add endoscope-pages-deploy.pub --allow-write \
  --title 'Endoscope Viewer Pages publisher' --repo RealPlasticCheese/realplasticcheese.github.io
gh secret set PAGES_DEPLOY_KEY --env production --repo RealPlasticCheese/EndoscipeViewer < endoscope-pages-deploy
```

The workflow publishes the APK, its checksum, and its version code under `endoscopeviewer/` on the Pages repository’s `main` branch. That repository’s **Refresh Pages for Endoscope Viewer** workflow requests a rebuild from the existing branch-based Pages source. Once both workflows are on their default branches and credentials are configured, each successful push to the app’s `main` updates the [direct download](https://plasticcheese.com/endoscopeviewer/endoscope-viewer.apk). You can also run **Android build** manually on `main`.

## Phone test

Try the following on the target phone before relying on a particular camera.

- Open the app with the camera unplugged. Verify that the splash screen appears and that **plasticcheese.com** opens the homepage in your default browser. Connect with the app already open, and connect again with the app closed.
- Deny USB permission, tap **Retry**, then allow it. Verify the preview appears.
- Change resolutions, rotate, mirror and zoom. Hide the controls and save a photo after bringing them back. Inspect the saved photo in Gallery.
- Unplug and reconnect the camera while streaming. Verify that the splash screen returns while the camera is unplugged. Try a second UVC camera and verify that the picker reflects its modes.
- Lock and unlock the phone, then leave and return to the app. Verify streaming resumes.

## License

PlasticCheese Endoscope Viewer is licensed under the [MIT License](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the native camera libraries, their licenses, and their sources.
