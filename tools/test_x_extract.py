"""Normalization checks with self-authored metadata; no X requests or credentials."""
import importlib.util
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "xprobe/src/main/assets/x_extract.py"
spec = importlib.util.spec_from_file_location("x_extract", SOURCE)
adapter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(adapter)


class XMediaAdapterTest(unittest.TestCase):
    def test_preserves_photos_and_media_types_without_collecting_quote(self):
        status = {"extended_entities": {"media": [
            {"id_str": "10", "type": "photo", "media_url_https": "https://pbs.twimg.com/media/fixture.jpg",
             "original_info": {"width": 1280, "height": 960}},
            {"id_str": "11", "type": "animated_gif", "original_info": {"width": 400, "height": 300},
             "video_info": {"variants": [{"content_type": "video/mp4", "url": "https://video.twimg.com/tweet_video/fixture.mp4"}]}},
        ]}, "quoted_status": {"extended_entities": {"media": [{"id_str": "99", "type": "photo"}]}}}
        output = adapter.normalize_status(status, "123")
        self.assertEqual(["photo", "animated_gif"], [m["kind"] for m in output["media"]])
        self.assertEqual("https://pbs.twimg.com/media/fixture?format=jpg&name=orig", output["media"][0]["url"])
        self.assertEqual(400, output["media"][1]["formats"][0]["width"])
        self.assertFalse(output["quotedMediaIncluded"])

    def test_keeps_multiple_direct_mp4_variants_and_ignores_hls(self):
        variants = [{"content_type": "video/mp4", "url": f"https://video.twimg.com/vid/{s}/fixture.mp4", "bitrate": b}
                    for s, b in [("320x180", 200000), ("1280x720", 500000)]]
        variants.append({"content_type": "application/x-mpegURL", "url": "https://video.twimg.com/playlist.m3u8"})
        result = adapter.normalize_status({"extended_entities": {"media": [
            {"type": "video", "video_info": {"variants": variants}}]}}, "123")
        self.assertEqual([320, 1280], [f["width"] for f in result["media"][0]["formats"]])

    def test_unknown_photo_dimensions_stay_unverified(self):
        result = adapter.normalize_status({"extended_entities": {"media": [
            {"type": "photo", "media_url_https": "https://pbs.twimg.com/media/fixture?format=png&name=small"}]}}, "123")
        self.assertEqual(0, result["media"][0]["expectedWidth"])
        self.assertTrue(result["media"][0]["thumbnail"].startswith("https://pbs.twimg.com/"))

    def test_video_thumbnail_is_host_checked(self):
        for url, present in [("https://pbs.twimg.com/ext_tw_video_thumb/a.jpg", True),
                             ("https://pbs.twimg.com.evil.test/a.jpg", False)]:
            result = adapter.normalize_status({"extended_entities": {"media": [
                {"type": "video", "media_url_https": url, "video_info": {"variants": []}}]}}, "123")
            self.assertEqual(present, "thumbnail" in result["media"][0])

    def test_rejects_untrusted_photo_authorities(self):
        for url in ("https://pbs.twimg.com.evil.test/media/a.jpg", "https://user@pbs.twimg.com/media/a.jpg",
                    "https://@pbs.twimg.com/media/a.jpg",
                    "http://pbs.twimg.com/media/a.jpg", "https://pbs.twimg.com:443/media/a.jpg",
                    "https://pbs.twimg.com/profile_images/a.jpg"):
            with self.assertRaises(ValueError):
                adapter.original_photo_url(url)


if __name__ == "__main__":
    unittest.main()
