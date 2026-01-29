package com.velp.common.constants;

public final class AppConstants {

    private AppConstants() {}

    public static final class TaskStatus {
        public static final String PENDING = "pending";
        public static final String PROCESSING = "processing";
        public static final String COMPLETED = "completed";
        public static final String FAILED = "failed";
    }

    public static final class Translation {
        public static final String PROVIDER_DOUBAO = "doubao";
        public static final String PROVIDER_DEEPSEEK = "deepseek";
        public static final String PROVIDER_LLM = "llm";
        public static final String PROVIDER_OPENAI = "openai";
        public static final String SERVICE_DOUBAO = "doubaoTranslationService";
        public static final String SERVICE_DEEPSEEK = "deepseekTranslationService";
        public static final String SERVICE_LLM = "llmTranslationService";
        
        public static final String ROLE_USER = "user";
        public static final String ROLE_SYSTEM = "system";
        public static final String TYPE_INPUT_TEXT = "input_text";
    }

    public static final class Storage {
        public static final String DEFAULT_PATH = "downloads";
        public static final String VIDEO_PREFIX = "video.";
        public static final String VTT_EXT = ".vtt";
        public static final String JSON_EXT = ".json";
        public static final String MP4_EXT = ".mp4";
        public static final String SUBS_JSON = "subs.json";
        public static final String EN_SUB_MARK = ".en";
        public static final String ZH_SUB_MARK = ".zh";
    }

    public static final class YtDlp {
        public static final String FORMAT_BEST = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]";
        public static final String SUB_LANGS = "en,zh-Hans,zh-Hant";
        public static final String EXTRACTOR_ARGS_YT = "youtube:player_client=android";
        public static final String REFERER_YT = "https://www.youtube.com/";
        public static final String OUTPUT_TEMPLATE_BASE = "video.%(ext)s";
        public static final String DOWNLOAD_MARK = "[download]";
        public static final String PERCENT_MARK = "%";
    }

    public static final class Subtitle {
        public static final String WEBVTT_HEADER = "WEBVTT";
        public static final String TIME_SEPARATOR = "-->";
        public static final String BOM = "\uFEFF";
    }

    public static final class Messages {
        public static final String INIT = "正在初始化...";
        public static final String DOWNLOADING_YT = "正在从 YouTube 下载视频和字幕...";
        public static final String DOWNLOADING_PROGRESS = "正在下载: ";
        public static final String PARSING_SUBS = "正在解析字幕文件...";
        public static final String PARSE_COMPLETE = "解析完成";
        public static final String TRANSLATION_STARTED = "检测到缺失中文，正在调用 AI 进行智能翻译...";
        public static final String TRANSLATION_PROGRESS = "正在翻译字幕: ";
        public static final String TRANSLATION_COMPLETE = "翻译完成，正在生成课程...";
        public static final String TRANSLATION_FAILED = "翻译失败 (余额不足)，将使用原文字幕...";
        public static final String TASK_SUBMITTED = "Task submitted";
    }
}
