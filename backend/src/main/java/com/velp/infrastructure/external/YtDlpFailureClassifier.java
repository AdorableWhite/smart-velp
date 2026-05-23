package com.velp.infrastructure.external;

import java.util.Locale;

public final class YtDlpFailureClassifier {

    private YtDlpFailureClassifier() {}

    public static String classify(String output) {
        if (output == null || output.isBlank()) {
            return "YouTube 下载失败：未收到 yt-dlp 错误输出，请检查网络和 yt-dlp 是否可用。";
        }

        String lower = output.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "sign in to confirm", "login required", "cookies", "not a bot", "confirm you're not a bot")) {
            return "YouTube 需要登录态或 Cookie。请在设置环境变量 VELP_YTDLP_COOKIES_PATH 后重试，或显式配置 cookies-from-browser。";
        }
        if (containsAny(lower, "private video", "members-only", "join this channel")) {
            return "该视频是私有/会员限定内容，当前配置无法下载。";
        }
        if (containsAny(lower, "this video is unavailable", "video unavailable", "removed by the uploader", "has been removed")) {
            return "该视频不可用，可能已删除、下架或链接无效。";
        }
        if (containsAny(lower, "not available in your country", "not made this video available in your country",
                "blocked in your country", "geo restricted", "geo-restricted")) {
            return "该视频存在地区限制。可以尝试在网络环境允许的情况下切换出口，或配置可访问该地区的下载环境。";
        }
        if (containsAny(lower, "unsupported url", "no suitable extractor", "not a valid url")) {
            return "该链接不是 yt-dlp 当前支持的视频地址。请确认链接完整有效，或换成 YouTube watch/shorts/youtu.be 链接。";
        }
        if (containsAny(lower, "requested format is not available", "no video formats found", "unable to extract video data")) {
            return "当前视频格式不可用或 YouTube 返回格式异常。系统已尝试多组格式回退，建议更新 yt-dlp 后重试。";
        }
        if (containsAny(lower, "http error 403", "forbidden", "precondition check failed")) {
            return "YouTube 拒绝了当前下载请求，通常与风控、登录态或区域策略有关。请配置 Cookie 后重试。";
        }
        if (containsAny(lower, "http error 429", "too many requests")) {
            return "YouTube 触发了访问频率限制。请稍后重试，或配置 Cookie/更换网络后再试。";
        }
        if (containsAny(lower, "http error 404", "not found")) {
            return "视频地址返回 404，可能链接失效、视频已删除或当前地区不可访问。";
        }
        if (containsAny(lower, "unable to download webpage", "http error 5", "bad gateway", "service unavailable")) {
            return "YouTube 页面获取失败，可能是临时网络或上游服务问题。请稍后重试。";
        }
        if (containsAny(lower, "timed out", "timeout", "connection reset", "connection aborted", "temporary failure", "network is unreachable")) {
            return "网络连接不稳定或超时。请稍后重试，必要时开启 VELP_YTDLP_FORCE_IPV4 或更换网络。";
        }
        if (containsAny(lower, "ffmpeg", "postprocessing", "unable to merge")) {
            return "视频后处理失败，通常与 ffmpeg 或格式合并有关。请检查下载诊断中的 ffmpeg 状态后重试。";
        }
        return "YouTube 下载失败：yt-dlp 已返回错误，详情见任务错误摘要。";
    }

    private static boolean containsAny(String value, String... patterns) {
        for (String pattern : patterns) {
            if (value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
