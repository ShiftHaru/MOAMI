# 0.33.3 current runtime

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

# Third-party components in the X probe

## Integrated Material 3 UI (0.23.0)

The integrated `app` uses `com.google.android.material:material:1.14.0`.
Its Apache-2.0 LICENSE is preserved byte-for-byte in
`../app/src/main/assets/licenses/material-components-LICENSE.txt` from
https://raw.githubusercontent.com/material-components/material-components-android/1.14.0/LICENSE.
`material-runtime-NOTICES.txt` collects embedded license/notice entries from the
resolved runtime archives, with their component coordinates and entry names.
`gallery-runtime-artifacts.json` records the current 69 resolved artifacts and
the separate desugaring input by coordinate/hash, without local paths.
These additions apply to `app`; the separate diagnostic `xprobe` UI is unchanged.
This inventory does not resolve the previously documented native APK source gates.

## Integrated gallery additions (0.22.0)

The integrated `app` additionally uses `org.jsoup:jsoup:1.23.2` (MIT) to parse
user-requested public HTML, with `com.android.tools:desugar_jdk_libs_nio:2.1.5`
for Android compatibility. jsoup's pinned upstream LICENSE is preserved at
`../app/src/main/assets/licenses/jsoup-MIT.txt` from
https://raw.githubusercontent.com/jhy/jsoup/jsoup-1.23.2/LICENSE.
The desugaring upstream LICENSE and ADDITIONAL_LICENSE_INFO (including Classpath
exception information) are preserved in that directory from commit
`092407c51c3eaaaa9e46f7b7e436dc642f614064` of
https://github.com/google/desugar_jdk_libs. These are upstream notices, not proof
of complete source correspondence for every transformed runtime class in an APK.
The root project GPL text and yt-dlp's aggregate third-party notices are also
included in the integrated app's assets. APK publication remains separate.

The 50-artifact inventory below describes the pre-gallery baseline; it does not
include jsoup or core-library desugaring and must not be presented as the complete
0.22.0 dependency inventory.

This is a local validation application. This inventory is not a completed license
audit or authorization to redistribute the APK. Project decisions and remaining
release checks are maintained in the Obsidian notes referenced by `AGENTS.md`.

| Component | Pinned version/source | License text included |
|---|---|---|
| youtubedl-android library/common | 0.18.1, upstream commit `d725d5c9a18c3a99a13ee0308bf78275dc310760` | `src/main/assets/licenses/youtubedl-android-GPL-3.0.txt` |
| yt-dlp Python zip application | 2026.08.19 | `src/main/assets/licenses/yt-dlp-Unlicense.txt` |
| Square gifencoder | 0.10.1, upstream commit `a359dae057a31636a1a6d621904cab81cf45bdb0` | `src/main/assets/licenses/gifencoder-Apache-2.0.txt` |

Upstream sources:

- https://github.com/yausername/youtubedl-android/tree/0.18.1
- https://github.com/yt-dlp/yt-dlp/tree/2026.08.19
- https://github.com/square/gifencoder/tree/gifencoder-0.10.1

The yt-dlp resource is the unmodified Python zip application from
https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp.
Its SHA-256 is
`1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`.
The application checks that digest and loads the APK resource; it does not invoke
the wrapper's network updater.

The zipimport release also bundles meriyah (ISC) and astring (MIT); the
Unlicense text alone does not cover those components. The unmodified upstream
2026.08.19 THIRD_PARTY_LICENSES.txt is preserved at
`../third_party/yt-dlp-THIRD_PARTY_LICENSES.txt` from
https://raw.githubusercontent.com/yt-dlp/yt-dlp/2026.08.19/THIRD_PARTY_LICENSES.txt.
That upstream aggregate also lists PyInstaller dependencies which need not be
present in this zip. It is not an exact inventory of this APK, and packaging
the applicable notices in a distributable APK remains a release check.

The Gradle wrapper JAR's embedded META-INF/LICENSE is preserved unchanged at
`../third_party/gradle-wrapper-LICENSE.txt` for source checkout distribution.

