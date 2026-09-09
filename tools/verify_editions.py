"""Verify signed full/share release APK boundaries without printing certificate identity."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path


def verify(build_tools, full, share):
    reports = []
    certificates = []
    for edition, apk in [('full', full), ('share', share)]:
        badging = subprocess.check_output([str(build_tools / 'aapt.exe'), 'dump', 'badging', str(apk)]).decode('utf-8')
        manifest = subprocess.check_output([str(build_tools / 'aapt.exe'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml']).decode('utf-8')
        package = 'dev.browserdownloader.share' if edition == 'share' else 'dev.browserdownloader.probe'
        assert f"name='{package}' versionCode='35' versionName='0.33.2-{edition}'" in badging
        assert "native-code: 'arm64-v8a'" in badging
        assert 'application-debuggable' not in badging
        assert "android.intent.action.SEND" in manifest
        assert 'android.permission.INTERNET' in manifest
        if edition == 'share':
            assert 'E: service' not in manifest
            assert 'BIND_ACCESSIBILITY_SERVICE' not in manifest
            assert 'ChromeProbeService' not in manifest
            assert 'CompareActivity' not in manifest and 'ResultsActivity' not in manifest
            assert 'SYSTEM_ALERT_WINDOW' not in manifest
            assert re.search(r'usesCleartextTraffic[^\n]*\)0x0\b', manifest)
        else:
            assert 'ChromeProbeService' in manifest and 'BIND_ACCESSIBILITY_SERVICE' in manifest
        signer = subprocess.check_output([str(build_tools / 'apksigner.bat'), 'verify', '--print-certs', str(apk)]).decode('utf-8')
        cert = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', signer)
        assert cert, 'Missing signing certificate digest'
        certificates.append(cert.group(1))
        reports.append(dict(edition=edition, version='0.33.2-' + edition, versionCode=35,
                            package=package, bytes=apk.stat().st_size,
                            sha256=hashlib.sha256(apk.read_bytes()).hexdigest()))
    assert certificates[0] == certificates[1], 'Editions must use the same signing certificate'
    return reports


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--full', type=Path, default=Path('app/build/outputs/apk/release/app-release.apk'))
    parser.add_argument('--share', type=Path, default=Path('app/build/share-only/outputs/apk/release/app-release.apk'))
    args = parser.parse_args()
    print(json.dumps(verify(args.build_tools, args.full, args.share), indent=2))
