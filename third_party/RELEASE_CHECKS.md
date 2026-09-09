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
