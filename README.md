# SceneCam

Pick what you're shooting — beach, sunset, night city, light trails, mountains, portrait, cinematic video… — and SceneCam sets the camera up like a pro would, then you just press the shutter.

## What each scene controls
- **Exposure style**: auto + brightness shift (EV), *shutter priority* (fixed shutter, ISO worked out from the scene's light), or full manual (fixed ISO + long shutter for light trails).
- **White balance**: daylight / warm / auto.
- **Lens & zoom**: 0.5x ultra-wide for landscapes, 2x for portraits and food, 3x for layered peaks.
- **Focus**: continuous, or locked at infinity for landscapes and night trails.
- **Video**: 25 / 30 / 60 fps, 4K or 1080p, stabilisation, and the 180° cinema shutter rule (1/50 s at 25 fps) with exposure locked.
- **Anti-flicker**: 50 Hz (India's mains frequency) so tube lights and LEDs don't flicker.
- **Self-timer** for tripod scenes, rule-of-thirds grid, tap to focus + meter.

Every scene has an ⓘ button explaining *why* those settings are used and how to shoot it.

## Notes
- Android doesn't let any app change the built-in camera app's settings, so SceneCam is its own camera (Camera2 API).
- Manual shutter/ISO depends on the phone: the home screen shows what yours allows. On phones that block manual control for other apps, SceneCam still applies brightness, colour, focus, zoom, frame-rate and stabilisation per scene.
- Photos: `Pictures/SceneCam` · Videos: `Movies/SceneCam`.

## Build
GitHub Actions builds the APK on every push to `main` and attaches it to the **latest** release. Android 10+.
