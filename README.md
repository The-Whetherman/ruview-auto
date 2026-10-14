# RuView Auto — PS2 Emulator on your car's Android Auto screen

An experimental Android Auto app that hijacks the navigation-template's
drawing surface to render a live PS2 emulator ([Play!](https://github.com/jpd002/Play-))
instead of a map. Built in one very long late-night session with
[Hermes](https://github.com/NousResearch/hermes) as a "wait, can we actually
do this?" project. Parked-use only.

## Status

Proof of concept, working end-to-end in Google's Desktop Head Unit (DHU)
simulator:

- A sideloaded `androidx.car.app` navigation-template service renders
  directly onto the car-screen `Surface` Android Auto provides.
- Play!'s native PS2 core (`NativeInterop.setupGsHandler(Surface)`) attaches
  straight to that same `Surface` via `ANativeWindow_fromSurface` — no video
  re-encoding, no virtual display, no screen capture.
- Boots free/legal PS2 homebrew (not commercial game ROMs) from
  [glampert/ps2-homebrew](https://github.com/glampert/ps2-homebrew) (MIT).

Not yet on a real car's app list — confirmed live (Oct 13-14 2026 session) it's
hitting the exact Play Store ownership gate described below:
`Finsky: PlayGearheadService com.whetherman.ruviewauto, app owners empty` →
`CAR.VALIDATOR: Package DENIED; failed all other checks`. A signed release
build is ready (see "Getting a real Play ownership record" below); next step
is a Google Play Developer account + internal testing track.

## How it works

Android Auto's `NavigationTemplate` gives apps a raw `android.view.Surface`
via `SurfaceCallback.onSurfaceAvailable()`, meant for drawing a map. Nothing
stops you from drawing something else. Play! already has a clean JNI
entry point (`NativeInterop.setupGsHandler(Surface)`) that attaches its
OpenGL/Vulkan renderer to *any* `Surface` — originally meant for its own
`GLSurfaceView`, but it doesn't care where the `Surface` comes from.

So: `RuViewCarAppService` → `PS2Screen` → `onSurfaceAvailable()` →
`NativeInterop.setupGsHandler(aaSurface)` → PS2 emulator renders onto the
car's screen.

```
app/src/main/java/com/whetherman/ruviewauto/
  RuViewCarAppService.java   CarAppService entry point
  RuViewSession.java         picks PS2Screen
  PS2Screen.java             the actual hijack: boots Play!, wires its
                             renderer to the AA surface
  BouncingBallScreen.java    earlier Canvas-only POC (kept for reference)

app/src/main/java/com/virtualapplications/play/
  NativeInterop.java         vendored from Play! (BSD-2), unmodified
  Bootable.java              vendored from Play! (BSD-2), unmodified

app/src/main/jniLibs/arm64-v8a/libPlay.so   prebuilt Play! native core
app/src/main/assets/console_demo.elf        free homebrew test ROM (MIT,
                                             from glampert/ps2-homebrew)
```

## The real-car gate (read this before you spend a weekend on it)

Getting a sideloaded `CarAppService` (nav/POI category) to even *show up* on
a real car is the hard part, not the rendering hack:

1. **Unknown sources** — Android Auto hides sideloaded nav apps by default.
   Phone Settings → Android Auto → tap Version 10x → Developer settings →
   enable Unknown sources.
2. **Fresh installs start "stopped"** — Android won't let other apps bind to
   a just-installed app's service until you've launched it once yourself.
   `adb shell am start -n <pkg>/.MainActivity` once after every install.
3. **`androidx.car.app.minCarApiLevel` must be on `<application>`, not
   `<service>`** — `AppInfo.retrieveMinCarAppApiLevel()` reads it via
   `getApplicationInfo()`, which only sees application-level meta-data. Get
   this wrong and you get a binder crash with no obvious cause.
4. **The real gate: Play Store ownership check.** On an actual car, Gearhead
   asks Play Services who owns your package (`PlayGearheadService`). If
   there's no Play record, it logs `CAR.VALIDATOR: Package DENIED` and the
   app silently never appears — Unknown sources alone does not bypass this.
   Setting the installer attribution locally (`adb install -i
   com.android.vending`) does **not** fool it; it's a server-side check.
   Known working paths: a real Play Developer account + internal/closed
   testing track listing, or (reportedly, inconsistently) sideload tools
   like Shizuku/"King Installer" on some Samsung phones.
5. **Google's Desktop Head Unit (DHU) skips the ownership check entirely.**
   This is how the whole thing was validated tonight — no Play listing, no
   real car needed. See `docs/dhu-setup.md` for the headless/cross-arch
   build notes (needed qemu-user tricks on an aarch64 host; trivial on an
   x86_64 machine).
6. **Wireless (Bluetooth) Android Auto sessions hit the same gate as wired
   ones** — confirmed live. Swapping USB cables or toggling Bluetooth
   off/on does NOT reliably force Gearhead to re-scan the installed app
   list (confirmed: zero log activity for this package after a BT toggle).
   What DOES force a real rescan: `adb shell am force-stop
   com.google.android.projection.gearhead` — this fully tears down and
   restarts the whole Gearhead session (car screen goes blank, then
   reconnects on its own), and the restart always re-runs the full
   `CAR.VALIDATOR` scan.
7. Also checked and ruled out as causes this session: the app being in a
   Samsung "stopped"/frozen background state (it wasn't — but if yours is,
   `adb shell dumpsys deviceidle whitelist +<pkg>` plus
   `adb shell cmd appops set <pkg> RUN_IN_BACKGROUND allow` and
   `RUN_ANY_IN_BACKGROUND allow` fixes that specific case), and Samsung's
   "Secure Folder" showing as a second Android user (`pm list users`) —
   that's an unrelated Knox container, not an Android Auto user profile.

## Getting a real Play ownership record

Two real fixes for the gate above:

1. **Google Play Developer account ($25 one-time)** — upload to an
   Internal or Closed testing track (no public review needed). This is
   the path in progress as of this session.
2. Shizuku-based sideload tricks — free, reported inconsistent on
   Samsung phones specifically, unverified for this device.

Release signing is already set up for path 1:

- Release keystore: `/root/secrets/ruviewauto-release.keystore` (password
  in the sibling `.pw` file, both outside the repo and outside git —
  **back this keystore up somewhere durable**; losing it means you can
  never ship an update to the same `applicationId` again).
- `app/build.gradle` has a `release` signing config wired to that
  keystore (falls back to unsigned if the keystore files aren't present,
  so the build doesn't break on a machine without them).
- Build command: `/opt/gradle/gradle-8.9/bin/gradle bundleRelease
  --no-daemon` (no `gradlew` wrapper script in this repo yet — use the
  system Gradle 8.9 install directly, matches the wrapper's pinned
  version).
- Output: `app/build/outputs/bundle/release/app-release.aab` — this is
  what gets uploaded to Play Console's internal testing track.

## Credits

- [Play!](https://github.com/jpd002/Play-) by Jean-Philip Desjardins and
  contributors (BSD-2-Clause) — the actual PS2 emulator. This project just
  redirects its output.
- [glampert/ps2-homebrew](https://github.com/glampert/ps2-homebrew) (MIT) —
  the test demo ELF.
- [Vela](https://github.com/PimpinPumpkin/Vela)'s `docs/ANDROID-AUTO.md` —
  the clearest public writeup of the Play Store ownership gate; saved a lot
  of guessing.

## License

Code original to this repo: MIT. `NativeInterop.java`/`Bootable.java` keep
Play!'s BSD-2-Clause (see header). `libPlay.so` is Play!'s compiled output,
same license.

## Disclaimer

Parked use only. This is a hobby experiment in what Android Auto's
navigation-template surface API technically allows, not a product. No
commercial game ROMs are included or required.
