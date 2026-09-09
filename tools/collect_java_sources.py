"""Collect versioned Maven source JARs and sanitized artifact provenance."""
import concurrent.futures
import hashlib
import json
import urllib.request
from pathlib import Path


def collect(item):
    group, name, version = item['coordinate'].split(':')
    filename = f'{name}-{version}-sources.jar'
    base = 'https://dl.google.com/dl/android/maven2/' if group.startswith(('androidx.', 'com.google.android.material', 'com.android.')) else 'https://repo.maven.apache.org/maven2/'
    url = base + group.replace('.', '/') + '/' + name + '/' + version + '/' + filename
    target = Path('.local/release-sources/java') / (group + '-' + filename)
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        target.write_bytes(urllib.request.urlopen(url, timeout=60).read())
    return dict(coordinate=item['coordinate'], binarySha256=hashlib.sha256(Path(item['file']).read_bytes()).hexdigest(),
                sourceFile=target.name, sourceUrl=url, sourceSha256=hashlib.sha256(target.read_bytes()).hexdigest())


if __name__ == '__main__':
    items=json.loads(Path('app/build/reports/runtime-inventory.json').read_text(encoding='utf-8'))
    result=[]; failures=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        pending={pool.submit(collect,item):item for item in items}
        for task in concurrent.futures.as_completed(pending):
            try: result.append(task.result())
            except Exception as error: failures.append(dict(coordinate=pending[task]['coordinate'], error=type(error).__name__))
    result.sort(key=lambda r:r['coordinate'])
    Path('.local/release-sources/java-manifest.json').write_text(json.dumps(dict(artifacts=result, failures=failures),indent=2)+'\n')
    print(json.dumps(dict(acquired=len(result), failures=failures)))
    if failures: raise SystemExit(1)
