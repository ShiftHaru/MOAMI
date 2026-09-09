"""Verify the APK contains only the locked MOAMI native payload, not the old wrapper."""
import argparse
import hashlib
import io
import json
import zipfile
from pathlib import Path


def verify(apk, lock):
    expected=json.loads(lock.read_text())
    with zipfile.ZipFile(apk) as archive:
        for name,digest in expected['files'].items():
            apk_name=name.replace('jniLibs/','lib/',1)
            assert hashlib.sha256(archive.read(apk_name)).hexdigest()==digest, apk_name
        natives={n for n in archive.namelist() if n.startswith('lib/') and not n.endswith('/')}
        assert natives=={'lib/arm64-v8a/libpython.so','lib/arm64-v8a/libqjs.so'},natives
        for name in archive.namelist():
            if name.endswith('.dex'):
                assert b'Lcom/yausername/youtubedl_android/' not in archive.read(name), 'Old wrapper in DEX'
        with zipfile.ZipFile(io.BytesIO(archive.read('assets/native/runtime.zip'))) as runtime:
            assert set(runtime.namelist())==set(expected['runtimeFiles'])
            for name,digest in expected['runtimeFiles'].items():
                assert hashlib.sha256(runtime.read(name)).hexdigest()==digest,name
        assert archive.read('assets/licenses/Mozilla-Public-License-2.0.txt')
    print('PASS: locked executables, runtime entries, native notices; no old wrapper or native ZIP')


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk',type=Path)
    p.add_argument('--lock',type=Path,default=Path('third_party/native-runtime-lock.json'))
    a=p.parse_args();verify(a.apk,a.lock)