The wrapper also bundles native Python, QuickJS and Python runtime dependencies.
The license/source correspondence for those binaries and the complete transitive
Gradle dependency inventory still require verification before distribution.
Including the three license texts above does not complete those obligations.
FFmpeg is not a dependency of the final X probe.

## Resolved transitive artifacts

Additional upstream source archives have been acquired for CPython 3.12.11,
QuickJS 2025-04-26 and the wrapper 0.18.1 sources JAR. Exact download hashes are
recorded in `../third_party/source-acquisition.json`; CPython and QuickJS original
LICENSE files are included in `assets/licenses/`. This does not establish the
complete Android native corresponding source. See `../third_party/RELEASE_CHECKS.md`.

The selected debug runtime configuration is recorded in the packaged
`assets/licenses/runtime-artifacts.json`: 50 artifacts from 47 Maven coordinates.
Some coordinates provide auxiliary artifacts; resolution is not proof that every
artifact's code enters the APK DEX. Test and build-tool dependencies are outside
this inventory.

The inventory also records exact cached POM hashes, declared license names and
URLs, source URLs where present, and parent chains used for inheritance.
Declarations were found for all 47 coordinates: 45 declare Apache 2.0 variants
and two wrapper coordinates declare GPL-3.0. This is metadata evidence, not a
completed review of notices, compatibility or corresponding source. Guava
listenablefuture inherits its declaration from guava-parent 26.0-android;
Commons IO/Compress inherit through commons-parent 39 and org.apache:apache:16.

Nine unmodified LICENSE/NOTICE files from the resolved Jackson annotations/core/
databind, Commons IO and Commons Compress JARs are included under
`assets/licenses/transitive/`. Their artifact coordinates and paths are recorded
in that inventory. AndroidX, Kotlin, annotations, Guava listenablefuture and the
native bundle still require further source/license correspondence and notice
review. A missing license file in an archive is not evidence of no obligations.

The Python ZIPs contain native libraries including OpenSSL, readline/history,
gdbm, ncurses, libffi, expat, sqlite, compression libraries and Android support
libraries. File names alone do not establish exact source revisions, build
options or license terms. Those are not covered by the wrapper's single GPL text.

`assets/licenses/native-origin.json` records the six packaged Python executable,
Python ZIP and QuickJS files across arm64-v8a and x86_64. Their Git blob hashes
match upstream 0.18.1 at commit
`d725d5c9a18c3a99a13ee0308bf78275dc310760`; SHA-256 hashes are also recorded.
This establishes the binary source repository, not corresponding source code
or reproducibility. The Python/QuickJS update commits inspected contain binary
changes and do not supply the complete build inputs. The tag's BUILD_PYTHON.md
uses older Python 3.7.2 examples, not a verified recipe for this 3.12.11 bundle.

Recreate the selected inventory with the configured Android SDK/JDK:

```text
gradlew.bat --no-configuration-cache -I tools/runtime_inventory.gradle :xprobe:writeRuntimeInventory :app:writeRuntimeInventory
python tools/audit_runtime_archives.py xprobe/build/reports/runtime-inventory.json xprobe/build/outputs/apk/debug/xprobe-debug.apk .local/evidence/g4-xprobe-archives.json
```

The archive audit records hashes and nested notice locations. It is an inventory,
not a license compatibility decision or complete corresponding-source delivery.

`src/main/assets/self_authored.mp4` is a generated test pattern, not third-party
or X media. Recreate a corresponding fixture with an installed FFmpeg:

```text
ffmpeg -nostdin -v error -y -f lavfi -i testsrc=size=160x120:rate=10:duration=2 -c:v libx264 -pix_fmt yuv420p self_authored.mp4
```

Encoder versions may produce different bytes. The checked-in fixture is 7,685
bytes, SHA-256 `a82d2828fa3acc22e2e2f3ae404cc72f3f43a2063a92a51f69d8825cc1721120`.
