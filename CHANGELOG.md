# Changelog

## v1.3.0

- Renamed the client app and dashboard header to OpenXR Companion.
- Refreshed the client dashboard and control UI to match the INMO Smart Companion dark control layout.
- Added live Phone cast from Android to the glasses using MediaProjection streaming.
- Added cast controls for zoom, vertical positioning, and manual rotation fallback.
- Improved phone cast orientation handling for landscape video playback by recreating the capture surface on rotation.
- Added Location cast mode with a dedicated dashboard tile and a glasses-side pseudo-3D map plane renderer.
- Added selectable client themes: Dark, Dracula, Solaris, and Dark Brown Golden.
- Added a Settings checkbox to show or hide SpaceWalker settings on the Home screen.
- Restored SpaceWalker controls, including rotation seekbar, zoom controls, and screen count controls.
- Changed Control Mode to default to cursor mode instead of direct touch mode.
- Removed Air Mouse from Control Mode and kept Touchpad/Remote modes.
- Fixed the Remote grid layout to use a consistent 4x3 layout.
- Removed the Remove Ads card and changed the device connection icon from Bluetooth to Wi-Fi.
- Added screen record and improved quick-settings based command handling on the glasses.
- Added live screenshot/cast command support to the shared protocol and core command handlers.

## v1.2.0

- Added remote cursor mode fixes for AR glasses cursor control.
- Removed Air Mouse and improved cursor-mode scrollbar scrolling.
- Added phone screenshot capture/crop/send support.
- Added file manager transfers between phone and XR device.
- Fixed MediaProjection foreground service handling on the phone.
- Fixed keyboard IME/checkmark actions to send Enter to the XR device.
- Documented one-command ADB setup for Core accessibility.
