# ESOFTMEDIA VIDEO EDITOR

**Create. Edit. Anywhere.** 🎬

A phone-first Android video editor designed to work fully offline. Your media and projects stay on your device.

## Product rules
- No account, cloud project, upload, or analytics service.
- No runtime internet permission.
- Non-destructive edits: trim/effects/layers are project instructions; originals remain untouched.
- Local rendering and export to the device.

## Current foundation
- Kotlin + Jetpack Compose
- Cinematic dark editor shell
- Android system media picker
- Media3 preview and visual adjustment foundation
- Pure Kotlin timeline engine with millisecond trim/split/reorder/duplicate/snap operations and undo/redo history
- GitHub Actions debug APK build

## Build
Push to `main` or open **Actions → Android APK Build → Run workflow**. The workflow builds a debug APK and uploads it as an artifact.

## Product roadmap
See [docs/PRODUCT_ROADMAP.md](docs/PRODUCT_ROADMAP.md) for phases, acceptance criteria, and new product ideas. Features are not considered complete until their behavior is integrated, built, and tested.
