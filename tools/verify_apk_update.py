"""Install final APK after instrumentation, verify real system rebind without toggling permissions.

Usage: python tools/verify_apk_update.py --adb PATH --serial SERIAL --apk PATH
Never run target-process instrumentation/uiautomator after this delivery check.
"""
import argparse
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--apk', required=True)
a = p.parse_args()
package = 'dev.browserdownloader.probe'

def adb(*args):
    return subprocess.check_output([a.adb, '-s', a.serial, *args], timeout=120).decode('utf-8').strip()

def activation():
    root = ET.fromstring(adb('shell', 'run-as', package, 'cat', 'shared_prefs/probe.xml'))
    return {x.attrib['name']: x.attrib.get('value') for x in root
            if x.attrib.get('name') in ('enabled', 'previewConsentVersion', 'xDrawerConsent', 'galleryConsentVersion', 'chromeDrawer', 'chromeDrawerPosition')}

def state():
    text = adb('shell', 'dumpsys', 'accessibility')
    return {name: next((line.strip() for line in text.splitlines() if line.strip().startswith(name+':')), '')
            for name in ('Bound services', 'Crashed services', 'Binding services', 'Enabled services')}

assert Path(a.apk).is_file()
before = activation()
settings = adb('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
assert package in settings, 'Device must already have user-approved accessibility permission'
assert 'Success' in adb('install', '-r', str(Path(a.apk).resolve()))
deadline = time.monotonic() + 30
while True:
    current = state()
    if package in current['Bound services'] and package not in current['Crashed services'] and package not in current['Binding services']:
        break
    assert time.monotonic() < deadline, 'System failed to reconnect after APK update'
    time.sleep(0.5)
assert before == activation(), 'Update changed user activation/consent'
assert settings == adb('shell', 'settings', 'get', 'secure', 'enabled_accessibility_services'), 'Accessibility grants changed'
print('PASS APK update: system-bound, not crashed/binding; activation and grants preserved; no toggle or instrumentation')
