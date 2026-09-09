import importlib.util
import unittest
from pathlib import Path

spec=importlib.util.spec_from_file_location('instagram_extract',Path(__file__).resolve().parents[1]/'xprobe/src/main/assets/instagram_extract.py')
ig=importlib.util.module_from_spec(spec)
spec.loader.exec_module(ig)

class InstagramTest(unittest.TestCase):
    def test_access_restriction_is_not_a_storage_failure(self):
        message = "This content isn't available to everyone: It can't be seen by certain audiences."
        self.assertEqual('audience-restricted', ig.error_category(Exception(message)))
        for message, code in [('HTTP 429', 'rate-limited'), ('login required', 'authentication-required'),
                              ('404 not found', 'unavailable'), ('timed out', 'network-timeout'),
                              ('unknown https://example.org/?secret=test', 'extraction-failed')]:
            self.assertEqual(code, ig.error_category(Exception(message)))
    def test_mixed_carousel_preserves_photos_and_unknown_video_dimensions(self):
        small={'url':'https://s.cdninstagram.com/s.jpg?sig=a','width':20,'height':20}
        large={'url':'https://s.cdninstagram.com/l.jpg?sig=b','width':1200,'height':800}
        photo={'media_type':1,'image_versions2':{'candidates':[small,large]}}
        video={'media_type':2,'image_versions2':{'candidates':[small]},'video_versions':[{'url':'https://s.fbcdn.net/a.mp4?sig=c'}]}
        r=ig.normalize({'carousel_media':[photo,video,photo]},'abc')
        self.assertEqual(['photo','video','photo'],[m['kind'] for m in r['media']])
        self.assertEqual(large['url'],r['media'][0]['url'])
        self.assertEqual(0,r['media'][1]['formats'][0]['width'])
        self.assertEqual(3,len({m['id'] for m in r['media']}))
    def test_reject_untrusted_and_empty_media(self):
        for url in ['http://s.fbcdn.net/a','https://fbcdn.net.evil/a','https://user@s.fbcdn.net/a','https://s.fbcdn.net:443/a']:
            self.assertFalse(ig.allowed_url(url))
        with self.assertRaises(ValueError):ig.normalize({'media_type':1},'a')
        with self.assertRaises(ValueError):ig.normalize({'carousel_media':[{}]*21},'a')

if __name__=='__main__':unittest.main()
