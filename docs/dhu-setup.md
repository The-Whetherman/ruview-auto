# Running the Desktop Head Unit (DHU) for testing

Google's DHU simulates a car head unit over adb, and — importantly — it
**doesn't run the Play Store ownership check** that blocks sideloaded nav
apps on real cars. This is how the whole project got validated without a
Play listing.

## If your dev machine is x86_64 (do this, it's trivial)

```
# grab the DHU
curl -sL -o dhu.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
# actually: https://dl.google.com/android/repository/desktop-head-unit-linux-x64_r02.0.zip
unzip dhu.zip && chmod +x desktop-head-unit

# deps (Debian/Ubuntu)
sudo apt install libsdl2-2.0-0 libsdl2-ttf-2.0-0 libportaudio2 libc++1 libc++abi1 libunwind8

# on the phone: Settings -> Android Auto -> tap Version 10x -> Developer
# settings -> "Start head unit server" (short-lived, press it right before
# connecting each time)
adb forward tcp:5277 tcp:5277
./desktop-head-unit --adb
```

If it segfaults on an old distro with a missing `libc++.so.1`/`libunwind.so.1`,
`apt-get download` the real package and point `LD_LIBRARY_PATH` at the
extracted `.so` (version mismatches between what DHU was linked against in
2022 and current distros are common; no need for anything fancier than a
version-pinned `.so` sitting next to it).

## If your dev machine is aarch64 (e.g. this was built on a Termux/proot
Android phone) — don't do what we did, just use an x86_64 machine

We ran DHU under `qemu-x86_64-static` user-mode emulation since the
official DHU binary is x86_64-only and Google doesn't ship an aarch64
build. It technically works but:

- Needs the full x86_64 glibc stack via Debian multiarch
  (`dpkg --add-architecture amd64`), not just qemu.
- Hits a **real, unfixed qemu-user bug**: `linux-user/mmap.c:1491:
  target_shmat: Assertion 'test == want' failed` — a SysV shared-memory
  remap bug in qemu's `do_shmat()`. `strace` showed DHU calling
  `shmat(id, NULL, 0)` (no specific address requested) and qemu still
  failing to honor it. No env var or `-R` flag fixes this; it needs a
  patched qemu built from source.
- Native compilation (NDK/clang/cmake for the actual PS2 emulator build)
  is the same story but worse — every single compiler invocation would
  need qemu wrapping. Not worth it.

If you're in this position, SSH to any x86_64 box you have (even
temporarily) and do the DHU + native build there instead. That's what we
ended up doing.

## `aapt2FromMavenOverride` gotcha (only matters for the qemu path)

If you do end up needing to run AAPT2 under qemu, the override check in
AGP is a dumb string check (`Aapt2MavenUtils.kt`):

```kotlin
if (!customAapt2.endsWith(SdkConstants.FN_AAPT2)) error(...)
```

It just checks the path string ends with the literal filename `aapt2` —
not that it's a real ELF, not anything about architecture. Name your
wrapper script/binary exactly `aapt2` (put it in its own directory) and
AGP will happily accept a shell script or even a cross-arch native
trampoline binary that execs qemu.
