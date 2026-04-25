import { useEffect, useMemo, useRef, useState } from 'react';
import { usePreferencesStore } from '../../store/usePreferencesStore';
import type { DisplaySubtitle, SubtitleMode } from '../../types/media';

interface PlayerPanelProps {
  title: string;
  videoSrc?: string;
  subtitles: DisplaySubtitle[];
  onDownload?: () => Promise<void> | void;
  sourceLang?: string;
  targetLang?: string;
}

const rates = [0.75, 1, 1.25, 1.5, 2];

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

export function PlayerPanel({
  title,
  videoSrc,
  subtitles,
  onDownload,
  sourceLang,
  targetLang
}: PlayerPanelProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const subtitleListRef = useRef<HTMLDivElement | null>(null);
  const [currentIndex, setCurrentIndex] = useState(-1);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const subtitleMode = usePreferencesStore((state) => state.subtitleMode);
  const setSubtitleMode = usePreferencesStore((state) => state.setSubtitleMode);
  const playbackRate = usePreferencesStore((state) => state.playbackRate);
  const setPlaybackRate = usePreferencesStore((state) => state.setPlaybackRate);
  const fontSize = usePreferencesStore((state) => state.fontSize);
  const setFontSize = usePreferencesStore((state) => state.setFontSize);
  const loopCurrentLine = usePreferencesStore((state) => state.loopCurrentLine);
  const setLoopCurrentLine = usePreferencesStore((state) => state.setLoopCurrentLine);

  const currentSubtitle = currentIndex >= 0 ? subtitles[currentIndex] : undefined;
  const overlay = useMemo(
    () => (currentSubtitle ? subtitleText(subtitleMode, currentSubtitle) : { source: '', target: '' }),
    [currentSubtitle, subtitleMode]
  );

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate]);

  useEffect(() => {
    const onFullscreenChange = () => {
      setIsFullscreen(Boolean(document.fullscreenElement));
    };

    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

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

  const toggleFullscreen = async () => {
    const container = videoRef.current?.closest('.video-stage') as HTMLElement | null;
    if (!container) {
      return;
    }

    if (document.fullscreenElement) {
      await document.exitFullscreen();
      return;
    }

    await container.requestFullscreen();
  };

  return (
    <section className="study-shell">
      <div className="player-header">
        <div>
          <p className="eyebrow">学习模式</p>
          <h1>{title}</h1>
          {(sourceLang || targetLang) ? (
            <p className="subtle-text">
              {sourceLang ?? 'source'} → {targetLang ?? 'target'}
            </p>
          ) : null}
        </div>

        <div className="inline-actions">
          {onDownload ? (
            <button type="button" className="secondary-button" onClick={() => void onDownload()}>
              下载视频
            </button>
          ) : null}
          <button type="button" className="secondary-button" onClick={toggleFullscreen}>
            {isFullscreen ? '退出全屏' : '全屏观看'}
          </button>
        </div>
      </div>

      <div className="player-grid">
        <div className="card video-stage" style={{ ['--subtitle-font-size' as string]: `${fontSize}px` }}>
          {videoSrc ? (
            <video ref={videoRef} src={videoSrc} controls className="study-video" onTimeUpdate={onTimeUpdate} />
          ) : (
            <div className="study-video study-video--placeholder">
              <div>
                <p className="eyebrow">字幕学习</p>
                <h3>当前内容没有视频文件</h3>
                <p className="subtle-text">这次会话来自字幕重译任务，可以直接点击右侧句子进行精读。</p>
              </div>
            </div>
          )}

          {isFullscreen && (overlay.source || overlay.target) ? (
            <div className="subtitle-overlay">
              {overlay.source ? <div>{overlay.source}</div> : null}
              {overlay.target ? <div>{overlay.target}</div> : null}
            </div>
          ) : null}

          <div className="control-cluster">
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

            <div className="control-row">
              <label className="field compact-field">
                <span>字幕字号</span>
                <input
                  type="range"
                  min="16"
                  max="34"
                  value={fontSize}
                  onChange={(event) => setFontSize(Number(event.target.value))}
                />
              </label>

              <button
                type="button"
                className={`secondary-button${loopCurrentLine ? ' active' : ''}`}
                onClick={() => setLoopCurrentLine(!loopCurrentLine)}
              >
                {loopCurrentLine ? '已开启句子循环' : '开启句子循环'}
              </button>
            </div>

            <div className="speed-row">
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
