# Local Track Logger

Privacy-first Android GPS track recorder. The app stores recordings in private app storage and has **no `INTERNET` permission**. It uses a user-started location foreground service and a persistent notification while recording.

## Current implementation

- Start and stop local GPS recording.
- Foreground `location` service using Fused Location Provider.
- Room database for tracks and recorded points.
- Point filtering for poor accuracy and short redundant samples.
- GPX 1.1 export engine (wire `GpxExporter` to a `CreateDocument` UI action in the next UI increment).
- Basic recorded-track list.
- GitHub Actions build/test/lint workflow.

## Intentional offline-map scope

This generated baseline has no map library or `INTERNET` permission. The next increment should add a mapsforge renderer and local `.map` package selection through Android's Storage Access Framework. This preserves the fully-offline requirement and avoids sending route-view information to map-tile providers.

## Build

Open with Android Studio, let Gradle sync, then run on a physical Android device. GPS tracking requires a device with location services enabled. Start tracking from the visible app UI so Android permits the location foreground service.

## Important device behavior

Android may still interrupt active work under extreme battery conditions or vendor-specific power management. The persistent notification is intentional: it informs you when location recording is active and provides a direct Stop control.
