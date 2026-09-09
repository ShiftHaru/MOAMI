# 0.33.3 replacement release

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

# Source publication and APK redistribution

The project is GPL-3.0-only; third-party copyright and license notices remain
under their respective terms. Do not remove upstream authors' notices when
anonymizing the project owner's Git identity.

Before publishing Git content:

```text
python tools/check_public_tree.py
```

This checks tracked working files and all reachable history, including project
author/committer identities. It is a pattern-based check, not proof that every
secret or vulnerability has been found. Publish Git content, not a ZIP of the
working directory: ignored local signing settings, evidence and caches are private.
Remote forks, previous clones, releases and hosting-provider caches are outside
the local rewrite. Do not fetch old refs back into an anonymized publication tree.
An existing remote needs an explicitly coordinated history replacement; a normal
push may be rejected. No automatic push or force-push is performed.

## Acquired source materials

`source-acquisition.json` records download URLs, exact sizes and SHA-256 hashes.
Acquire or verify local archives with:

```text
python tools/fetch_release_sources.py
```

The archives are kept in ignored `.local/release-sources/`. Their notices are
included in the shared Android assets. These are upstream sources, **not yet a
complete corresponding-source package for the Android binaries**:

| Material | Evidence | Remaining work |
| --- | --- | --- |
| Wrapper 0.18.1 sources JAR | Exact Maven coordinate | Native code is not supplied by this JAR |
| CPython 3.12.11 | Official versioned source and LICENSE | Android patches, build recipe and transitive native source mapping |
| QuickJS 2025-04-26 | Version identified by upstream maintainer in PR 338; official source and LICENSE | Exact Android changes and build inputs |
| Readline 8.3 / GDBM 1.26 / Expat 2.7.3 / OpenSSL 3.5.2 | Packaged filename/version strings, official archives and original notices | Android changes, applied patches and exact build inputs |

Evidence:
- https://github.com/yausername/youtubedl-android/pull/338
- https://raw.githubusercontent.com/yausername/youtubedl-android/d725d5c9a18c3a99a13ee0308bf78275dc310760/BUILD_PYTHON.md
- https://www.python.org/downloads/release/python-31211/
- https://bellard.org/quickjs/

## APK distribution remains blocked

Before uploading an APK, establish the exact native build inputs and applicable
source/notices for Python, QuickJS and bundled libraries (including readline,
history, gdbm, ncurses, OpenSSL and others listed in the native inventory).
Either obtain the actual upstream build inputs or replace the bundle with a
documented build from known sources and retest the app. Merely downloading a
similarly numbered source release does not close this requirement.

The owner explicitly approved publication of the existing APK signing certificate
on 2026-09-09. Keep the current signing key for update compatibility. This approval
covers public certificate metadata only: private keys and signing passwords must
never accompany either the APK or source package. Personal Git identity and local
paths remain excluded. Recheck the final APK signature before release.

Keep `apkDistributionReady` false until these checks and the complete packaged
notice review are finished. Source hosting, content permissions, trademark/art
rights and Google Play policy approval remain distinct checks.
