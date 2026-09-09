"""X media adapter for the hash-pinned yt-dlp 2026.08.19 runtime.

Uses its status retrieval so photos and original media types survive normalization.
Only media attached to the requested status is included; quoted posts are separate.
"""
import json
import re
import sys
from urllib.parse import parse_qs, urlencode, urlsplit, urlunsplit


def allowed_url(value, host, prefix):
    if not isinstance(value, str):
        return False
    try:
        parts = urlsplit(value)
        return (parts.scheme == "https" and parts.hostname == host and parts.username is None
                and parts.password is None and parts.port is None and parts.path.startswith(prefix))
    except ValueError:
        return False


def original_photo_url(value):
    if not allowed_url(value, "pbs.twimg.com", "/media/"):
        raise ValueError("unsupported-photo-address")
    parts = urlsplit(value)
    path, dot, extension = parts.path.rpartition(".")
    if not dot or extension not in ("jpg", "jpeg", "png", "webp"):
        extension = parse_qs(parts.query).get("format", [""])[0]
        path = parts.path
    if extension not in ("jpg", "jpeg", "png", "webp"):
        raise ValueError("unsupported-photo-format")
    return urlunsplit(("https", "pbs.twimg.com", path,
                       urlencode({"format": extension, "name": "orig"}), ""))


def normalize_status(status, post_id):
    entries = []
    for index, media in enumerate(status.get("extended_entities", {}).get("media", [])):
        kind = media.get("type", "unknown")
        identifier = str(media.get("id_str") or media.get("id") or f"{post_id}-{index + 1}")
        if not re.fullmatch(r"[0-9-]{1,52}", identifier):
            raise ValueError("invalid-media-identifier")
        original = media.get("original_info") or {}
        item = {"id": identifier, "kind": kind, "sourceIndex": index + 1,
                "expectedWidth": original.get("width", 0),
                "expectedHeight": original.get("height", 0)}
        thumbnail = media.get("media_url_https") or ""
        if allowed_url(thumbnail, "pbs.twimg.com", "/"):
            item["thumbnail"] = thumbnail
        if kind == "photo":
            item["url"] = original_photo_url(media.get("media_url_https") or media.get("media_url"))
            item["qualityEvidence"] = "X orig variant; original_info dimensions require file verification"
        elif kind in ("video", "animated_gif"):
            formats = []
            for variant in media.get("video_info", {}).get("variants", []):
                url = variant.get("url")
                if variant.get("content_type") != "video/mp4" or not allowed_url(url, "video.twimg.com", "/"):
                    continue
                dimensions = re.search(r"/(\d{1,5})x(\d{1,5})/", urlsplit(url).path)
                width, height = map(int, dimensions.groups()) if dimensions else (0, 0)
                formats.append({"format_id": f"mp4-{len(formats) + 1}", "url": url,
                                "width": width, "height": height, "ext": "mp4",
                                "protocol": "https", "tbr": (variant.get("bitrate") or 0) / 1000})
            # X animated GIF often has one MP4 URL without dimensions in its path.
            if kind == "animated_gif" and len(formats) == 1 and not formats[0]["width"]:
                formats[0].update(width=item["expectedWidth"], height=item["expectedHeight"])
            item["formats"] = formats
        else:
            item["unsupported"] = True
        entries.append(item)
    return {"postId": post_id, "extractor": "yt-dlp Twitter status adapter",
            "runtime": "2026.08.19", "media": entries, "quotedMediaIncluded": False}


def extract(runtime_path, url):
    parts = urlsplit(url)
    match = re.fullmatch(r"/(?:[A-Za-z0-9_]{1,15}|i/web)/status/(\d{1,25})(?:/(?:photo|video)/[1-4])?/?", parts.path)
    if parts.scheme != "https" or parts.netloc != "x.com" or not match:
        raise ValueError("invalid-post-url")
    sys.path.insert(0, runtime_path)
    from yt_dlp import YoutubeDL
    from yt_dlp.extractor.twitter import TwitterIE
    from yt_dlp.version import __version__
    if __version__ != "2026.08.19":
        raise ValueError("runtime-version-mismatch")

    class QuietLogger:
        def debug(self, message): pass
        def warning(self, message): pass
        def error(self, message): pass

    with YoutubeDL({"quiet": True, "no_warnings": True, "cachedir": False,
                    "socket_timeout": 15, "retries": 1, "logger": QuietLogger()}, auto_init=False) as ydl:
        extractor = TwitterIE(ydl)
        extractor.initialize()
        # Private upstream API: covered by fixture tests and the live supplied-post test.
        status = extractor._extract_status(match.group(1))
        return normalize_status(status, match.group(1))


if __name__ == "__main__":
    try:
        result = extract(sys.argv[1], sys.argv[2])
    except Exception as error:
        message = str(error).lower()
        category = "extraction-failed"
        if "login" in message or "not authorized" in message:
            category = "authentication-required"
        elif "429" in message:
            category = "rate-limited"
        elif "404" in message or "not found" in message:
            category = "unavailable"
        elif "timed out" in message:
            category = "network-timeout"
        result = {"error": category, "errorClass": type(error).__name__}
    print(json.dumps(result, ensure_ascii=True))
