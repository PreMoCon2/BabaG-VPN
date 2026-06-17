# BabaVPN

Cyberpunk-styled Android VPN app with an embedded Tor backend and a full-device tunnel flow.

## Overview

BabaVPN is an Android app that experiments with routing device traffic through an embedded Tor stack while presenting a bold neon UI inspired by cyberpunk dashboards. The project combines a Jetpack Compose interface, Android's `VpnService`, Guardian Project's Tor Android runtime, and a bundled native tunnel bridge.

This repository is currently best described as an experimental prototype. The core pieces are in place, but it should still be treated as a developer-focused build rather than a production-ready privacy product.

## Features

- Full-device VPN permission flow built on Android `VpnService`
- Embedded Tor runtime using `tor-android`
- Native tunnel bridge that forwards VPN traffic into Tor
- Real-time connection state updates in the UI
- Custom cyberpunk launcher branding and Compose-based interface
- GitHub-release-friendly debug APK output

## Current Status

- Tor bootstrap flow: implemented
- VPN tunnel creation: implemented
- Native tun-to-socks bridge: bundled and wired in
- UI/branding pass: implemented
- Production hardening and formal testing: still needed

## Tech Stack

- Kotlin
- Jetpack Compose
- Android SDK 34
- Min SDK 27
- `info.guardianproject:tor-android`
- `info.guardianproject:jtorctl`
- Bundled `hev-socks5-tunnel` native bridge

## Project Structure

```text
app/src/main/java/com/example/babavpn/
  MainActivity.kt                  # Compose UI and VPN permission flow
  vpn/
    BabaVpnController.kt           # UI state model for tunnel lifecycle
    BabaVpnService.kt              # Foreground VPN service coordinator
    TorRuntimeManager.kt           # Embedded Tor bootstrap and control-port logic
    TorVpnBridgeManager.kt         # Android TUN setup and native bridge handoff
    TProxyService.kt               # JNI wrapper for native tunnel service
```

## How It Works

1. The app asks Android for VPN permission.
2. `BabaVpnService` starts as a foreground service.
3. `TorRuntimeManager` boots the embedded Tor daemon and waits for live listener ports.
4. `TorVpnBridgeManager` creates the device-wide VPN interface.
5. The bundled native bridge reads packets from the VPN TUN interface and forwards them into Tor.
6. The Compose UI updates as the connection moves from permission -> Tor bootstrap -> tunnel connected.

## Building Locally

### Requirements

- Android Studio
- Android SDK with API 34
- Gradle support enabled in Android Studio

### Debug Build

From the project root:

```powershell
.\gradlew.bat assembleDebug
```

Generated APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Installing the APK

1. Build the debug APK.
2. Transfer `app-debug.apk` to an Android device.
3. Allow installs from the source you are using.
4. Open the app and approve the Android VPN permission prompt.

## Notes for Developers

- This app uses a foreground `VpnService`, so behavior can vary slightly across Android versions.
- Tor is TCP-oriented by design, so some UDP-heavy apps may not behave as expected.
- The app excludes its own package from the VPN route to avoid tunneling management traffic back into itself.
- The native tunnel library is bundled in `jniLibs` for reliable local builds from this workspace.

## Roadmap

- Add a polished splash screen
- Add a signed release build pipeline
- Improve connection diagnostics and logging
- Add settings for bridges, DNS behavior, and routing modes
- Improve testing across more Android devices

## Disclaimer

BabaVPN is an experimental project and should not be treated as a guarantee of anonymity, security, or censorship resistance. Review the code, test carefully, and validate the networking behavior before relying on it for sensitive use cases.

This project is not affiliated with the Tor Project or Guardian Project.
