# Building the 0.33.4 corresponding source

The source release includes `app-source/`, `native-source/`, `java-sources/`,
`desugar-source/`, `prebuilt-native/`, `notices/`, `yt-dlp-source/` and a SHA-256 manifest.
It corresponds to **0.33.4-full/share** at the revision in `SOURCE_COMMIT.txt`.
It reuses the source-built native runtime introduced in 0.33.3; it does not
cover the old 0.33.2 native binaries.
No signing private key, password or development Git history is included.

## Native source and build inputs

Use Ubuntu 24.04 WSL2 and the tool versions listed in
`native-source/host-packages.tsv` and `toolchain.txt`. Install the development tools
listed in `WSL_NATIVE_BUILD.md`. Obtain Android NDK r28c from the URL and SHA-256
in that document. General-purpose SDK/JDK/NDK tools are external prerequisites.

1. Extract `native-source/termux-recipes.tar.gz` to `/opt/moami-native/`.
   These are the actual recipes used, including the two MOAMI patches; do not
   apply `native-patches/` again to this already-patched archive.
2. Use a non-root account named `builder`. Copy each directory under
   `native-source/source-inputs/` to `/home/builder/.termux-build/<package>/cache/`.
   The captured CA certificate PEM is source data, not a private key. Recipes
   include upstream URLs and hashes and may still download host tools or data.
3. Place NDK r28c at `/home/builder/lib/android-ndk-r28c`. Build on the Linux
   filesystem, not `/mnt/c`. From the recipe directory run:

   ```sh
   ./build-package.sh -a aarch64 python
   ./build-package.sh -a aarch64 quickjs
   ```

   Do **not** use `-I` (prebuilt dependency packages). The recipes build runtime
   dependencies and host/build prerequisites. Python's recipe intentionally uses
   one compilation worker. The original build's configuration files are included
   under `native-source/configuration/`; they are evidence, not portable commands.
4. From the extracted source-release root, package the result using the app source's script:

   ```sh
   python3 app-source/tools/package_native_runtime.py \
     --recipes /opt/moami-native/termux-packages \
     --cache /home/builder/.termux-build --output /opt/moami-native/runtime
   ```

The packager follows package dependencies, checks ARM64 ELF dependencies, copies
the real Python/QuickJS executables and flattens safe in-tree symlinks in private
runtime data. It excludes static archives and bytecode caches and collects actual
license texts. `manifest.json` maps packaged files to hashes and source-built DEBs.
Build-time paths/timestamps can change hashes. If rebuilding differs, inspect the
inputs and generated manifest; intentionally record a new `native-runtime-lock.json`
for your build instead of silently bypassing the integrity check. Original payload
and source provenance are supplied independently of byte reproducibility.

## Full and Share APKs

For building the exact provided payload, set `MOAMI_NATIVE_RUNTIME` to the absolute
`prebuilt-native/` path in this source release. For a native rebuild, point it to
your generated runtime and use its reviewed lock as described above.

From `app-source/`, with JDK 21, Android SDK/Build Tools 36.1.0 and an **external**
signing properties file:

Set `BROWSERDOWNLOADER_SIGNING_PROPERTIES` to a UTF-8 Java properties file
outside the repository. It must contain `storeFile`, `storePassword`, `keyAlias`
and `keyPassword`. Use `/` in paths; relative keystore paths are resolved against
the properties file's directory. Keep the private key and passwords out of Git
and source releases.

```powershell
.\gradlew.bat :app:assembleRelease --no-configuration-cache
.\gradlew.bat :app:assembleRelease -PmoamiShare=true --no-configuration-cache
```

The source JARs under `java-sources/` correspond to the resolved Maven libraries;
their coordinates, artifact hashes and source URLs are in `java-manifest.json`.
Gradle resolves the pinned published dependencies normally. Desugaring 2.1.5 uses
the upstream release-preparation source revision
`73170c345e6a762fc6a1f0301bb15218850023ef`, with its original build files and license
in `desugar-source/`. Build tools are external prerequisites, not app runtime data.

Validate APK boundaries and native provenance with `verify_editions.py` and
`verify_native_apk.py`. Read `notices/` and the app's `licenses/` assets. Sources
and original third-party notices must remain available with distributed APKs.
