"""Photo/carousel adapter around the bundled, pinned Instagram extractor.

No user credentials or browser cookies. Only requested-post product media is kept.
"""
import json
import re
import sys
from urllib.parse import urlsplit


def allowed_url(value):
    try:
        u = urlsplit(value)
        return (u.scheme == 'https' and u.hostname is not None and u.username is None
                and u.password is None and u.port is None
                and u.hostname.endswith(('.cdninstagram.com', '.fbcdn.net')))
    except (ValueError, TypeError):
        return False


def normalize(product, shortcode):
    items = product.get('carousel_media') or [product]
    if not isinstance(items, list) or not 1 <= len(items) <= 20:
        raise ValueError('unsupported-media-count')
    media = []
    for index, raw in enumerate(items):
        photos = [p for p in (raw.get('image_versions2') or {}).get('candidates', [])
                  if allowed_url(p.get('url')) and isinstance(p.get('width'), int)
                  and isinstance(p.get('height'), int) and p['width'] > 0 and p['height'] > 0]
        photo = max(photos, key=lambda p: p['width'] * p['height'], default={})
        kind = 'video' if raw.get('media_type') == 2 or raw.get('video_versions') else 'photo'
        item = {'id': f'ig-{shortcode}-{index + 1}', 'kind': kind, 'sourceIndex': index + 1,
                'thumbnail': photo.get('url', ''), 'provider': 'Instagram'}
        if kind == 'photo':
            if not photo:
                raise ValueError('extraction-failed')
            item.update(url=photo['url'], expectedWidth=photo['width'], expectedHeight=photo['height'])
        else:
            formats = []
            for v in raw.get('video_versions', []):
                if not allowed_url(v.get('url')):
                    continue
                formats.append({'url': v['url'], 'width': v.get('width') or 0, 'height': v.get('height') or 0,
                                'format_id': str(len(formats)), 'ext': 'mp4', 'protocol': 'https'})
            if not formats or len(formats) > 6:
                raise ValueError('extraction-failed')
            best = max(formats, key=lambda f: f['width'] * f['height'])
            item.update(formats=formats, expectedWidth=best['width'], expectedHeight=best['height'])
        media.append(item)
    return {'postId': shortcode, 'provider': 'Instagram', 'media': media}


def extract(runtime_path, url):
    u = urlsplit(url)
    match = re.fullmatch(r'/(?:p|reel)/([A-Za-z0-9_-]{1,28})/', u.path)
    if u.scheme != 'https' or u.netloc != 'www.instagram.com' or not match or u.query or u.fragment:
        raise ValueError('invalid-post-url')
    sys.path.insert(0, runtime_path)
    from yt_dlp import YoutubeDL
    from yt_dlp.extractor.instagram import InstagramIE
    from yt_dlp.version import __version__
    if __version__ != '2026.08.19':
        raise ValueError('runtime-version-mismatch')

    class QuietLogger:
        def debug(self, message): pass
        def warning(self, message): pass
        def error(self, message): pass

    class MediaIE(InstagramIE):
        product = None
        def _extract_product(self, product_info, video_id=None, get_comments=False):
            self.product = product_info[0] if isinstance(product_info, list) else product_info
            # _real_extract checks for video formats even for a photo. No download uses this placeholder.
            return {'id': video_id, 'formats': [{'format_id': 'product-captured'}]}

    with YoutubeDL({'quiet': True, 'no_warnings': True, 'cachedir': False,
                    'socket_timeout': 15, 'retries': 1, 'logger': QuietLogger()}, auto_init=False) as ydl:
        extractor = MediaIE(ydl)
        extractor.initialize()
        extractor._real_extract(url)
        if not extractor.product:
            raise ValueError('extraction-failed')
        return normalize(extractor.product, match.group(1))


def error_category(error):
    message = str(error).lower()
    if "isn't available to everyone" in message or "can't be seen by certain audiences" in message:
        return 'audience-restricted'
    if '429' in message or 'rate-limit' in message:
        return 'rate-limited'
    if 'login' in message or 'logged-in' in message or 'registered users' in message:
        return 'authentication-required'
    if '404' in message or 'not found' in message:
        return 'unavailable'
    if 'timed out' in message:
        return 'network-timeout'
    return 'extraction-failed'


if __name__ == '__main__':
    try:
        result = extract(sys.argv[1], sys.argv[2])
    except Exception as error:
        result = {'error': error_category(error)}
    print(json.dumps(result, ensure_ascii=True))
