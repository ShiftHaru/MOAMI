# Native corresponding-source gaps

APK distribution is not approved by this inventory. Certificate publication was
approved by the owner; exact native build correspondence remains unresolved.

The inspected arm64-v8a and x86_64 Python archives contain
`usr/lib/python3.12/_sysconfigdata__linux_.py`, but no native source patches,
Makefiles or complete native license bundle. Configuration output alone cannot
reconstruct the actual patched source tree.

| Evidence in packaged runtime | Source acquired | Still missing |
| --- | --- | --- |
| Python 3.12.11 | Official upstream archive | Applied Android patches and exact build inputs |
| QuickJS 2025-04-26, maintainer identification | Official upstream archive | Applied Android patches and exact build inputs |
| libreadline.so.8.3 / libhistory.so.8.3 | Readline 8.3 | Patch level and Android build inputs |
| GDBM version string 1.26 | GDBM 1.26 | Android build inputs |
| Expat version string 2.7.3 | Expat 2.7.3 | Android build inputs |
| OpenSSL version string 3.5.2 | OpenSSL 3.5.2 | Android build inputs |
| ncurses 6.5 / SQLite 3.50.4 / xz 5.8.1 / zlib 1.3.1 / bzip2 1.0.8 names | Not acquired in this batch | Exact source versions, patches, notices and build inputs |
| libffi, Android support/semaphore, libc++ and other runtime entries | Not established | Exact version, source, applicable notices and build inputs |

Upstream Python update commit changes only eight binary files:
https://github.com/yausername/youtubedl-android/commit/ed17169174832cbdd3839f7f5d5b35c9cc72e85f

The pinned build guide instead documents Python 3.7.2 examples:
https://raw.githubusercontent.com/yausername/youtubedl-android/d725d5c9a18c3a99a13ee0308bf78275dc310760/BUILD_PYTHON.md

## Information needed from the binary builder

For the six ABI artifacts identified by hashes in
`xprobe/src/main/assets/licenses/native-origin.json`, obtain:

1. The exact termux-packages revision and all local changes.
2. Source archive versions/hashes and applied patches for each dependency.
3. NDK/toolchain versions, configuration, build and packaging scripts.
4. The complete applicable license and copyright notices.

No maintainer message has been sent. A different route is to build replacement
native runtimes from documented sources and validate ARM64 and all media
flows. That is a runtime replacement, not proof about the existing binaries.

## Rebuild candidate acquired

The complete Termux recipes/patches snapshot at
`4af4053b0a94ac28a035159d3f425e0c32b8944f` has been acquired and hash-pinned in
`source-acquisition.json`. `native-rebuild-candidate.json` records the paths and
hashes of recipe/patch files for 15 relevant packages. The snapshot was selected
near an observed native build date; this is a candidate, not the original build
revision. Its Python version is 3.12.11, but its Expat recipe is 2.7.1 while the
existing APK reports 2.7.3. Do not mark binary correspondence complete.

For replacement builds, use the Linux environment documented in
`WSL_NATIVE_BUILD.md` (Docker is not required), pin the host inputs and NDK,
build dependencies from the candidate source (not downloaded
prebuilt packages), archive all actual source inputs/patches/notices, then package
and test arm64-v8a. Only ARM64 is supported; x86_64 is no longer a release target.
Any recipe modifications must be preserved as source.
Source builds are in progress under Ubuntu 24.04 WSL2. No replacement runtime has
been integrated into the app; successful dependency builds do not close this gate.
