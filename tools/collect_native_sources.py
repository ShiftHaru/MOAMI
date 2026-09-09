"""Capture actual WSL inputs, recipes and configuration without owner paths or keys."""
import argparse
import hashlib
import json
import shutil
import subprocess
import tarfile
from pathlib import Path


def collect(recipes, cache, output):
    output.mkdir(parents=True, exist_ok=False)
    # Preserve Unix modes and recipe symlinks; exclude generated packages and VCS metadata.
    with tarfile.open(output/'termux-recipes.tar.gz','w:gz') as archive:
        def filtered(info):
            relative=Path(info.name).parts[1:]
            if relative and relative[0] in ('output','.git'):return None
            info.uid=info.gid=0;info.uname=info.gname='';return info
        archive.add(recipes,arcname='termux-packages',filter=filtered)
    inputs=output/'source-inputs'; inputs.mkdir()
    for folder in sorted(cache.iterdir()):
        if not (folder/'cache').is_dir():continue
        dest=inputs/folder.name;dest.mkdir()
        for path in (folder/'cache').iterdir():
            # keyutil is a general-purpose host tool for an unused Java keystore subpackage.
            if path.is_file() and path.suffix!='.jar':shutil.copyfile(path,dest/path.name)
        for name in ('config.log','config.status','CMakeCache.txt'):
            path=folder/'build'/name
            if path.is_file():
                target=output/'configuration'/folder.name/name;target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(path,target)
    cert=Path('/data/data/com.termux/files/usr/etc/tls/cert.pem')
    (inputs/'ca-certificates').mkdir(exist_ok=True)
    shutil.copyfile(cert,inputs/'ca-certificates/cacert-2025-08-12.pem')
    host=subprocess.check_output(['dpkg-query','-W','-f=${Package}\t${Version}\n'],text=True)
    (output/'host-packages.tsv').write_text(host)
    (output/'toolchain.txt').write_text(subprocess.check_output(['/home/builder/lib/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/clang','--version'],text=True))
    files={str(p.relative_to(output)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.rglob('*')) if p.is_file()}
    (output/'SHA256.json').write_text(json.dumps(files,indent=2)+'\n')
    print(json.dumps({'capturedFiles':len(files),'bytes':sum(p.stat().st_size for p in output.rglob('*') if p.is_file())}))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--recipes',type=Path,default=Path('/opt/moami-native/termux-packages'))
    p.add_argument('--cache',type=Path,default=Path('/home/builder/.termux-build'))
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();collect(a.recipes,a.cache,a.output)
