package com.velp.common.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class YouTubeUrlNormalizer {

    private YouTubeUrlNormalizer() {}

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        String parseable = withSchemeIfMissing(trimmed);
        try {
            URI uri = URI.create(parseable);
            String host = uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host == null) {
                return trimmed;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String videoId = null;
            if (normalizedHost.equals("youtu.be")) {
                videoId = firstPathPart(path);
            } else if (isYouTubeHost(normalizedHost)) {
                if (path.equals("/watch")) {
                    videoId = queryParam(uri.getRawQuery(), "v");
                } else if (path.startsWith("/shorts/")) {
                    videoId = firstPathPart(path.substring("/shorts".length()));
                } else if (path.startsWith("/embed/")) {
                    videoId = firstPathPart(path.substring("/embed".length()));
                } else if (path.startsWith("/live/")) {
                    videoId = firstPathPart(path.substring("/live".length()));
                }
            }

            if (videoId != null && !videoId.isBlank()) {
                return "https://www.youtube.com/watch?v=" + videoId;
            }
        } catch (Exception ignored) {
            return trimmed;
        }

        return trimmed;
    }

    private static String withSchemeIfMissing(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value;
        }
        if (lower.startsWith("youtube.com/")
                || lower.startsWith("www.youtube.com/")
                || lower.startsWith("m.youtube.com/")
                || lower.startsWith("music.youtube.com/")
                || lower.startsWith("youtu.be/")) {
            return "https://" + value;
        }
        return value;
    }

    private static boolean isYouTubeHost(String host) {
        return host.equals("youtube.com") || host.endsWith(".youtube.com");
    }

    private static String firstPathPart(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String normalized = path.startsWith("/") ? path.substring(1) : path;
        int slash = normalized.indexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : normalized;
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        for (String part : rawQuery.split("&")) {
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            if (name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                String rawValue = equals >= 0 ? part.substring(equals + 1) : "";
                return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
