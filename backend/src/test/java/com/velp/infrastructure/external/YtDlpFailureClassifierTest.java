package com.velp.infrastructure.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpFailureClassifierTest {

    @Test
    void identifiesCookieOrLoginRequirement() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: Sign in to confirm you're not a bot. Use --cookies-from-browser"))
                .contains("Cookie");
    }

    @Test
    void identifiesPrivateVideos() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: Private video"))
                .contains("私有");
    }

    @Test
    void identifiesRegionRestriction() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: The uploader has not made this video available in your country"))
                .contains("地区限制");
    }

    @Test
    void identifiesFormatFailures() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: requested format is not available"))
                .contains("格式");
    }

    @Test
    void identifiesNetworkTimeouts() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: timed out while downloading"))
                .contains("网络");
    }

    @Test
    void identifiesUnsupportedUrls() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: Unsupported URL: https://example.com/video"))
                .contains("完整有效");
    }

    @Test
    void identifiesForbiddenRequests() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: HTTP Error 403: Forbidden"))
                .contains("拒绝");
    }

    @Test
    void identifiesRateLimits() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: HTTP Error 429: Too Many Requests"))
                .contains("频率限制");
    }

    @Test
    void identifiesNotFoundResponses() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: HTTP Error 404: Not Found"))
                .contains("404");
    }

    @Test
    void identifiesTemporaryUpstreamFailures() {
        assertThat(YtDlpFailureClassifier.classify("ERROR: Unable to download webpage: HTTP Error 503: Service Unavailable"))
                .contains("页面获取失败");
    }
}
