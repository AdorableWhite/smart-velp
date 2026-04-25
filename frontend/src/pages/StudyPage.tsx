import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PlayerPanel } from '../components/player/PlayerPanel';
import {
  buildDownloadUrl,
  fetchCourseDetail,
  fetchTaskStatus,
  submitCourseRetranslate,
  submitLocalSubtitleAnalyze
} from '../services/api/media';
import { downloadToDevice } from '../services/storage/download';
import { getImportedMedia, updateImportedMediaSubtitleTask } from '../services/storage/localMedia';
import { getTranslationProfile } from '../services/storage/translationProfiles';
import { usePreferencesStore } from '../store/usePreferencesStore';
import type { DisplaySubtitle } from '../types/media';
import { normalizeBackendSubtitles, parseSubtitleFile } from '../utils/subtitles';

export function StudyPage() {
  const navigate = useNavigate();
  const params = useParams();
  const autoDownload = usePreferencesStore((state) => state.autoDownload);
  const sourceLang = usePreferencesStore((state) => state.sourceLang);
  const targetLang = usePreferencesStore((state) => state.targetLang);
  const selectedProfileId = usePreferencesStore((state) => state.selectedProfileId);
  const autoDownloadedRef = useRef(false);
  const [localState, setLocalState] = useState<{
    title: string;
    videoSrc: string;
    subtitles: DisplaySubtitle[];
    subtitleTaskId?: string;
  }>();

  const taskStatusQuery = useQuery({
    queryKey: ['task-status', params.id],
    queryFn: () => fetchTaskStatus(params.id ?? ''),
    enabled: params.mode === 'backend' && Boolean(params.id),
    refetchInterval: (query) => {
      const status = (query.state.data as { status?: string } | undefined)?.status;
      return status === 'completed' || status === 'failed' ? false : 2_000;
    }
  });

  const courseQuery = useQuery({
    queryKey: ['course', taskStatusQuery.data?.videoId],
    queryFn: () => fetchCourseDetail(taskStatusQuery.data?.videoId ?? ''),
    enabled: params.mode === 'backend' && taskStatusQuery.data?.status === 'completed' && Boolean(taskStatusQuery.data.videoId)
  });

  const localSubtitleStatusQuery = useQuery({
    queryKey: ['local-subtitle-status', localState?.subtitleTaskId],
    queryFn: () => fetchTaskStatus(localState?.subtitleTaskId ?? ''),
    enabled: params.mode === 'local' && Boolean(localState?.subtitleTaskId),
    refetchInterval: (query) => {
      const status = (query.state.data as { status?: string } | undefined)?.status;
      return status === 'completed' || status === 'failed' ? false : 2_000;
    }
  });

  const localSubtitleCourseQuery = useQuery({
    queryKey: ['local-subtitle-course', localSubtitleStatusQuery.data?.videoId],
    queryFn: () => fetchCourseDetail(localSubtitleStatusQuery.data?.videoId ?? ''),
    enabled:
      params.mode === 'local' &&
      localSubtitleStatusQuery.data?.status === 'completed' &&
      Boolean(localSubtitleStatusQuery.data?.videoId)
  });

  const courseRetranslateMutation = useMutation({
    mutationFn: async (videoId: string) => {
      const selectedProfile = await getTranslationProfile(selectedProfileId);
      return submitCourseRetranslate(videoId, {
        sourceLang,
        targetLang,
        translationProfile: selectedProfile
          ? {
              provider: selectedProfile.provider,
              baseUrl: selectedProfile.baseUrl,
              model: selectedProfile.model,
              apiKey: selectedProfile.apiKey
            }
          : undefined
      });
    },
    onSuccess: (result) => {
      navigate(`/study/backend/${result.taskId}`);
    }
  });

  useEffect(() => {
    autoDownloadedRef.current = false;
  }, [params.id]);

  useEffect(() => {
    if (params.mode !== 'local' || !params.id) {
      return;
    }

    const mediaId = params.id;
    let active = true;
    let objectUrl = '';

    void (async () => {
      const item = await getImportedMedia(mediaId);
      if (!item || !active) {
        return;
      }

      objectUrl = URL.createObjectURL(item.videoFile);
      const subtitles = await parseSubtitleFile(item.subtitleFile);

      if (active) {
        setLocalState({
          title: item.title,
          videoSrc: objectUrl,
          subtitles,
          subtitleTaskId: item.subtitleTaskId
        });
      }
    })();

    return () => {
      active = false;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [params.id, params.mode]);

  useEffect(() => {
    if (!localSubtitleCourseQuery.data) {
      return;
    }

    setLocalState((current) =>
      current
        ? {
            ...current,
            subtitles: normalizeBackendSubtitles(localSubtitleCourseQuery.data.subtitles)
          }
        : current
    );
  }, [localSubtitleCourseQuery.data]);

  const localSubtitleStatusText =
    localSubtitleStatusQuery.data?.status === 'processing' || localSubtitleStatusQuery.data?.status === 'pending'
      ? '本地字幕正在后台重译，当前先展示原始字幕，处理完成后会自动替换。'
      : localSubtitleStatusQuery.data?.status === 'failed'
        ? `本地字幕重译失败：${localSubtitleStatusQuery.data.error ?? '请稍后重试。'}`
        : localSubtitleStatusQuery.data?.status === 'completed'
          ? '本地字幕已完成后台重译，当前展示的是最新结果。'
          : undefined;

  const rerunLocalSubtitleTask = async () => {
    if (params.mode !== 'local' || !params.id) {
      return;
    }

    const item = await getImportedMedia(params.id);
    if (!item?.subtitleFile) {
      return;
    }

    const selectedProfile = await getTranslationProfile(selectedProfileId);
    const subtitleContent = await item.subtitleFile.text();
    const result = await submitLocalSubtitleAnalyze({
      title: item.title,
      subtitleContent,
      subtitleFormat: item.subtitleName?.toLowerCase().endsWith('.srt') ? 'srt' : 'vtt',
      sourceLang,
      targetLang,
      translationProfile: selectedProfile
        ? {
            provider: selectedProfile.provider,
            baseUrl: selectedProfile.baseUrl,
            model: selectedProfile.model,
            apiKey: selectedProfile.apiKey
          }
        : undefined
    });

    await updateImportedMediaSubtitleTask(params.id, result.taskId);
    setLocalState((current) => (current ? { ...current, subtitleTaskId: result.taskId } : current));
  };

  useEffect(() => {
    if (
      params.mode === 'backend' &&
      autoDownload &&
      !autoDownloadedRef.current &&
      taskStatusQuery.data?.status === 'completed' &&
      taskStatusQuery.data.videoId
    ) {
      autoDownloadedRef.current = true;
      void downloadToDevice(buildDownloadUrl(taskStatusQuery.data.videoId), `${courseQuery.data?.title ?? 'video'}.mp4`);
    }
  }, [autoDownload, courseQuery.data?.title, params.mode, taskStatusQuery.data]);

  if (params.mode === 'backend') {
    if (taskStatusQuery.isLoading || taskStatusQuery.data?.status === 'processing' || taskStatusQuery.data?.status === 'pending') {
      return (
        <section className="card status-card">
          <p className="eyebrow">后台处理中</p>
          <h2>{taskStatusQuery.data?.title ?? '正在准备学习素材...'}</h2>
          <div className="progress-strip large">
            <span style={{ width: `${taskStatusQuery.data?.progress ?? 12}%` }} />
          </div>
          <p className="subtle-text">当前进度 {taskStatusQuery.data?.progress ?? 0}%</p>
        </section>
      );
    }

    if (taskStatusQuery.data?.status === 'failed') {
      return (
        <section className="card status-card">
          <p className="eyebrow">处理失败</p>
          <h2>这次任务还没准备好</h2>
          <p className="subtle-text">{taskStatusQuery.data.error ?? '请稍后重试。'}</p>
        </section>
      );
    }

    if (courseQuery.data) {
      return (
        <div className="page-stack">
          <section className="card status-card">
            <p className="eyebrow">课程操作</p>
            <p className="subtle-text">
              当前语言方向：{courseQuery.data.sourceLang ?? sourceLang} → {courseQuery.data.targetLang ?? targetLang}
            </p>
            <div className="inline-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={!taskStatusQuery.data?.videoId || courseRetranslateMutation.isPending}
                onClick={() => {
                  if (taskStatusQuery.data?.videoId) {
                    courseRetranslateMutation.mutate(taskStatusQuery.data.videoId);
                  }
                }}
              >
                {courseRetranslateMutation.isPending ? '正在创建重译任务...' : '重新翻译当前课程'}
              </button>
            </div>
          </section>
          <PlayerPanel
            title={courseQuery.data.title}
            videoSrc={courseQuery.data.hasVideo ? courseQuery.data.videoUrl : undefined}
            subtitles={normalizeBackendSubtitles(courseQuery.data.subtitles)}
            sourceLang={courseQuery.data.sourceLang ?? sourceLang}
            targetLang={courseQuery.data.targetLang ?? targetLang}
            onDownload={
              courseQuery.data.hasVideo
                ? () =>
                    downloadToDevice(
                      buildDownloadUrl(taskStatusQuery.data?.videoId ?? ''),
                      `${courseQuery.data.title || 'video'}.mp4`
                    )
                : undefined
            }
          />
        </div>
      );
    }
  }

  if (params.mode === 'local' && localState) {
    return (
      <div className="page-stack">
        {localSubtitleStatusText ? (
          <section className="card status-card">
            <p className="eyebrow">本地字幕任务</p>
            <p className="subtle-text">{localSubtitleStatusText}</p>
            <div className="inline-actions">
              <button type="button" className="secondary-button" onClick={() => void rerunLocalSubtitleTask()}>
                重新翻译字幕
              </button>
            </div>
          </section>
        ) : null}
        <PlayerPanel
          title={localState.title}
          videoSrc={localState.videoSrc}
          subtitles={localState.subtitles}
          sourceLang={localState.subtitles[0]?.sourceLang ?? sourceLang}
          targetLang={localState.subtitles[0]?.targetLang ?? targetLang}
        />
      </div>
    );
  }

  return (
    <section className="card status-card">
      <p className="eyebrow">正在加载</p>
      <h2>学习页准备中</h2>
      <p className="subtle-text">内容加载完成后会自动进入播放器。</p>
    </section>
  );
}
