"""Check tracked files and reachable Git objects without printing matched secrets.

Run before publication. This pattern check is not a security certification.
Third-party copyright attribution must remain intact.
"""
import os
from pathlib import Path
import re
import subprocess


def git(*args):
    return subprocess.check_output(['git', *args])


def private_patterns():
    patterns = [rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
                rb'gh[pousr]_[A-Za-z0-9]{30,}', rb'AKIA[0-9A-Z]{16}',
                rb'(?i)[A-Z]:[\\/]+Users[\\/]+(?!Public\b|Default\b)[^\s<>"\r\n]+']
    # Local identity is used as a search term, never written into reports.
    identities = [Path.home().name, os.environ.get('PUBLIC_AUDIT_PRIVATE_EMAIL', '')]
    result = subprocess.run(['git', 'config', '--global', '--get', 'user.email'], capture_output=True)
    if result.returncode == 0:
        identities.append(result.stdout.decode().strip())
    signing = Path(os.environ.get('BROWSERDOWNLOADER_SIGNING_PROPERTIES',
                                 str(Path.home() / 'keyStore/signing.properties')))
    if signing.is_file():
        # Only compare secret values; never report them or their source path.
        for line in signing.read_text(encoding='utf-8-sig').splitlines():
            match = re.match(r'\s*(?:storePassword|keyPassword)\s*[=:]\s*(.+)', line)
            if match:
                patterns.append(re.escape(match[1].encode()))
    patterns.extend(re.escape(v.encode()) for v in identities if len(v) >= 4 and not v.endswith('.invalid'))
    return patterns


def violations(data, patterns):
    return any(re.search(pattern, data) for pattern in patterns)


def check():
    patterns = private_patterns()
    failures = []
    paths = git('ls-files', '-z').decode().split('\0')
    forbidden = re.compile(r'(?i)(?:^|/)(?:\.local|\.agents|build)/|\.(?:apk|aab|jks|keystore|p12|pfx|pem|key)$|(?:^|/)(?:signing\.properties|local\.properties|\.env(?:\..*)?)$')
    for path in filter(None, paths):
        if forbidden.search(path) or (Path(path).is_file() and violations(Path(path).read_bytes(), patterns)):
            failures.append('tracked file: ' + path)
    count = 0
    for row in git('rev-list', '--objects', '--all').decode().splitlines():
        oid, _, path = row.partition(' ')
        kind = git('cat-file', '-t', oid).decode().strip()
        if kind not in ('blob', 'commit', 'tag'):
            continue
        count += 1
        data = git('cat-file', kind, oid)
        if (path and forbidden.search(path)) or violations(data, patterns):
            failures.append('history ' + kind + ': ' + oid[:12])
        if kind == 'commit':
            for identity in re.findall(rb'^(?:author|committer) (.+?)> ', data.split(b'\n\n', 1)[0], re.M):
                if identity != b'MOAMI contributors <contributors@moami.invalid':
                    failures.append('non-anonymous commit: ' + oid[:12])
    if failures:
        raise SystemExit('\n'.join(sorted(set(failures))))
    print('PASS: tracked files and', count, 'reachable content/metadata objects; patterns only.')


if __name__ == '__main__':
    check()
