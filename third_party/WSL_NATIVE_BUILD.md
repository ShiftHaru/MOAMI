# 0.33.3 integration result

The opaque youtubedl-android 0.18.1 dependency is removed in 0.33.3.
Full and Share use the same locked ARM64 Python 3.12.11 / QuickJS 2025-04-26
runtime, built from the pinned Termux recipes with the recorded MOAMI patches.
The 17-package closure, packaged file hashes and actual source-built DEB hashes
are in `third_party/native-runtime-lock.json` (paths relative to repository root).
Native license texts accompany the runtime; the Mozilla MPL-2.0 text is also an
APK asset. Original upstream attribution is retained.

`third_party/BUILDING_SOURCE_RELEASE.md` describes the corresponding-source
bundle: actual patched recipes, original source inputs, build configuration,
62 Maven source JARs, desugaring release sources, yt-dlp sources and notices.
Python and QuickJS were rebuilt from the extracted recipe archive using the
existing build cache/sysroot; this is not a clean-room or byte-reproducibility
claim. On an ARM64 Android 16 device, runtime imports, QuickJS, generated MP4/GIF,
X GIF download/repeat-save and four Instagram media saves passed.

Distribute each 0.33.3 APK with its matching source archive, notices and hashes.
This does NOT supply the missing corresponding source for old 0.33.2 APKs.
Before making a repository public, remove those old binary assets from public
availability (or obtain their actual corresponding sources). Private prereleases
become visible when their repository becomes public. No visibility change is
performed by these build tools. Platform terms and Play approval are separate.

## Historical investigation (before replacement)

The text below records earlier versions and unresolved old-binary findings;
it does not describe the 0.33.3 runtime.

# Native build on WSL2

Use Ubuntu 24.04 under WSL2. Docker is not required. The working tree and build
outputs belong on the Linux filesystem, not under `/mnt/c`, to preserve symlinks
and Unix permissions. The app's existing runtime is unchanged until replacement
binaries pass both ABI and Android media-flow tests.

The current isolated layout is:

- Build account: `builder` (no personal identity)
- Source tree: `/opt/moami-native/termux-packages`
- Upstream snapshot: `4af4053b0a94ac28a035159d3f425e0c32b8944f`
- NDK: `/home/builder/lib/android-ndk-r28c`
- NDK archive SHA-256: `dfb20d396df28ca02a8c708314b814a4d961dc9074f9a161932746f815aa552f`
- NDK URL: https://dl.google.com/android/repository/android-ndk-r28c-linux.zip
- Outputs: `/opt/moami-native/termux-packages/output`
- Build log: `/opt/moami-native/python-aarch64.log`

Obtain the pinned recipe archive using `tools/fetch_release_sources.py` and
extract it under the Linux source directory, stripping its first path component.
Install standard Ubuntu development tools (C/C++, Python 3.12, autoconf/automake,
libtool, pkg-config, cmake/ninja, gettext/autopoint, texinfo, bison/flex, gawk, jq, curl/git,
zip/unzip, xz/bzip2/lzip, rsync, file, locales, OpenJDK 17 JRE (for the certificate
package's host-side keystore generation), Tcl (SQLite's host-side generator), and common Python development
libraries). This is a tested initial tool set, not yet a complete dependency lock.

Host package installation (inside Ubuntu, as root):

```sh
apt-get update
apt-get install -y --no-install-recommends \
  build-essential ca-certificates curl git python3 python-is-python3 \
  python3-venv python3-pip unzip zip xz-utils bzip2 patch autoconf automake \
  libtool-bin pkg-config cmake ninja-build gettext autopoint texinfo bison flex \
  gawk jq rsync file sudo locales libssl-dev zlib1g-dev libffi-dev libbz2-dev \
  liblzma-dev libsqlite3-dev libncurses-dev libreadline-dev help2man gperf lzip \
  openjdk-17-jre-headless tcl
```

## Recorded recipe change

Apply `native-patches/ncurses-no-extra-foot.patch` with `patch -p1` in the source
tree. Dry-run the patch and run `bash -n packages/ncurses/build.sh` first.

The original recipe downloads an additional foot terminal description archive.
Its server now returns bytes which do not match the pinned checksum. The patch
removes that extra download and its terminfo merge; it does not replace a checksum
with unverified bytes. Ncurses' own pinned source and standard terminal descriptions
are retained. MOAMI has no foot terminal UI. The modified recipe must accompany
any binaries built from it; this is not a reproduction of the old APK bundle.

Also apply `native-patches/libbz2-upstream-source.patch`. The old Fossies mirror
returns HTTP 500. This selects the official Sourceware bzip2 1.0.8 `.tar.gz`, with
its exact SHA-256 recorded in the patch and source manifest, rather than the
unavailable mirror's `.tar.xz`. Both patch dry-runs and shell syntax checks passed.

## Build invocation

From Windows, after preparing the Linux account/directories and applying the patch:

```text
wsl -d Ubuntu-24.04 -u builder -- bash -lc 'cd /opt/moami-native/termux-packages; export TERMUX_PKG_MAKE_PROCESSES=4; ./build-package.sh -a aarch64 python > /opt/moami-native/python-aarch64.log 2>&1'
```

Do not add `-I`: the intended path builds dependencies from source rather than
installing prebuilt Termux dependencies. Preserve the downloaded sources, patches,
build configuration, notices and hashes before packaging. QuickJS, package
assembly and app integration remain separate validation steps. Only arm64-v8a is
an app target; the x86_64 host remains a cross-compilation environment. See
`NATIVE_SOURCE_GAPS.md`; this setup does not close the APK distribution gate.

## Observed build result (2026-09-09)

The ARM64 Python 3.12.11 build finished successfully. The subsequent command
`./build-package.sh -a aarch64 quickjs` also finished successfully (log:
`/opt/moami-native/quickjs-aarch64.log`). Both Termux symbol checks reported zero
remaining undefined-symbol files after their standard exclusions for static
archives/objects. This is a build-time check, not Android runtime testing.

The Python and QuickJS executables are ARM aarch64 ELF PIE files using Android's
`/system/bin/linker64`. Python additionally needs the packaged `libpython3.12`
and Android support library; copying the executable alone is insufficient.
Package hashes from this run are in `native-rebuild-candidate.json`. They identify
these outputs, not a claim of byte-for-byte reproducibility across clean builds.
Source caches and dependency packages remain in the WSL filesystem. Redistribution
archive assembly, APK integration and device tests are not complete.
The owner removed x86_64 app support on 2026-09-09; it is not a pending build gate.
