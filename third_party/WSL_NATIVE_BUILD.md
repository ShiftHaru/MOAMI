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
libtool, pkg-config, cmake/ninja, gettext, texinfo, bison/flex, gawk, jq, curl/git,
zip/unzip, xz/bzip2/lzip, rsync, file, locales, OpenJDK 17 JRE (for the certificate
package's host-side keystore generation), and common Python development
libraries). This is a tested initial tool set, not yet a complete dependency lock.

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
build configuration, notices and hashes before packaging. x86_64, QuickJS, package
assembly and app integration remain separate validation steps. See
`NATIVE_SOURCE_GAPS.md`; this setup does not close the APK distribution gate.
