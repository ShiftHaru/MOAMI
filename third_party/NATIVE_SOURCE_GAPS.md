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
native runtimes from documented sources and validate both ABIs and all media
flows. That is a runtime replacement, not proof about the existing binaries.
