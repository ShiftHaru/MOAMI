import hashlib
import struct
import unittest
import zlib
from fixture_server import IMAGES, PAGE, png


class FixtureTest(unittest.TestCase):
    def test_png_sources_decode_to_the_declared_dimensions(self):
        for name, (width, height, color) in IMAGES.items():
            with self.subTest(name=name):
                data = png(width, height, color)
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                pos, compressed = 8, b""
                while pos < len(data):
                    length = struct.unpack("!I", data[pos:pos+4])[0]
                    kind = data[pos+4:pos+8]
                    content = data[pos+8:pos+8+length]
                    crc = struct.unpack("!I", data[pos+8+length:pos+12+length])[0]
                    self.assertEqual(crc, zlib.crc32(kind+content))
                    if kind == b"IHDR":
                        self.assertEqual(struct.unpack("!II", content[:8]), (width,height))
                    if kind == b"IDAT":
                        compressed += content
                    pos += 12+length
                self.assertEqual(len(zlib.decompress(compressed)), height*(width*3+1))

    def test_thumbnail_is_not_identical_to_original(self):
        self.assertNotEqual(hashlib.sha256(png(*IMAGES['original.png'])).digest(),
                            hashlib.sha256(png(*IMAGES['thumbnail.png'])).digest())

    def test_fixture_exercises_late_loading_without_a_collector_bridge(self):
        self.assertIn('IntersectionObserver', PAGE)
        self.assertIn("observer.disconnect()", PAGE)
        self.assertNotIn('navigator.share', PAGE)
        self.assertNotIn('addJavascriptInterface', PAGE)


if __name__ == '__main__':
    unittest.main()
