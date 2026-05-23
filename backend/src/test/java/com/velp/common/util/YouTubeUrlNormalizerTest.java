package com.velp.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YouTubeUrlNormalizerTest {

    @Test
    void keepsWatchUrlAndDropsNoiseParams() {
        assertThat(YouTubeUrlNormalizer.normalize("https://www.youtube.com/watch?v=jNQXAC9IVRw&t=5s&list=abc"))
                .isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void normalizesShortUrl() {
        assertThat(YouTubeUrlNormalizer.normalize("https://youtu.be/jNQXAC9IVRw?t=5"))
                .isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void normalizesShortsEmbedAndLiveUrls() {
        assertThat(YouTubeUrlNormalizer.normalize("https://www.youtube.com/shorts/jNQXAC9IVRw"))
                .isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
        assertThat(YouTubeUrlNormalizer.normalize("https://www.youtube.com/embed/jNQXAC9IVRw"))
                .isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
        assertThat(YouTubeUrlNormalizer.normalize("https://www.youtube.com/live/jNQXAC9IVRw?feature=share"))
                .isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void acceptsMobileAndSchemeLessUrls() {
        assertThat(YouTubeUrlNormalizer.normalize("m.youtube.com/watch?v=jNQXAC9IVRw"))
                .isEqualTo("https://www.youtube.com/watch?v=jNQXAC9IVRw");
    }

    @Test
    void leavesNonYoutubeUrlsUntouched() {
        assertThat(YouTubeUrlNormalizer.normalize("https://example.com/video?id=1"))
                .isEqualTo("https://example.com/video?id=1");
    }
}
