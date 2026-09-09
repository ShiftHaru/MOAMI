"""Assemble a committed app tree and verified local corresponding-source inputs."""
import argparse
import hashlib
import io
import json
import subprocess
import tarfile
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def package(root, native, runtime, output, revision):
    commit = subprocess.check_output(['git', 'rev-parse', revision + '^{commit}'], cwd=root).decode().strip()
    source = subprocess.check_output(['git', 'archive', '--format=tar', commit], cwd=root)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise ValueError('Output already exists; use a new path')
    hashes = {}
    with tarfile.open(output, 'w:gz') as target:
        def add(name, data, mode=0o644):
            if name in hashes:
                if hashes[name] == digest(data):
                    return  # Multiple binary variants can share one source JAR.
                raise ValueError('Conflicting archive member')
            if name.startswith('/') or '..' in Path(name).parts:
                raise ValueError('Invalid archive member')
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mode = mode
            target.addfile(info, io.BytesIO(data))
            hashes[name] = digest(data)

        with tarfile.open(fileobj=io.BytesIO(source)) as app:
            committed = {}
            for entry in app:
                if entry.isdir():
                    continue
                if not entry.isfile():
                    raise ValueError('Unexpected non-file in app Git archive')
                data = app.extractfile(entry).read()
                add('app-source/' + entry.name, data, entry.mode)
                if entry.name.startswith('third_party/'):
                    committed[entry.name] = data
            lock = json.loads(committed['third_party/native-runtime-lock.json'])
            receipts = json.loads(committed['third_party/source-acquisition.json'])
            add('BUILDING.md', committed['third_party/BUILDING_SOURCE_RELEASE.md'])

        native_manifest = json.loads((native / 'SHA256.json').read_text())
        for name, sha in native_manifest.items():
            data = (native / name).read_bytes()
            if digest(data) != sha:
                raise ValueError('Native source digest mismatch: ' + name)
            add('native-source/' + name, data)
        add('native-source/SHA256.json', (native / 'SHA256.json').read_bytes())
        for name, sha in lock['files'].items():
            data = (runtime / name).read_bytes()
            if digest(data) != sha:
                raise ValueError('Native payload digest mismatch: ' + name)
            add('prebuilt-native/' + name, data)
        add('prebuilt-native/manifest.json', committed['third_party/native-runtime-lock.json'])
        add('prebuilt-native/assets/native/runtime.sha256', (lock['files']['assets/native/runtime.zip'] + '\n').encode())

        sources = root / '.local/release-sources'
        java = json.loads((sources / 'java-manifest.json').read_text())
        if java['failures'] or len(java['artifacts']) != 62:
            raise ValueError('Incomplete Java source collection')
        for item in java['artifacts']:
            data = (sources / 'java' / item['sourceFile']).read_bytes()
            if digest(data) != item['sourceSha256']:
                raise ValueError('Java source digest mismatch')
            add('java-sources/' + item['sourceFile'], data)
        add('java-manifest.json', (sources / 'java-manifest.json').read_bytes())
        for name, folder in [('desugar_jdk_libs-2.1.5.tar.gz', 'desugar-source'), ('yt-dlp-2026.08.19.tar.gz', 'yt-dlp-source')]:
            item = next(a for a in receipts['artifacts'] if a['file'] == name)
            data = (sources / name).read_bytes()
            if len(data) != item['bytes'] or digest(data) != item['sha256']:
                raise ValueError('Upstream source digest mismatch')
            add(folder + '/' + name, data)
        # Notices are also preserved at their original paths inside app-source.
        for name, data in committed.items():
            if name.endswith(('-LICENSE.txt', '-LICENSES.txt')):
                add('notices/' + Path(name).name, data)
        add('SOURCE_COMMIT.txt', (commit + '\n').encode())
        manifest = json.dumps(hashes, indent=2).encode() + b'\n'
        add('SHA256.json', manifest)
    with tarfile.open(output) as check:
        for entry in check:
            if digest(check.extractfile(entry).read()) != hashes[entry.name]:
                raise ValueError('Source archive verification failed')
    print(json.dumps(dict(commit=commit, files=len(hashes), bytes=output.stat().st_size,
                          sha256=digest(output.read_bytes()))))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--revision', default='HEAD')
    p.add_argument('--native', type=Path, default=Path('.local/native-source-0.33.3'))
    p.add_argument('--runtime', type=Path, default=Path('.local/native-runtime'))
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    package(Path.cwd(), a.native, a.runtime, a.output, a.revision)
