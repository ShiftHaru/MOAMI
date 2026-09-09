"""Fetch hash-pinned upstream sources. Does NOT authorize APK distribution."""
from pathlib import Path
import hashlib
import json
import urllib.request


def main():
    root = Path(__file__).resolve().parents[1]
    manifest = json.loads((root / 'third_party/source-acquisition.json').read_text(encoding='utf-8'))
    out = root / '.local/release-sources'
    out.mkdir(parents=True, exist_ok=True)
    for item in manifest['artifacts']:
        name = item['file']
        if Path(name).name != name or not item['url'].startswith('https://'):
            raise ValueError('Invalid source manifest entry')
        target = out / name
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == item['sha256']:
            print(name, 'verified'); continue
        with urllib.request.urlopen(item['url'], timeout=45) as response:
            data = response.read(item['bytes'] + 1)
        if len(data) != item['bytes'] or hashlib.sha256(data).hexdigest() != item['sha256']:
            raise ValueError('Source size/hash mismatch: ' + name)
        target.write_bytes(data)
        print(name, 'verified')
    print('Acquisition receipts verified. For 0.33.3 use BUILDING_SOURCE_RELEASE.md; these downloads alone do not cover old 0.33.2 APKs.')


if __name__ == '__main__':
    main()
