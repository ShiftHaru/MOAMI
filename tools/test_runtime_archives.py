import io
import unittest
import zipfile
import tempfile
from pathlib import Path
from audit_runtime_archives import inspect_archive, pom_declarations


def archive(entries):
    result = io.BytesIO()
    with zipfile.ZipFile(result, 'w') as output:
        for name, content in entries.items():
            output.writestr(name, content)
    return result.getvalue()


class RuntimeArchiveTest(unittest.TestCase):
    def test_finds_nested_notices_without_treating_library_zip_as_elf(self):
        inner = archive({'pkg.dist-info/METADATA': 'Name: example\nVersion: 1.2\n\nbody',
                         'pkg.dist-info/LICENSE': 'license text', 'lib/native.so.1.2': b'ELF'})
        result = inspect_archive(archive({'lib/x86_64/libpython.zip.so': inner, 'assets/licenses/example.txt': 'notice'}))
        self.assertEqual(result['pythonMetadata'][0]['fields'], {'Name': 'example', 'Version': '1.2'})
        self.assertEqual(result['notices'][0]['path'], 'lib/x86_64/libpython.zip.so!/pkg.dist-info/LICENSE')
        self.assertEqual(result['nativeEntries'], ['lib/x86_64/libpython.zip.so!/lib/native.so.1.2'])
        self.assertEqual(result['notices'][1]['path'], 'assets/licenses/example.txt')
        self.assertEqual(len(result['notices'][0]['sha256']), 64)

    def test_resolves_declared_parent_without_inventing_missing_license(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory)
            child = cache / 'example/lib/1/hash/lib.jar'
            child.parent.mkdir(parents=True)
            (child.parent/'lib.pom').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><groupId>example</groupId><artifactId>parent</artifactId><version>2</version></parent></project>')
            self.assertEqual(pom_declarations(child)['status'], 'missing-or-ambiguous-parent')
            parent = cache/'example/parent/2/hash/parent.pom'
            parent.parent.mkdir(parents=True)
            parent.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses><license><name>Example license</name><url>https://example.test/license</url></license></licenses></project>')
            result = pom_declarations(child)
            self.assertEqual(result['licenseProvider'], 'example:parent:2')
            self.assertEqual(len(result['chain']), 2)
