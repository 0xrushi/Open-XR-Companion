# Open XR Companion

Open XR Companion is a pair of Android apps for controlling an Android-based
XR device from a phone over the local Wi-Fi network.

The project is split into two applications:

- **XR Core** runs on the XR glasses or other Android device being controlled.
- **Smart Companion** runs on the Android phone used as the remote control.

Both applications use the `shared` module for device discovery messages and the
JSON command protocol.

## What It Does

After the devices are paired, the phone can:

- Discover XR Core devices on the same local network.
- Connect and reconnect over WebSocket.
- Use a touchpad for taps, double taps, long presses, swipes, and scrolling.
- Send text, backspace, and enter actions to focused fields.
- Trigger Back, Home, and Recents navigation.
- Adjust media volume and screen brightness.
- Mute audio and request sleep or wake actions.
- View battery, brightness, volume, and connection state.
- Request a screenshot from the XR device.
- List installed applications and apply best-effort background restrictions.

## Core And Client

### Core

The `core` module builds the **XR Core** app. Install it on the Android XR
device that will be controlled.

Core:

- Runs a foreground service on the XR device.
- Responds to UDP discovery requests on port `8767`.
- Hosts a WebSocket server on port `8765` by default.
- Shows and approves new phone pairing requests.
- Validates saved pairing tokens before accepting commands.
- Uses an Android accessibility service for gestures, navigation, text input,
  and screenshots.
- Handles device controls such as volume, brightness, and app management.
- Broadcasts device state to authenticated clients.

Core requires Android 12 or newer (`minSdk 31`). Some controls require
additional Android permissions or device-level privileges.

### Client

The `client` module builds the **Smart Companion** app. Install it on the
Android phone used as the controller.

Client:

- Scans the local network for XR Core devices.
- Opens and maintains the WebSocket connection.
- Requests pairing and stores the approved session token.
- Provides dashboards for connection and device state.
- Provides touchpad, keyboard, navigation, volume, and brightness controls.
- Provides an installed-app management screen.

Client requires Android 10 or newer (`minSdk 29`).

### Shared

The `shared` Android library contains code used by both apps:

- Discovery constants and device metadata.
- Serializable command and response models.
- Device state, app list, file list, screenshot, and error messages.
- The common Kotlin Serialization JSON configuration.

## How It Works

1. XR Core starts a foreground service on the XR device.
2. Smart Companion sends discovery broadcasts over the local network.
3. Core replies with its name, IP address, WebSocket port, battery level, and
   protocol version.
4. Client opens a WebSocket connection and sends a pairing request.
5. The user approves the phone on the XR device.
6. Core returns a pairing token, which Client stores for future connections.
7. Client sends JSON commands and Core returns state updates and responses.

Both devices must normally be connected to the same Wi-Fi network. The current
transport uses unencrypted `ws://` connections and is intended for trusted
local networks.

## Required Core Permissions

Open XR Core and grant the permissions needed for the controls you want to use:

- **Accessibility service:** gestures, scrolling, text input, navigation, and
  screenshots.
- **Modify system settings:** screen brightness control.
- **Usage/app access:** installed-app management features.
- **Notifications:** foreground-service status on supported Android versions.
- **Storage/media access:** file listing where supported.

Android and device-vendor restrictions may prevent some system actions. App
background restriction currently records the requested state and attempts to
stop the target process; it is not persistent OS-level process enforcement.

## Build

Requirements:

- JDK 17
- Android SDK 34
- An Android development environment such as Android Studio

Build all modules:

```bash
./gradlew build
```

Build debug APKs:

```bash
./gradlew :core:assembleDebug :client:assembleDebug
```

The debug APKs are generated at:

```text
core/build/outputs/apk/debug/core-debug.apk
client/build/outputs/apk/debug/client-debug.apk
```

Install `core-debug.apk` on the XR device and `client-debug.apk` on the phone.

## Project Structure

```text
.
|-- core/       XR-device server application
|-- client/     Android phone remote-control application
|-- shared/     Discovery and command protocol library
`-- gradle/     Gradle wrapper and version catalog
```

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Kotlin Coroutines and Flow
- Kotlin Serialization
- Hilt dependency injection
- Java-WebSocket on Core
- OkHttp WebSocket on Client
- Android DataStore

## Default Network Ports

| Purpose | Port |
| --- | ---: |
| WebSocket commands and state | `8765` |
| Reserved HTTP port | `8766` |
| UDP discovery | `8767` |

The WebSocket port and XR device name can be changed in the Core settings.
