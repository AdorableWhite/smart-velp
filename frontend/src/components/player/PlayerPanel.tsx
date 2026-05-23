import { useEffect, useRef, useState } from 'react';
import { ThemeIconButton } from '../layout/ThemeIconButton';
import { exportRenderedVideo } from '../../services/storage/renderedVideoExport';
import { usePreferencesStore } from '../../store/usePreferencesStore';
import type { DisplaySubtitle, SubtitleMode } from '../../types/media';

interface PlayerPanelProps {
  title: string;
  videoSrc?: string;
  subtitles: DisplaySubtitle[];
  onRetranslate?: () => Promise<void> | void;
  isRetranslating?: boolean;
  canRetranslate?: boolean;
  sourceLang?: string;
  targetLang?: string;
}

const rates = [0.75, 1, 1.25, 1.5, 2];
const languageNames: Record<string, string> = {
  en: '英语',
  zh: '中文',
  'zh-CN': '简体中文',
  'zh-Hans': '简体中文',
  'zh-Hant': '繁体中文',
  ja: '日语',
  ko: '韩语',
  fr: '法语',
  de: '德语',
  es: '西班牙语'
};

function subtitleText(mode: SubtitleMode, subtitle: DisplaySubtitle) {
  if (mode === 'source') {
    return { source: subtitle.sourceText, target: '' };
  }
  if (mode === 'target') {
    return { source: '', target: subtitle.targetText };
  }
  if (mode === 'hidden') {
    return { source: '', target: '' };
  }
  return { source: subtitle.sourceText, target: subtitle.targetText };
}

function languageName(code?: string) {
  if (!code) {
    return '未知语言';
  }
  return languageNames[code] ?? code;
}

function safeFileName(title: string) {
  return `${title || 'video'}.mp4`;
}

