# YouTube Download Stability

Smart VELP uses `yt-dlp` for YouTube extraction and `ffmpeg` for high quality video/audio merging.

## Runtime Strategy

- `yt-dlp` is resolved from `VELP_YTDLP_PATH`, `.tools/yt-dlp.exe`, or `PATH`.
- `ffmpeg` is resolved from `VELP_FFMPEG_PATH`, `.tools/ffmpeg/**/bin/ffmpeg.exe`, or `PATH`.
- When `ffmpeg` is available, downloads try merged MP4 video/audio first, then progressive MP4, then any best format.
- When `ffmpeg` is unavailable, downloads fall back to progressive MP4, then lower-resolution single-file formats.
- YouTube URL variants are normalized before task reuse: `watch`, `youtu.be`, `shorts`, `embed`, and `live`.
- Download tasks run on a background media executor so the submit API can return quickly while progress is polled.

## Optional Cookies

Cookies are disabled by default. Enable them only when a video requires login, age verification, or regional session state.

Environment options:

```bash
VELP_YTDLP_COOKIES_PATH=/absolute/path/to/cookies.txt
VELP_YTDLP_COOKIES_FROM_BROWSER=chrome
VELP_YTDLP_FORCE_IPV4=true
```

`VELP_YTDLP_COOKIES_PATH` is preferred over browser cookies. The backend diagnostics endpoint shows whether cookies are active.

## Diagnostics

Use this endpoint to verify the active toolchain:

```text
GET /api/parser/download-diagnostics
```

It returns the resolved `yt-dlp` path/version, `ffmpeg` status/path, timeout, cookies status, IPv4 mode, and format fallback chain.

## Failure Categories

The task error shown in the Library is intentionally user-facing. It classifies common `yt-dlp` failures into actionable buckets:

- Login, bot check, or age gate: configure `VELP_YTDLP_COOKIES_PATH` or `VELP_YTDLP_COOKIES_FROM_BROWSER`.
- Private, members-only, removed, or unavailable video: the current account/session cannot access the video.
- Region restriction: retry from a network environment that can access the video's region.
- Unsupported URL: submit a complete YouTube `watch`, `shorts`, `live`, `embed`, or `youtu.be` URL.
- Format extraction failure: update `yt-dlp` and retry; Smart VELP already tries multiple format fallback chains.
- HTTP 403 or precondition failure: YouTube rejected the request, usually due to login/session/risk checks.
- HTTP 429: YouTube rate-limited the current environment; retry later or use cookies.
- HTTP 404: the link is invalid, removed, or unavailable to the current environment.
- Temporary webpage or 5xx failure: retry later before changing settings.
- Network timeout/reset: retry, enable IPv4, or use a more stable network.
- ffmpeg/post-processing failure: verify `ffmpegAvailable` in diagnostics.
