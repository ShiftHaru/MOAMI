"""Run in WSL after the pinned Termux Python/QuickJS source builds."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

PREFIX = Path('data/data/com.termux/files/usr')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(recipes, output, cache):
    debs = {}
    for deb in sorted((recipes / 'output').glob('*.deb')):
        name = subprocess.check_output(['dpkg-deb', '-f', str(deb), 'Package'], text=True).strip()
        if name in debs:
            raise ValueError('Multiple versions of package: ' + name)
        debs[name] = deb
    selected = {}
    pending = ['python', 'quickjs', 'ca-certificates']
    while pending:
        name = pending.pop()
        if name in selected:
            continue
        deb = debs[name]
        selected[name] = deb
        depends = subprocess.check_output(['dpkg-deb', '-f', str(deb), 'Depends'], text=True).strip()
        for item in filter(None, depends.split(',')):
            if '|' in item:
                raise ValueError('Resolve dependency alternatives explicitly: ' + item)
            pending.append(item.strip().split()[0])
    output.mkdir(parents=True, exist_ok=True)
    jni = output / 'jniLibs/arm64-v8a'
    assets = output / 'assets/native'
    jni.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)
    licenses = output / 'assets/licenses'
    licenses.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='moami-package-') as temp:
        stage = Path(temp)
        for deb in selected.values():
            subprocess.run(['dpkg-deb', '-x', str(deb), str(stage)], check=True)
        usr = stage / PREFIX
        for source, target in [('python3.12', 'libpython.so'), ('qjs', 'libqjs.so')]:
            shutil.copyfile(usr / 'bin' / source, jni / target)
        files = {}
        for path in sorted(usr.rglob('*')):
            relative = path.relative_to(usr)
            if not path.is_file() or not str(relative).startswith(('lib/', 'etc/tls/', 'share/doc/')):
                continue
            if path.suffix in ('.a', '.la', '.pyc') or '__pycache__' in path.parts:
                continue
            if not path.resolve().is_relative_to(usr.resolve()):
                raise ValueError('External package link: ' + str(relative))
            files['usr/' + relative.as_posix()] = path
        # Verify all ELF dependencies can be resolved inside the bundle or Android.
        names = {p.name for p in files.values()}
        system = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libz.so'}
        for path in list(files.values()) + list(jni.glob('*.so')):
            if path.read_bytes()[:4] != b'\x7fELF':
                continue
            header = subprocess.check_output(['readelf', '-h', str(path)], text=True)
            if 'AArch64' not in header:
                raise ValueError('Unexpected ELF architecture: ' + path.name)
            dynamic = subprocess.check_output(['readelf', '-d', str(path)], text=True)
            missing = set(re.findall(r'NEEDED.*\[(.*?)\]', dynamic)) - names - system
            if missing:
                raise ValueError(f'{path.name}: unresolved {missing}')
        with zipfile.ZipFile(assets / 'runtime.zip', 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, path in files.items():
                info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100600 << 16
                archive.writestr(info, path.read_bytes())
        notices = ['MOAMI source-built ARM64 native components\nOriginal copyright and license texts follow.\n']
        for name in sorted(selected):
            recipe_name = 'ncurses' if name == 'ncurses-ui-libs' else name
            recipe = recipes / 'packages' / recipe_name / 'build.sh'
            notices.append('\nCOMPONENT: ' + name + '\n' + '\n'.join(line for line in recipe.read_text().splitlines() if line.startswith(('TERMUX_PKG_LICENSE', 'TERMUX_PKG_HOMEPAGE', 'TERMUX_PKG_VERSION'))))
            candidates = list((cache / recipe_name / 'src').glob('COPYING*')) + list((cache / recipe_name / 'src').glob('LICENSE*'))
            candidates += list((recipes / 'packages' / recipe_name).glob('LICENSE*'))
            candidates += [path for key,path in files.items() if key.startswith('usr/share/doc/' + name + '/') and re.search('license|copying|copyright',path.name,re.I)]
            seen=set()
            for path in sorted(set(candidates)):
                if not path.is_file() or sha(path) in seen: continue
                seen.add(sha(path))
                notices.append('\nFILE: ' + path.name + '\n' + path.read_text(errors='strict'))
            if not seen and name != 'ca-certificates':
                raise ValueError('Missing component license: ' + name)
            if name == 'ca-certificates':
                notices.append('Mozilla CA certificate data: MPL-2.0. https://curl.se/docs/caextract.html\nSee Mozilla-Public-License-2.0.txt in the corresponding source notices.')
        notice = licenses / 'native-runtime-NOTICES.txt'
        notice.write_text('\n'.join(notices), encoding='utf-8')
        manifest = dict(schema=1, packages=[dict(name=n, file=p.name, sha256=sha(p)) for n,p in sorted(selected.items())],
                        files={str(p.relative_to(output)):sha(p) for p in [assets / 'runtime.zip', notice, *sorted(jni.glob('*.so'))]},
                        runtimeFiles={name:sha(p) for name,p in files.items()})
        (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        (assets / 'runtime.sha256').write_text(sha(assets / 'runtime.zip') + '\n')
        print(json.dumps({'packages': list(sorted(selected)), 'files': len(files), 'output': str(output)}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--recipes', type=Path, default=Path('/opt/moami-native/termux-packages'))
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--cache', type=Path, default=Path('/home/builder/.termux-build'))
    a = p.parse_args()
    package(a.recipes, a.output, a.cache)
