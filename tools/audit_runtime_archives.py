"""Inventory local artifacts and nested notices; does not decide redistribution rights."""
import argparse
import hashlib
import io
import json
import re
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET


def pom_declarations(artifact):
    """Read exact cached POM parents. Missing or ambiguous metadata stays unresolved."""
    cache = artifact.parents[4]
    coordinate = ':'.join((artifact.parents[3].name, artifact.parents[2].name, artifact.parents[1].name))
    chain = []
    seen = set()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    while coordinate not in seen and len(chain) < 10:
        seen.add(coordinate)
        poms = list(cache.joinpath(*coordinate.split(':')).glob('*/*.pom'))
        if len(poms) != 1:
            return {'chain': chain, 'status': 'missing-or-ambiguous-parent', 'unresolved': coordinate}
        raw = poms[0].read_bytes()
        root = ET.fromstring(raw)
        licenses = [{tag: entry.findtext('m:' + tag, default='', namespaces=ns)
                     for tag in ('name', 'url')} for entry in root.findall('m:licenses/m:license', ns)]
        chain.append({'coordinate': coordinate, 'sha256': hashlib.sha256(raw).hexdigest(),
                      'projectUrl': root.findtext('m:url', default='', namespaces=ns),
                      'scmUrl': root.findtext('m:scm/m:url', default='', namespaces=ns),
                      'declaredLicenses': licenses})
        if licenses:
            return {'chain': chain, 'status': 'declaration-found', 'licenseProvider': coordinate}
        parent = root.find('m:parent', ns)
        if parent is None:
            return {'chain': chain, 'status': 'no-declaration'}
        parts = [parent.findtext('m:' + tag, default='', namespaces=ns)
                 for tag in ('groupId', 'artifactId', 'version')]
        if any(not re.fullmatch(r'[A-Za-z0-9_.-]+', part) for part in parts):
            return {'chain': chain, 'status': 'unresolved-parent-expression'}
        coordinate = ':'.join(parts)
    return {'chain': chain, 'status': 'parent-cycle-or-depth-limit'}


def inspect_archive(data, prefix='', depth=0):
    notices, metadata, native = [], [], []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            name = entry.filename
            label = prefix + name
            basename = name.rsplit('/', 1)[-1].lower()
            if not entry.is_dir() and (basename.startswith(('license', 'licence', 'copying', 'notice', 'copyright'))
                                       or ('licenses' in name.lower().split('/')[:-1] and not name.endswith('.json'))):
                raw = archive.read(entry)
                notices.append({'path': label, 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})
            if name.endswith('.dist-info/METADATA'):
                text = archive.read(entry).decode('utf-8', errors='replace')
                fields = {}
                for line in text.splitlines():
                    if not line:
                        break
                    key, separator, value = line.partition(': ')
                    if separator and key in ('Name', 'Version', 'License', 'License-Expression'):
                        fields[key] = value
                metadata.append({'path': label, 'fields': fields})
            if re.search(r'\.so(?:\.\d+)*$', name) and not name.endswith('.zip.so'):
                native.append(label)
            if depth < 3 and (name.endswith(('.jar', '.zip', '.zip.so')) or basename == 'ytdlp'):
                child = archive.read(entry)
                if zipfile.is_zipfile(io.BytesIO(child)):
                    result = inspect_archive(child, label + '!/', depth + 1)
                    notices.extend(result['notices'])
                    metadata.extend(result['pythonMetadata'])
                    native.extend(result['nativeEntries'])
    return {'notices': notices, 'pythonMetadata': metadata, 'nativeEntries': native}


def audit(inventory, apk):
    artifacts = []
    for entry in json.loads(inventory.read_text(encoding='utf-8')):
        path = Path(entry['file'])
        raw = path.read_bytes()
        artifacts.append({k: v for k, v in entry.items() if k != 'file'} | {
            'filename': path.name, 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
            'pom': pom_declarations(path),
            **inspect_archive(raw)})
    raw = apk.read_bytes()
    return {'scope': 'Resolved Gradle artifacts and APK archive entries; not a complete license audit or proof of DEX inclusion',
            'artifacts': artifacts,
            'apk': {'filename': apk.name, 'sha256': hashlib.sha256(raw).hexdigest(), **inspect_archive(raw)}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    parser.add_argument('apk', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    report = audit(args.inventory, args.apk)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'artifacts': len(report['artifacts']),
                      'coordinates': len({a['coordinate'] for a in report['artifacts']}),
                      'apkNotices': len(report['apk']['notices']),
                      'apkPythonMetadata': len(report['apk']['pythonMetadata'])}))