export function PlayerPanel({
  title,
  videoSrc,
  subtitles,
  onRetranslate,
  isRetranslating = false,
  canRetranslate = false,
  sourceLang,
  targetLang
}: PlayerPanelProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const subtitleListRef = useRef<HTMLDivElement | null>(null);
  const [currentIndex, setCurrentIndex] = useState(-1);
  const [exportProgress, setExportProgress] = useState<number | undefined>();
  const [exportError, setExportError] = useState<string>();
  const [exportMessage, setExportMessage] = useState<string>();
  const [exportController, setExportController] = useState<AbortController>();
  const subtitleMode = usePreferencesStore((state) => state.subtitleMode);
  const setSubtitleMode = usePreferencesStore((state) => state.setSubtitleMode);
  const playbackRate = usePreferencesStore((state) => state.playbackRate);
  const setPlaybackRate = usePreferencesStore((state) => state.setPlaybackRate);
  const fontSize = usePreferencesStore((state) => state.fontSize);
  const setFontSize = usePreferencesStore((state) => state.setFontSize);
  const loopCurrentLine = usePreferencesStore((state) => state.loopCurrentLine);
  const setLoopCurrentLine = usePreferencesStore((state) => state.setLoopCurrentLine);

  const languageDirection = `${languageName(sourceLang)} → ${languageName(targetLang)}`;
  const isExporting = exportProgress !== undefined;
  const activeSubtitle = currentIndex >= 0 ? subtitles[currentIndex] : undefined;
  const activeVisibleSubtitle = activeSubtitle ? subtitleText(subtitleMode, activeSubtitle) : undefined;

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate]);

  const seekToSubtitle = (index: number) => {
    const player = videoRef.current;
    if (!player) {
      return;
    }

    player.currentTime = subtitles[index].startTime;
    void player.play();
  };

  const onTimeUpdate = () => {
    const player = videoRef.current;
    if (!player) {
      return;
    }

    const now = player.currentTime;
    const index = subtitles.findIndex((subtitle) => now >= subtitle.startTime && now < subtitle.endTime);

    if (loopCurrentLine && currentIndex >= 0) {
      const active = subtitles[currentIndex];
      if (now >= active.endTime - 0.1) {
        player.currentTime = active.startTime;
        return;
      }
    }

    if (index !== currentIndex) {
      setCurrentIndex(index);

      if (index >= 0 && subtitleListRef.current) {
        const target = subtitleListRef.current.querySelector<HTMLElement>(`[data-subtitle-index="${index}"]`);
        target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
    }
  };

  const onExport = async () => {
    if (isExporting) {
      exportController?.abort();
      return;
    }
    if (!videoSrc) {
      return;
    }

    const player = videoRef.current;
    const wasPaused = player?.paused ?? true;
    if (player) {
      player.pause();
    }

    setExportError(undefined);
    setExportMessage(undefined);
    setExportProgress(0);
    const controller = new AbortController();
    setExportController(controller);
    try {
      await exportRenderedVideo({
        videoSrc,
        fileName: safeFileName(title),
        subtitles,
        subtitleMode,
        fontSize,
        playbackRate,
        signal: controller.signal,
        onProgress: setExportProgress
      });
      setExportMessage('字幕版视频已生成');
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        setExportMessage('导出已取消');
      } else {
        setExportError(error instanceof Error ? error.message : '导出失败');
      }
    } finally {
      setExportProgress(undefined);
      setExportController(undefined);
      if (player && !wasPaused) {
        void player.play();
      }
    }
  };

  return (
    <section className="study-shell">
      <div className="player-header player-header--compact">
        <div>
          <h1>{title}</h1>
          <p className="subtle-text">{languageDirection}</p>
        </div>
      </div>

      <div className="player-grid">
        <div className="player-main-column">
          <div className="card video-stage" style={{ ['--subtitle-font-size' as string]: `${fontSize}px` }}>
            {videoSrc ? (
              <video
                ref={videoRef}
                src={videoSrc}
                controls
                controlsList="nodownload"
                className="study-video"
                onTimeUpdate={onTimeUpdate}
              />
            ) : (
              <div className="study-video study-video--placeholder">
                <div>
                  <p className="eyebrow">Subtitle Session</p>
                  <h3>当前内容没有视频文件</h3>
                  <p className="subtle-text">这次会话来自字幕重译任务，可以直接点击右侧句子进行精读。</p>
                </div>
              </div>
            )}
            {activeVisibleSubtitle && (activeVisibleSubtitle.source || activeVisibleSubtitle.target) ? (
              <div className="subtitle-overlay" aria-live="polite">
                {activeVisibleSubtitle.source ? <span>{activeVisibleSubtitle.source}</span> : null}
                {activeVisibleSubtitle.target ? <span className="subtitle-overlay-target">{activeVisibleSubtitle.target}</span> : null}
              </div>
            ) : null}
          </div>

          <div className="card learning-controls">
            <div className="learning-controls-head">
              <div>
                <p className="eyebrow">课程操作</p>
                <h3>{languageDirection}</h3>
              </div>
              <div className="icon-actions">
                <ThemeIconButton />
                {videoSrc ? (
                  <button
                    type="button"
                    className="icon-button"
                    title={isExporting ? '取消导出' : '按当前字幕设置导出视频'}
                    aria-label={isExporting ? '取消导出' : '按当前字幕设置导出视频'}
                    onClick={() => void onExport()}
                  >
                    {isExporting ? '×' : '↓'}
                  </button>
                ) : null}
                {onRetranslate ? (
                  <button
                    type="button"
                    className="icon-button"
                    title="重新翻译当前的课程"
                    aria-label="重新翻译当前的课程"
                    disabled={!canRetranslate || isRetranslating}
                    onClick={() => void onRetranslate()}
                  >
                    译
                  </button>
                ) : null}
              </div>
            </div>

            {isExporting ? (
              <div className="export-progress">
                <span style={{ width: `${Math.max(4, Math.round((exportProgress ?? 0) * 100))}%` }} />
                <small>正在按当前字幕、字号和 {playbackRate}x 倍速导出 {Math.round((exportProgress ?? 0) * 100)}%，再次点击下载按钮可取消</small>
              </div>
            ) : null}
            {exportMessage ? <p className="test-result ok">{exportMessage}</p> : null}
            {exportError ? <p className="test-result failed">{exportError}</p> : null}

            <div className="segmented">
              {[
                { key: 'dual', label: '双语' },
                { key: 'source', label: '原文' },
                { key: 'target', label: '译文' },
                { key: 'hidden', label: '隐藏' }
              ].map((item) => (
                <button
                  key={item.key}
                  type="button"
                  className={subtitleMode === item.key ? 'active' : ''}
                  onClick={() => setSubtitleMode(item.key as SubtitleMode)}
                >
                  {item.label}
                </button>
              ))}
            </div>

            <label className="field compact-field">
              <span>字幕字号：{fontSize}px</span>
              <input
                type="range"
                min="16"
                max="34"
                value={fontSize}
                onChange={(event) => setFontSize(Number(event.target.value))}
              />
            </label>

            <div className="speed-row" aria-label="视频播放倍速">
              {rates.map((rate) => (
                <button
                  key={rate}
                  type="button"
                  className={`chip-button${playbackRate === rate ? ' active' : ''}`}
                  onClick={() => setPlaybackRate(rate)}
                >
                  {rate}x
                </button>
              ))}
            </div>

            <button
              type="button"
              className={`secondary-button loop-button${loopCurrentLine ? ' active' : ''}`}
              onClick={() => setLoopCurrentLine(!loopCurrentLine)}
            >
              {loopCurrentLine ? '已开启句子循环' : '开启句子循环'}
            </button>
          </div>
        </div>

        <div className="card subtitle-panel">
          <div className="subtitle-panel-head">
            <div>
              <p className="eyebrow">字幕流</p>
              <h3>句子级对齐</h3>
            </div>
            <span>{subtitles.length} 句</span>
          </div>

          <div className="subtitle-list" ref={subtitleListRef}>
            {subtitles.map((subtitle, index) => {
              const visible = subtitleText(subtitleMode, subtitle);
              return (
                <button
                  key={subtitle.id}
                  type="button"
                  data-subtitle-index={index}
                  className={`subtitle-item${currentIndex === index ? ' active' : ''}`}
                  onClick={() => seekToSubtitle(index)}
                >
                  {visible.source ? <span className="subtitle-source">{visible.source}</span> : null}
                  {visible.target ? <span className="subtitle-target">{visible.target}</span> : null}
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
