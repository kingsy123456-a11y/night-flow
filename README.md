# Night Flow 2.0

Offline Android traffic driving game built for landscape phones, with original geometry and a procedural soundtrack. Android 8+ and OpenGL ES 3.0. No ads, accounts, network access or purchases.

## New in 2.0

- Smoother fixed-step steering, lateral inertia, body roll, camera damping and animated wheels.
- A live, rotatable 3D garage: four cars, six paints, stock/sport/widebody kits, spoilers, rim finishes and underglow. Engine stages, 6/7/8-speed gearboxes and tyres affect the simulated car; upgrades are saved per model and all are unlocked.
- Automatic shifts, RPM, throttle load, combustion/exhaust synthesis and shift sounds. The audio is generated, not a recording of a real car.
- Corner speedometer/tachometer and metal/rubber gas and brake pedals. Optional automatic throttle.
- Zen, Classic, Hardcore, three AI opponents and time attack with timed checkpoints and a saved personal ghost. Two race distances (2 or 5 km), configurable traffic/AI difficulty. Missing a time-attack gate costs 3 seconds; race collisions impose a 2-second stop. Ghosts are separate per car, performance tune and distance.
- Complete Bosnian, English, German and Croatian UI.
- Smoother body meshes, clear-coat-like reflections, improved glass and wheel materials, wet asphalt, lighting and edge smoothing. This remains an original procedural game, not a photorealistic commercial asset pack.

## Install

Download `Night-Flow-2.apk` from the successful Android APK workflow. Version 0.1 had an ephemeral development signing key: uninstall 0.1 before installing 2.0 (old local scores are removed). Builds use Android development signatures. No permissions are needed beyond optional vibration.

## Controls

Hold the phone in landscape and start to calibrate tilt. Recalibrate from pause if needed. Hold gas to accelerate or brake to slow/stop. With auto throttle disabled, releasing the gas coasts. Touch arrows are available in settings. Drag the car in the garage to rotate it; tuning previews immediately, Save applies it and Cancel restores the prior tune.

## Build and verification

JDK 17, Gradle 8.11.1, Android SDK 35; run `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`. GitHub Actions runs unit tests and an Android 15 emulator smoke test, saves screenshots, checks the APK signature and exports the exact APK and SHA-256. Smoke tests use accelerated setup states for race finish/ghost persistence; unit tests drive complete short races. FPS settings are limits, not measured S24 Ultra guarantees. No physical-device performance measurement has been made.
